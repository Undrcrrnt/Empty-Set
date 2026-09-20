package com.emptyset.detector

import com.emptyset.detector.detect.Ieee80211
import com.emptyset.detector.radio.RadioKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonitorUiState(
    val running: Boolean = false,
    val attacking: Boolean = false,
    val soundSilenced: Boolean = false,
    val radioTitle: String = "No radio",
    val radioDetail: String = "Plug a TP-Link T2U Plus into the phone with OTG",
    val radioKind: RadioKind = RadioKind.UNKNOWN,
    val canCapture: Boolean = false,
    val status: String = "Idle",
    val channel: Int? = null,
    val packetsPerSweep: Int = 0,
    val lastEvent: Ieee80211.MgmtEvent? = null,
    val events: List<String> = emptyList()
)

object MonitorStore {
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    @Volatile
    var appInForeground: Boolean = false

    @Volatile
    var mainResumed: Boolean = false

    @Volatile
    var lastMainResumeMs: Long = 0L

    private var lastUiEventMs = 0L
    private var suppressedEvents = 0

    fun setRunning(running: Boolean) {
        _state.update {
            it.copy(
                running = running,
                attacking = if (running) it.attacking else false,
                soundSilenced = if (running) it.soundSilenced else false
            )
        }
    }

    fun setSoundSilenced(silenced: Boolean) {
        _state.update { it.copy(soundSilenced = silenced) }
    }

    fun setRadio(title: String, detail: String, kind: RadioKind, canCapture: Boolean) {
        _state.update {
            it.copy(radioTitle = title, radioDetail = detail, radioKind = kind, canCapture = canCapture)
        }
    }

    fun setStatus(status: String) {
        _state.update { it.copy(status = status) }
    }

    fun setChannel(channel: Int) {
        _state.update { it.copy(channel = channel) }
    }

    fun setSweep(packets: Int, attacking: Boolean) {
        _state.update { it.copy(packetsPerSweep = packets, attacking = attacking) }
    }

    fun addEvent(line: String, event: Ieee80211.MgmtEvent? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && event != null && now - lastUiEventMs < 120) {
            suppressedEvents += 1
            _state.update { it.copy(lastEvent = event) }
            return
        }
        val extra = if (suppressedEvents > 0) "  (+$suppressedEvents)" else ""
        suppressedEvents = 0
        lastUiEventMs = now
        _state.update {
            it.copy(
                lastEvent = event ?: it.lastEvent,
                events = (listOf(line + extra) + it.events).take(40)
            )
        }
    }
}
