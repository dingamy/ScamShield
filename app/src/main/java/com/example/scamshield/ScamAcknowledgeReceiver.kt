package com.example.scamshield

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScamAcknowledgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ScamShield", "ScamAcknowledgeReceiver: Dismissing notification")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(9999)

        VibrationManager.stopVibration()

        Log.d("ScamShield", "ScamAcknowledgeReceiver: Stopped vibration")
    }
}
