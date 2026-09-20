package com.emptyset.detector.detect

/**
 * Channel-hop schedule matching the ESP8266 detector on 2.4 GHz, plus 5 GHz
 * UNII channels the TP-Link T2U Plus (RTL8821AU) can tune.
 */
object ChannelPlan {
    val BAND_24 = (1..13).toList()

    val BAND_5 = listOf(
        36, 40, 44, 48,
        52, 56, 60, 64,
        100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
        149, 153, 157, 161, 165
    )

    fun hopset(band24: Boolean, band5: Boolean): List<Int> = buildList {
        if (band24) addAll(BAND_24)
        if (band5) addAll(BAND_5)
    }
}
