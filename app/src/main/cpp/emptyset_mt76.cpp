#include <android/log.h>
#include <jni.h>
#include <stdio.h>

#include <mutex>
#include <string>

#include "mt7610u/mt7610u.h"

#define TAG "emptyset_mt76"

namespace {

JavaVM *g_vm = nullptr;
std::mutex g_mu;
jobject g_sink = nullptr;
jmethodID g_on_frame = nullptr;
jmethodID g_on_error = nullptr;
jmethodID g_on_ready = nullptr;
jmethodID g_on_status = nullptr;

JNIEnv *env_for()
{
	JNIEnv *env = nullptr;
	if (!g_vm)
		return nullptr;
	if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED)
		g_vm->AttachCurrentThread(&env, nullptr);
	return env;
}

void report(const char *msg)
{
	JNIEnv *env = env_for();
	if (!env || !g_sink || !g_on_status || !msg)
		return;
	jstring s = env->NewStringUTF(msg);
	if (!s)
		return;
	env->CallVoidMethod(g_sink, g_on_status, s);
	env->DeleteLocalRef(s);
}

void on_frame(const uint8_t *frame, int len, int rssi, int channel, void *)
{
	JNIEnv *env = env_for();
	if (!env || !g_sink || !g_on_frame || !frame || len <= 0)
		return;
	const jsize n = static_cast<jsize>(len > 4096 ? 4096 : len);
	jbyteArray arr = env->NewByteArray(n);
	if (!arr)
		return;
	env->SetByteArrayRegion(arr, 0, n, reinterpret_cast<const jbyte *>(frame));
	env->CallVoidMethod(g_sink, g_on_frame, arr, static_cast<jint>(rssi),
	                    static_cast<jint>(channel));
	env->DeleteLocalRef(arr);
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeStart(
    JNIEnv *env, jclass, jint fd, jstring fw_path, jint channel, jobject sink)
{
	env->GetJavaVM(&g_vm);
	if (sink == nullptr)
		return env->NewStringUTF("missing JNI sink");
	if (fw_path == nullptr)
		return env->NewStringUTF("missing firmware path");

	std::unique_lock<std::mutex> hold(g_mu);
	if (g_sink) {
		env->DeleteGlobalRef(g_sink);
		g_sink = nullptr;
	}
	g_sink = env->NewGlobalRef(sink);
	jclass cls = env->GetObjectClass(sink);
	g_on_frame = env->GetMethodID(cls, "onFrame", "([BII)V");
	g_on_error = env->GetMethodID(cls, "onNativeError", "(Ljava/lang/String;)V");
	g_on_ready = env->GetMethodID(cls, "onReady", "()V");
	g_on_status = env->GetMethodID(cls, "onStatus", "(Ljava/lang/String;)V");
	if (env->ExceptionCheck())
		env->ExceptionClear();
	env->DeleteLocalRef(cls);
	if (!g_on_frame || !g_on_error || !g_on_ready) {
		env->ExceptionClear();
		return env->NewStringUTF("JNI sink methods missing");
	}

	const char *path = env->GetStringUTFChars(fw_path, nullptr);
	char err[192] = {0};
	report("PAU0A: wrapping USB and loading mt7610u.bin...");
	hold.unlock();
	int rc = mt7610u_open(fd, path ? path : "", err, sizeof err);
	if (path)
		env->ReleaseStringUTFChars(fw_path, path);
	if (rc) {
		mt7610u_stop();
		return env->NewStringUTF(err[0] ? err : "MT7610U open failed");
	}
	rc = mt7610u_start_rx(channel <= 0 ? 1 : channel, on_frame, nullptr, err, sizeof err);
	if (rc) {
		mt7610u_stop();
		return env->NewStringUTF(err[0] ? err : "MT7610U RX start failed");
	}
	if (g_on_ready && g_sink)
		env->CallVoidMethod(g_sink, g_on_ready);
	char diag[160];
	mt7610u_rx_diag(diag, sizeof diag);
	report(diag);
	return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeStartPump(JNIEnv *, jclass)
{
	mt7610u_start_pump();
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeSetChannel(JNIEnv *, jclass, jint channel)
{
	std::lock_guard<std::mutex> hold(g_mu);
	mt7610u_set_channel(channel);
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativePushRx(
    JNIEnv *env, jclass, jbyteArray data, jint length)
{
	if (!data || length <= 0)
		return;
	const jsize n = env->GetArrayLength(data);
	const int use = length < n ? length : n;
	if (use <= 0)
		return;
	jbyte *raw = env->GetByteArrayElements(data, nullptr);
	if (!raw)
		return;
	mt7610u_push_rx(reinterpret_cast<const uint8_t *>(raw), use);
	env->ReleaseByteArrayElements(data, raw, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeRxPoll(JNIEnv *, jclass, jint lastRc)
{
	mt7610u_rx_poll(lastRc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeKickRx(JNIEnv *, jclass)
{
	mt7610u_kick_rx();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeRxStats(JNIEnv *env, jclass)
{
	char buf[160];
	mt7610u_rx_diag(buf, sizeof buf);
	return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeRxEndpoint(JNIEnv *, jclass)
{
	return mt7610u_rx_endpoint();
}

extern "C" JNIEXPORT void JNICALL
Java_com_emptyset_detector_radio_NativePau0a_nativeStop(JNIEnv *env, jclass)
{
	std::lock_guard<std::mutex> hold(g_mu);
	mt7610u_stop();
	if (g_sink) {
		env->DeleteGlobalRef(g_sink);
		g_sink = nullptr;
	}
	g_on_frame = g_on_error = g_on_ready = g_on_status = nullptr;
}
