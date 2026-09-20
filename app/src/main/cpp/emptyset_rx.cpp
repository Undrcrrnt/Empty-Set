#include <android/log.h>
#include <jni.h>
#include <libusb.h>

#include <atomic>
#include <cstdint>
#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "DeviceConfig.h"
#include "IRadio.h"
#include "RxPacket.h"
#include "SelectedChannel.h"
#include "UsbOpen.h"
#include "WiFiDriver.h"
#include "logger.h"

#define TAG "emptyset_rx"

namespace {

JavaVM *g_vm = nullptr;
std::mutex g_mu;
libusb_context *g_ctx = nullptr;
libusb_device_handle *g_handle = nullptr;
std::shared_ptr<devourer::UsbDeviceLock> g_lock;
std::unique_ptr<IRadio> g_radio;
std::thread g_rx;
int g_iface = 0;
std::atomic<int> g_channel{1};
std::atomic<bool> g_running{false};
jobject g_sink = nullptr;
jmethodID g_on_frame = nullptr;
jmethodID g_on_error = nullptr;
jmethodID g_on_ready = nullptr;

void log_err(const char *msg) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg);
}

JNIEnv *env_for() {
    JNIEnv *env = nullptr;
    if (g_vm == nullptr) {
        return nullptr;
    }
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_vm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

void deliver_error(const std::string &msg) {
    log_err(msg.c_str());
    JNIEnv *env = env_for();
    if (env == nullptr || g_sink == nullptr || g_on_error == nullptr) {
        return;
    }
    jstring jmsg = env->NewStringUTF(msg.c_str());
    env->CallVoidMethod(g_sink, g_on_error, jmsg);
    env->DeleteLocalRef(jmsg);
}

void deliver_ready() {
    JNIEnv *env = env_for();
    if (env == nullptr || g_sink == nullptr || g_on_ready == nullptr) {
        return;
    }
    env->CallVoidMethod(g_sink, g_on_ready);
}

bool is_deauth_or_disassoc(const uint8_t *data, size_t len) {
    if (len < 24) {
        return false;
    }
    const uint8_t fc = data[0];
    const int type = (fc >> 2) & 0x3;
    const int subtype = (fc >> 4) & 0xF;
    return type == 0 && (subtype == 10 || subtype == 12);
}

void on_packet(const Packet &packet) {
    if (!g_running.load()) {
        return;
    }
    const auto data = packet.Data;
    if (!is_deauth_or_disassoc(data.data(), data.size())) {
        return;
    }
    JNIEnv *env = env_for();
    if (env == nullptr || g_sink == nullptr || g_on_frame == nullptr) {
        return;
    }
    const jsize n = static_cast<jsize>(data.size() > 4096 ? 4096 : data.size());
    jbyteArray arr = env->NewByteArray(n);
    if (arr == nullptr) {
        return;
    }
    env->SetByteArrayRegion(arr, 0, n, reinterpret_cast<const jbyte *>(data.data()));
    const jint rssi = static_cast<jint>(packet.RxAtrib.rssi[0]);
    env->CallVoidMethod(g_sink, g_on_frame, arr, rssi, static_cast<jint>(g_channel.load()));
    env->DeleteLocalRef(arr);
}

void close_usb_locked() {
    g_lock.reset();
    if (g_handle != nullptr) {
        libusb_release_interface(g_handle, g_iface);
        libusb_close(g_handle);
        g_handle = nullptr;
    }
    if (g_ctx != nullptr) {
        libusb_exit(g_ctx);
        g_ctx = nullptr;
    }
    g_iface = 0;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_emptyset_detector_radio_NativeRx_nativeStart(
    JNIEnv *env, jclass, jint fd, jstring lock_dir, jint channel, jobject sink) {
    try {
        std::lock_guard<std::mutex> hold(g_mu);
        if (g_running) {
            return env->NewStringUTF("native RX already running");
        }
        if (fd < 0) {
            return env->NewStringUTF("invalid USB file descriptor");
        }
        if (sink == nullptr) {
            return env->NewStringUTF("missing JNI sink");
        }

        if (g_sink != nullptr) {
            env->DeleteGlobalRef(g_sink);
            g_sink = nullptr;
        }
        g_sink = env->NewGlobalRef(sink);
        jclass sink_cls = env->GetObjectClass(sink);
        g_on_frame = env->GetMethodID(sink_cls, "onFrame", "([BII)V");
        g_on_error = env->GetMethodID(sink_cls, "onNativeError", "(Ljava/lang/String;)V");
        g_on_ready = env->GetMethodID(sink_cls, "onReady", "()V");
        env->DeleteLocalRef(sink_cls);
        if (g_on_frame == nullptr || g_on_error == nullptr || g_on_ready == nullptr) {
            env->ExceptionClear();
            return env->NewStringUTF("JNI sink methods missing");
        }

        std::string lock_path;
        if (lock_dir != nullptr) {
            const char *lock_chars = env->GetStringUTFChars(lock_dir, nullptr);
            if (lock_chars) {
                lock_path = lock_chars;
                env->ReleaseStringUTFChars(lock_dir, lock_chars);
            }
        }

        (void)libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        int r = libusb_init(&g_ctx);
        if (r < 0 || g_ctx == nullptr) {
            g_ctx = nullptr;
            return env->NewStringUTF("libusb_init failed");
        }

        r = libusb_wrap_sys_device(g_ctx, static_cast<intptr_t>(fd), &g_handle);
        if (r < 0 || g_handle == nullptr) {
            libusb_exit(g_ctx);
            g_ctx = nullptr;
            g_handle = nullptr;
            return env->NewStringUTF("libusb_wrap_sys_device failed");
        }

        auto logger = std::make_shared<Logger>();
        g_iface = devourer::find_wifi_interface(g_handle);
        if (g_iface < 0) {
            g_iface = 0;
        }
        r = devourer::claim_interface_then_reset(
            g_handle, g_iface, logger, /*do_reset=*/false, g_lock, lock_path);
        if (r < 0) {
            close_usb_locked();
            return env->NewStringUTF("USB claim failed (is the adapter already in use?)");
        }

        devourer::DeviceConfig cfg{};
        cfg.usb.lock_dir = lock_path;
        cfg.usb.rx_zerocopy = false;

        WiFiDriver driver(logger);
        g_radio = driver.CreateRadio(g_handle, g_ctx, g_lock, cfg);
        if (!g_radio) {
            close_usb_locked();
            return env->NewStringUTF("CreateRadio returned null (chip not Jaguar1/8821AU?)");
        }

        const uint8_t start_ch = static_cast<uint8_t>(channel <= 0 ? 1 : channel);
        g_channel = start_ch;
        g_running = true;
        SelectedChannel sel{};
        sel.Channel = start_ch;
        sel.ChannelOffset = 0;
        sel.ChannelWidth = CHANNEL_WIDTH_20;
        IRadio *radio = g_radio.get();

        g_rx = std::thread([sel, radio]() {
            try {
                deliver_ready();
                radio->Init(on_packet, sel);
            } catch (const std::exception &ex) {
                deliver_error(std::string("RTL8821AU RX failed: ") + ex.what());
            } catch (...) {
                deliver_error("RTL8821AU RX failed");
            }
            g_running = false;
        });

        return env->NewStringUTF("");
    } catch (const std::exception &ex) {
        g_running = false;
        try {
            close_usb_locked();
        } catch (...) {
        }
        return env->NewStringUTF(ex.what());
    } catch (...) {
        g_running = false;
        try {
            close_usb_locked();
        } catch (...) {
        }
        return env->NewStringUTF("native start failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativeRx_nativeSetChannel(JNIEnv *, jclass, jint channel) {
    try {
        if (channel <= 0 || channel > 253) {
            return;
        }
        g_channel = channel;
        std::lock_guard<std::mutex> hold(g_mu);
        if (!g_radio || !g_running) {
            return;
        }
        g_radio->FastRetune(static_cast<uint8_t>(channel), true);
    } catch (const std::exception &ex) {
        log_err(ex.what());
    } catch (...) {
        log_err("nativeSetChannel failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativeRx_nativeStop(JNIEnv *env, jclass) {
    try {
        std::unique_lock<std::mutex> lock(g_mu);
        g_running = false;
        if (g_radio) {
            g_radio->StopRxLoop();
        }
        lock.unlock();
        if (g_rx.joinable()) {
            g_rx.join();
        }
        lock.lock();
        if (g_radio) {
            try {
                g_radio->Stop();
            } catch (...) {
            }
            g_radio.reset();
        }
        close_usb_locked();
        if (g_sink != nullptr) {
            env->DeleteGlobalRef(g_sink);
            g_sink = nullptr;
        }
        g_on_frame = nullptr;
        g_on_error = nullptr;
        g_on_ready = nullptr;
    } catch (...) {
        log_err("nativeStop failed");
    }
}
