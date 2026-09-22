package com.emptyset.detector

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emptyset.detector.alert.AlertSettings
import com.emptyset.detector.alert.IncomingAlertActivity
import com.emptyset.detector.log.CaptureLog
import com.emptyset.detector.radio.RadioCatalog
import com.emptyset.detector.radio.RadioSelection
import com.emptyset.detector.radio.UsbDeviceFinder
import com.emptyset.detector.radio.UsbIds
import com.emptyset.detector.service.MonitorService
import com.emptyset.detector.ui.AboutScreen
import com.emptyset.detector.ui.AttemptDetailScreen
import com.emptyset.detector.ui.EmptySetTheme
import com.emptyset.detector.ui.HistoryScreen
import com.emptyset.detector.ui.MenuScreen
import com.emptyset.detector.ui.MonitorScreen
import com.emptyset.detector.ui.RadioScreen
import com.emptyset.detector.ui.SettingsScreen
import com.emptyset.detector.ui.SetupScreen
import com.emptyset.detector.ui.StickyAlertBar
import com.emptyset.detector.ui.TitleScreen

class MainActivity : ComponentActivity() {
    private val settings by lazy { AlertSettings(this) }
    private val radioSelection by lazy { RadioSelection(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var pendingExport: Pair<String, String>? = null
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val pending = pendingExport
        pendingExport = null
        if (uri == null || pending == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(pending.second.toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
    }

    private var onSettingsChanged: (() -> Unit)? = null

    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.let { data ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }
        if (uri != null) {
            settings.ringtoneUri = uri.toString()
            onSettingsChanged?.invoke()
        }
    }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.customUri = uri.toString()
        onSettingsChanged?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshRadios()
        val log = (application as EmptySetApp).captureLog
        setContent {
            EmptySetTheme {
                val state by MonitorStore.state.collectAsStateWithLifecycle()
                val attempts by log.attemptsFlow.collectAsStateWithLifecycle()
                var showTitle by remember { mutableStateOf(!LaunchGate.titleShown) }
                var screen by remember { mutableStateOf("monitor") }
                var openId by remember { mutableStateOf<String?>(null) }
                var alertTick by remember { mutableIntStateOf(0) }
                onSettingsChanged = { alertTick++ }
                if (showTitle) {
                    TitleScreen {
                        LaunchGate.titleShown = true
                        showTitle = false
                    }
                    return@EmptySetTheme
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.alertHeld) {
                        StickyAlertBar(
                            title = settings.lockScreenMessage,
                            detail = state.alertSummary,
                            onDismiss = { MonitorStore.dismissHeldAlert() }
                        )
                    }
                when (screen) {
                    "history" -> HistoryScreen(
                        attempts = attempts,
                        onBack = { screen = "monitor" },
                        onOpen = { id ->
                            openId = id
                            screen = "detail"
                        },
                        onExportJson = { export(CaptureLog.exportFileName("json"), log.exportJson()) },
                        onExportCsv = { export(CaptureLog.exportFileName("csv"), log.exportCsv()) },
                        onDelete = { id -> log.deleteAttempt(id) },
                        onClear = { log.clear() }
                    )
                    "detail" -> {
                        val id = openId
                        val allFrames by log.framesFlow.collectAsStateWithLifecycle()
                        AttemptDetailScreen(
                            attempt = attempts.firstOrNull { it.id == id },
                            frames = allFrames.filter { it.attemptId == id },
                            onBack = { screen = "history" },
                            onExportJson = {
                                val started = attempts.firstOrNull { it.id == id }?.startedAtMs
                                export(CaptureLog.exportFileName("json", started ?: System.currentTimeMillis()), log.exportJson(id))
                            },
                            onExportCsv = {
                                val started = attempts.firstOrNull { it.id == id }?.startedAtMs
                                export(CaptureLog.exportFileName("csv", started ?: System.currentTimeMillis()), log.exportCsv(id))
                            },
                            onDelete = {
                                if (id != null) log.deleteAttempt(id)
                                openId = null
                                screen = "history"
                            }
                        )
                    }
                    "menu" -> MenuScreen(
                        onBack = { screen = "monitor" },
                        onOpenAlerts = { screen = "settings" },
                        onOpenSetup = { screen = "setup" },
                        onOpenAbout = { screen = "about" }
                    )
                    "about" -> AboutScreen(onBack = { screen = "menu" })
                    "settings" -> {
                        SettingsScreen(
                            settings = settings,
                            revision = alertTick,
                            onBack = { screen = "menu" },
                            onPickRingtone = {
                                ringtonePicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, settings.lockScreenMessage)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                )
                            },
                            onPickFile = { audioPicker.launch(arrayOf("audio/*")) },
                            onMode = { mode ->
                                settings.soundMode = mode
                                alertTick++
                            }
                        )
                    }
                    "radio" -> {
                        var selectedRadioId by remember { mutableStateOf(radioSelection.selectedId) }
                        var band24 by remember { mutableStateOf(radioSelection.band24) }
                        var band5 by remember { mutableStateOf(radioSelection.band5) }
                        var band6 by remember { mutableStateOf(radioSelection.band6) }
                        RadioScreen(
                            options = RadioCatalog.options,
                            selectedId = selectedRadioId,
                            onSelect = { id ->
                                radioSelection.selectedId = id
                                selectedRadioId = id
                                refreshRadios()
                            },
                            band24 = band24,
                            band5 = band5,
                            band6 = band6,
                            onBand24 = {
                                radioSelection.band24 = it
                                band24 = radioSelection.band24
                            },
                            onBand5 = {
                                radioSelection.band5 = it
                                band5 = radioSelection.band5
                            },
                            onBand6 = {
                                radioSelection.band6 = it
                                band6 = radioSelection.band6
                            },
                            onBack = { screen = "monitor" },
                            onOpenSetup = { screen = "setup" }
                        )
                    }
                    "setup" -> SetupScreen(onBack = { screen = "menu" })
                    else -> MonitorScreen(
                        state = state,
                        recordedCount = attempts.size,
                        alertTitle = settings.lockScreenMessage,
                        onToggle = {
                            if (state.running) stopMonitor() else startMonitor()
                        },
                        onSilence = { silenceAlert() },
                        onOpenHistory = { screen = "history" },
                        onOpenRadio = { screen = "radio" },
                        onLogoClick = { screen = "menu" },
                        onExportLog = {
                            export(MonitorStore.eventLogFileName(), MonitorStore.exportEventLog())
                        }
                    )
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStore.mainResumed = true
        MonitorStore.appInForeground = true
        MonitorStore.lastMainResumeMs = System.currentTimeMillis()
        IncomingAlertActivity.dismissIfPresent()
        refreshRadios()
    }

    override fun onPause() {
        MonitorStore.mainResumed = false
        MonitorStore.appInForeground = false
        super.onPause()
    }

    private fun refreshRadios() {
        if (MonitorStore.state.value.running) return
        val option = radioSelection.selected
        val match = UsbDeviceFinder.attached(this).firstOrNull { it.kind == option.kind }
            ?: UsbDeviceFinder.attached(this).firstOrNull { UsbIds.matches(option.kind, it.device) }
        when {
            match != null -> MonitorStore.setRadio(
                option.title,
                match.detail,
                option.kind,
                match.canCapture && option.backendReady
            )
            option.backendReady -> MonitorStore.setRadio(
                option.title,
                "Selected. Plug it in with OTG.",
                option.kind,
                false
            )
            else -> MonitorStore.setRadio(
                option.title,
                "Backend not added yet.",
                option.kind,
                false
            )
        }
    }

    private fun export(name: String, body: String) {
        pendingExport = name to body
        createDocument.launch(name)
    }

    private fun startMonitor() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        }.onFailure { err ->
            MonitorStore.setRunning(false)
            MonitorStore.setStatus("Could not start monitor: ${err.message ?: err.javaClass.simpleName}")
        }
    }

    private fun stopMonitor() {
        startService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
        stopService(Intent(this, MonitorService::class.java))
    }

    private fun silenceAlert() {
        startService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_SILENCE))
    }
}

private object LaunchGate {
    @Volatile
    var titleShown: Boolean = false
}
