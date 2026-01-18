package com.example.scamshield

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import android.widget.RemoteViews
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlin.apply
import kotlin.or
import kotlin.text.compareTo

class CaptionScraperService : AccessibilityService() {

    private val CHANNEL_ID = "scam_alert_channel"
    private val NOTIFICATION_ID = 9999
    private var scamNotificationShown = false

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scamNotificationShown = false
            Log.d("ScamShield", "Scam notification flag reset - ready for next call")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ScamShield", "CaptionScraperService connected")
        createNotificationChannel()
        val filter = IntentFilter("com.example.scamshield.RESET_SCAM_FLAG")
        registerReceiver(resetReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val callActive = isCallActive()
        if (!callActive) {
            if (scamNotificationShown) {
                // Call ended, stop vibration but KEEP notification
                VibrationManager.stopVibration()
                scamNotificationShown = false
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)

                val intent = Intent(this, ScamDetectedActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)


                scamNotificationShown = false

                Log.d("ScamShield", "Call ended - notification dismissed and flag reset")

                // Don't cancel the notification here - let user dismiss it
            }
            return  // Don't reset scamNotificationShown flag
        }

//        if (!callActive) {
//            if (scamNotificationShown) {
//                // Call ended, stop vibration and reset notification
//                VibrationManager.stopVibration()
//                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//                notificationManager.cancel(NOTIFICATION_ID)
//            }
//            scamNotificationShown = false
//            return
//        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            val source = event.source
            if (source != null) {
                val sb = StringBuilder()
                traverseNode(source, sb)
                val fullText = sb.toString().trim()
                if (fullText.isNotEmpty()) {
                    checkForScam(fullText)
                }
            } else {
                if (event.text.isNotEmpty()) {
                    val text = event.text.joinToString(" ")
                    checkForScam(text)
                }
            }
        }
    }

    private fun isCallActive(): Boolean {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.callState == TelephonyManager.CALL_STATE_OFFHOOK
        } catch (e: Exception) {
            false
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return

        if (!node.text.isNullOrEmpty()) {
            sb.append(node.text).append(" ")
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, sb)
                child.recycle()
            }
        }
    }

    private fun checkForScam(text: String) {
        if (true && !scamNotificationShown) {
            Log.w("ScamShield", "SCAM DETECTED in text: $text")
            scamNotificationShown = true
            incrementScamCounter()
            showPersistentScamNotification(text)
        }
    }
    private fun incrementScamCounter() {
        val prefs = getSharedPreferences("scam_shield_prefs", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("scam_count", 0)
        prefs.edit().putInt("scam_count", currentCount + 1).apply()

        Log.d("ScamShield", "Total scams blocked: ${currentCount + 1}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scam Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for potential scam calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(false)
                }
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
//    private fun showPersistentScamNotification(text: String) {
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        val dismissIntent = Intent(this, ScamAcknowledgeReceiver::class.java)
//        val dismissPendingIntent = PendingIntent.getBroadcast(
//            this, 0, dismissIntent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        val customView = RemoteViews(packageName, R.layout.notification_scam_alert)
//        customView.setTextViewText(R.id.notification_text, "⚠️ Likely Scam")
//        customView.setOnClickPendingIntent(R.id.notification_button, dismissPendingIntent)
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setCustomContentView(customView)
//            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
//            .setPriority(NotificationCompat.PRIORITY_MAX)
//            .setCategory(NotificationCompat.CATEGORY_CALL)
//            .setOngoing(true)
//            .setAutoCancel(false)
//            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//            .setTimeoutAfter(0)
//            .build()
//
////        notification.flags = notification.flags or android.app.Notification.FLAG_NO_CLEAR or android.app.Notification.FLAG_INSISTENT
//
//        notification.flags = (android.app.Notification.FLAG_NO_CLEAR
//                or android.app.Notification.FLAG_INSISTENT
//                or android.app.Notification.FLAG_ONGOING_EVENT)
//
//        notificationManager.notify(NOTIFICATION_ID, notification)
//        VibrationManager.startContinuousVibration(this)
//    }
    private fun showPersistentScamNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dismissIntent = Intent(this, ScamAcknowledgeReceiver::class.java)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a full-screen intent to keep notification persistent
        val fullScreenIntent = Intent(this, WarningActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val customView = RemoteViews(packageName, R.layout.notification_scam_alert)
        customView.setTextViewText(R.id.notification_text, "⚠️ Likely Scam")
        customView.setOnClickPendingIntent(R.id.notification_button, dismissPendingIntent)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(customView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)  // Changed to ALARM
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)  // This keeps it persistent
            .build()

        notification.flags = android.app.Notification.FLAG_NO_CLEAR

        notificationManager.notify(NOTIFICATION_ID, notification)
        VibrationManager.startContinuousVibration(this)
    }




    override fun onDestroy() {
        super.onDestroy()
        VibrationManager.stopVibration()
        unregisterReceiver(resetReceiver)  // Don't forget to unregister
    }

    override fun onInterrupt() {
        Log.d("ScamShield", "CaptionScraperService interrupted")
    }
}
