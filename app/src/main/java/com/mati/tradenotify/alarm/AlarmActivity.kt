package com.mati.tradenotify.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mati.tradenotify.util.formatClock

/**
 * The full-screen alarm. Shown over the lock screen by the service's full-screen intent, and
 * responsible only for presenting Dismiss / Snooze — the sound lives in [AlarmService].
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val payload = AlarmPayload.from(intent)
        if (payload == null) {
            finish()
            return
        }

        // Leaving by Back would hide the alarm while it kept ringing, which reads as a bug.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            MaterialTheme {
                AlarmScreen(
                    payload = payload,
                    onDismiss = {
                        AlarmService.dismiss(this)
                        finish()
                    },
                    onSnooze = {
                        AlarmService.snooze(this, payload)
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
private fun AlarmScreen(
    payload: AlarmPayload,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF7F1D1D))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text(
            text = "TRADING SIGNAL",
            color = Color(0xFFFCA5A5),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = payload.channel,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${payload.ruleName} · ${formatClock(payload.postedAt)}",
            color = Color(0xFFFCA5A5),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = payload.text,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Text("Snooze", fontSize = 18.sp, color = Color.White)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) {
                Text(
                    "Dismiss",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7F1D1D),
                )
            }
        }
    }
}
