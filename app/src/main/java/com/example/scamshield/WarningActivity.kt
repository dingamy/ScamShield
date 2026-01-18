package com.example.scamshield

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.view.accessibility.CaptioningManager

class WarningActivity : ComponentActivity() {

    private var isServiceEnabled by mutableStateOf(false)
    private var isCaptionsEnabled by mutableStateOf(false)

    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d("ScamShield", "WarningActivity received state: $state")
                // Only finish on IDLE (call ended). Keep open on OFFHOOK (answered).
                if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                     Log.d("ScamShield", "Call ended, finishing WarningActivity")
                     finishAffinity()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set overlay window type for Android 8.0+
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        val callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "Unknown"

        // Register receiver programmatically
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(callStateReceiver, filter)

        setContent {
            WarningScreen(
                callerNumber = callerNumber,
                isServiceEnabled = isServiceEnabled,
                isCaptionsEnabled = isCaptionsEnabled,
                onEnableServiceClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                },
                onCaptionSettingsClick = {
                     val intent = Intent(Settings.ACTION_CAPTIONING_SETTINGS)
                     intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                     startActivity(intent)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = isAccessibilityServiceEnabled(this, CaptionScraperService::class.java)
        isCaptionsEnabled = isSystemCaptioningEnabled(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name) {
                return true
            }
        }
        return false
    }

    private fun isSystemCaptioningEnabled(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager
        return cm.isEnabled
    }
}

@Composable
fun WarningScreen(
    callerNumber: String,
    isServiceEnabled: Boolean,
    isCaptionsEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onCaptionSettingsClick: () -> Unit
) {
    val backgroundColor = if (isServiceEnabled) Color(0xFF2E7D32) else Color.Red // Green if enabled, Red if warning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isServiceEnabled) stringResource(R.string.scam_shield_monitoring)
                   else stringResource(R.string.warning_spoof, callerNumber),
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isServiceEnabled) {
            Button(
                onClick = onEnableServiceClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
            ) {
                Text(text = stringResource(R.string.enable_scam_shield))
            }
        } else if (!isCaptionsEnabled) {
             Text(
                text = stringResource(R.string.scam_shield_live_caption_hint),
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCaptionSettingsClick,
                 colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2E7D32))
            ) {
                Text("Open Caption Settings")
            }
        }
    }
}
