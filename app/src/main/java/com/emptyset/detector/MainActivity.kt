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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emptyset.detector.alert.AlertSettings
import com.emptyset.detector.alert.IncomingAlertActivity
import com.emptyset.detector.log.CaptureLog
import com.emptyset.detector.radio.RadioKind
import com.emptyset.detector.radio.UsbDeviceFinder
import com.emptyset.detector.service.MonitorService
import com.emptyset.detector.ui.AttemptDetailScreen
import com.emptyset.detector.ui.EmptySetTheme
import com.emptyset.detector.ui.HistoryScreen
import com.emptyset.detector.ui.MonitorScreen
import com.emptyset.detector.ui.SettingsScreen
import com.emptyset.detector.ui.SetupScreen
import com.emptyset.detector.ui.TitleScreen

class MainActivity : ComponentActivity() {
    private val settings by lazy { AlertSettings(this) }

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
                            }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            settings = settings,
                            revision = alertTick,
                            onBack = { screen = "monitor" },
                            onPickRingtone = {
                                ringtonePicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Incoming Deauthentication")
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
                    "setup" -> SetupScreen(onBack = { screen = "monitor" })
                    else -> MonitorScreen(
                        state = state,
                        recordedCount = attempts.size,
                        onToggle = {
                            if (state.running) stopMonitor() else startMonitor()
                        },
                        onSilence = { silenceAlert() },
                        onOpenHistory = { screen = "history" },
                        onOpenSettings = { screen = "settings" },
                        onOpenSetup = { screen = "setup" }
                    )
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
        val radios = UsbDeviceFinder.attached(this)
        val chosen = radios.firstOrNull { it.kind == RadioKind.T2U_PLUS } ?: radios.firstOrNull()
        if (chosen != null) {
            MonitorStore.setRadio(chosen.title, chosen.detail, chosen.kind, chosen.canCapture)
        } else {
            MonitorStore.setRadio(
                "No radio",
                "Plug a TP-Link T2U Plus into the phone with OTG",
                RadioKind.UNKNOWN,
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
