package com.emptyset.detector

import com.emptyset.detector.detect.Ieee80211
import com.emptyset.detector.detect.WifiBand
import com.emptyset.detector.log.CaptureLog
import com.emptyset.detector.radio.RadioKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonitorUiState(
    val running: Boolean = false,
    val attacking: Boolean = false,
    val alertHeld: Boolean = false,
    val alertSummary: String = "",
    val soundSilenced: Boolean = false,
    val radioTitle: String = "No radio",
    val radioDetail: String = "Plug a supported USB adapter in with OTG",
    val radioKind: RadioKind = RadioKind.UNKNOWN,
    val canCapture: Boolean = false,
    val status: String = "Idle",
    val channel: Int? = null,
    val band: WifiBand? = null,
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
    private val exportLock = Any()
    private val exportLines = ArrayDeque<String>()

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

    fun setChannel(channel: Int, band: WifiBand = WifiBand.infer(channel)) {
        _state.update { it.copy(channel = channel, band = band) }
    }

    fun setSweep(packets: Int, attacking: Boolean) {
        _state.update { it.copy(packetsPerSweep = packets, attacking = attacking) }
    }

    fun latchAlert(summary: String) {
        _state.update { it.copy(alertHeld = true, alertSummary = summary) }
    }

    fun dismissHeldAlert() {
        _state.update { it.copy(alertHeld = false, alertSummary = "") }
    }

    fun clearEvents() {
        lastUiEventMs = 0L
        suppressedEvents = 0
        synchronized(exportLock) { exportLines.clear() }
        _state.update { it.copy(events = emptyList(), lastEvent = null) }
    }

    fun addEvent(line: String, event: Ieee80211.MgmtEvent? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        appendExportLine(now, line)
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

    fun exportEventLog(): String {
        val snap = _state.value
        val lines = synchronized(exportLock) { exportLines.toList() }
        return buildString {
            appendLine("Empty Set event log")
            appendLine("exported ${CaptureLog.displayTime(System.currentTimeMillis())}")
            appendLine("radio ${snap.radioTitle}")
            appendLine("detail ${snap.radioDetail}")
            appendLine("kind ${snap.radioKind}")
            appendLine("status ${snap.status}")
            appendLine("channel ${snap.channel ?: "--"}")
            appendLine("running ${snap.running} attacking ${snap.attacking} frames ${snap.packetsPerSweep}")
            appendLine("---")
            if (lines.isEmpty()) {
                appendLine("(empty)")
            } else {
                lines.forEach { appendLine(it) }
            }
        }
    }

    fun eventLogFileName(epochMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date(epochMs))
        return "Empty Set Event Log $stamp.txt"
    }

    private fun appendExportLine(epochMs: Long, line: String) {
        synchronized(exportLock) {
            exportLines.addLast("${CaptureLog.displayTime(epochMs)}  $line")
            while (exportLines.size > MAX_EXPORT_LINES) {
                exportLines.removeFirst()
            }
        }
    }

    private const val MAX_EXPORT_LINES = 2_000
}
