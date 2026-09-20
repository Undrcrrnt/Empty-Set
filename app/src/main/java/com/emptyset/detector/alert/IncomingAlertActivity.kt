package com.emptyset.detector.alert

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.emptyset.detector.MonitorStore
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emptyset.detector.MainActivity
import com.emptyset.detector.service.MonitorService
import com.emptyset.detector.ui.Alert
import com.emptyset.detector.ui.CodecFrequencyDisplay
import com.emptyset.detector.ui.EmptySetTheme
import com.emptyset.detector.ui.Ink
import com.emptyset.detector.ui.Panel
import com.emptyset.detector.ui.Phosphor

class IncomingAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        if (shouldStayInApp()) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val channel = intent.getIntExtra(EXTRA_CHANNEL, -1).takeIf { it > 0 }
        setContent {
            EmptySetTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CodecFrequencyDisplay(
                        modifier = Modifier.fillMaxSize(),
                        channel = channel
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { silenceAndOpen() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Alert, contentColor = Ink),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("SILENCE SOUND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { stopRecording() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Phosphor, contentColor = Ink),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("STOP RECORDING", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { openApp() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("OPEN APP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldStayInApp()) finish()
    }

    private fun silenceAndOpen() {
        startService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_SILENCE))
        openApp()
    }

    private fun stopRecording() {
        startService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
        openApp()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        silenceAndOpen()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    private fun shouldStayInApp(): Boolean {
        if (MonitorStore.mainResumed) return true
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        return !locked
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
        private var current: WeakReference<IncomingAlertActivity>? = null

        fun dismissIfPresent() {
            current?.get()?.let { activity ->
                if (!activity.isFinishing) activity.finish()
            }
        }
    }
}
