cmake_minimum_required(VERSION 3.22.1)
project(emptyset_rx)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(DEVOURER_DIR "${PROJECT_ROOT}/third_party/devourer")
set(LIBUSB_DIR "${PROJECT_ROOT}/third_party/libusb")

# libusb (LGPL-2.1, shared)
add_library(usb-1.0 SHARED
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/sync.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c
    ${LIBUSB_DIR}/libusb/os/linux_netlink.c
)
target_include_directories(usb-1.0
    PUBLIC
        ${LIBUSB_DIR}/libusb
    PRIVATE
        ${LIBUSB_DIR}/android
        ${LIBUSB_DIR}/libusb/os
)
target_compile_definitions(usb-1.0 PRIVATE HAVE_CONFIG_H _GNU_SOURCE)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 PRIVATE log)

# devourer Jaguar1 RX core (GPL-2.0). TX demos and other chip HALs stay out.
add_library(devourer STATIC
    ${DEVOURER_DIR}/src/HalmacCfgParam.cpp
    ${DEVOURER_DIR}/src/ParsedRadioPacket.cpp
    ${DEVOURER_DIR}/src/Radiotap.c
    ${DEVOURER_DIR}/src/RadiotapBuilder.cpp
    ${DEVOURER_DIR}/src/RtlAdapter.cpp
    ${DEVOURER_DIR}/src/UsbTransport.cpp
    ${DEVOURER_DIR}/src/UsbDeviceLock.cpp
    ${DEVOURER_DIR}/src/UsbOpen.cpp
    ${DEVOURER_DIR}/src/TxMode.cpp
    ${DEVOURER_DIR}/src/TxPower.cpp
    ${DEVOURER_DIR}/src/LinkHealth.cpp
    ${DEVOURER_DIR}/src/chanmig/ChannelScore.cpp
    ${DEVOURER_DIR}/src/chanmig/MigGate.cpp
    ${DEVOURER_DIR}/src/sensing/SenseWindow.cpp
    ${DEVOURER_DIR}/src/SignalStop.cpp
    ${DEVOURER_DIR}/src/WiFiDriver.cpp
    ${DEVOURER_DIR}/src/LaCapture.cpp
    ${DEVOURER_DIR}/src/PhyTableLoader.cpp
    ${DEVOURER_DIR}/hal/hal8812a_fw.c
    ${DEVOURER_DIR}/hal/hal8821a_fw.c
    ${DEVOURER_DIR}/hal/Hal8812PwrSeq.c
    ${DEVOURER_DIR}/hal/Hal8821APwrSeq.c
    ${DEVOURER_DIR}/hal/Hal8812a_TxPwrTrack.cpp
    ${DEVOURER_DIR}/src/jaguar1/RtlJaguarDevice.cpp
    ${DEVOURER_DIR}/src/jaguar1/HalModule.cpp
    ${DEVOURER_DIR}/src/jaguar1/RadioManagementModule.cpp
    ${DEVOURER_DIR}/src/jaguar1/EepromManager.cpp
    ${DEVOURER_DIR}/src/jaguar1/FirmwareManager.cpp
    ${DEVOURER_DIR}/src/jaguar1/Iqk8812a.cpp
    ${DEVOURER_DIR}/src/jaguar1/PhydmWatchdog.cpp
    ${DEVOURER_DIR}/src/jaguar1/PowerTracking8812a.cpp
    ${DEVOURER_DIR}/src/jaguar1/FrameParser.cpp
    ${DEVOURER_DIR}/src/jaguar1/BbDbgportReader.cpp
)
target_include_directories(devourer PUBLIC
    ${DEVOURER_DIR}/src
    ${DEVOURER_DIR}/src/jaguar1
    ${DEVOURER_DIR}/hal
    ${DEVOURER_DIR}/hal/phydm/rtl8814a
)
target_compile_definitions(devourer PUBLIC DEVOURER_HAVE_JAGUAR1=1)
target_compile_options(devourer PRIVATE
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-unused-function
    -Wno-unused-value
    -Wno-missing-field-initializers
)
target_link_libraries(devourer PUBLIC usb-1.0 log)

add_library(emptyset_rx SHARED
    emptyset_rx.cpp
    emptyset_mt76.cpp
    mt7610u/usb.cpp
    mt7610u/mcu.cpp
    mt7610u/fw.cpp
    mt7610u/radio.cpp
)
target_include_directories(emptyset_rx PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(emptyset_rx PRIVATE devourer usb-1.0 log android)
target_compile_options(emptyset_rx PRIVATE -fexceptions -frtti)
