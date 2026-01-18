package com.example.scamshield

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class CaptionScraperService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ScamShield", "CaptionScraperService connected")
        Handler(Looper.getMainLooper()).post {
             Toast.makeText(this, "ScamShield Caption Scraper Active", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // optimize: don't process if no call is active
        if (!isCallActive()) return

        // We listen primarily for content changes or text selection
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            // Log package to help debug where text comes from (e.g., com.google.android.as)
            // Log.d("ScamShield", "Event from: ${event.packageName}")

            val source = event.source
            if (source != null) {
                val sb = StringBuilder()
                traverseNode(source, sb)
                val fullText = sb.toString().trim()
                if (fullText.isNotEmpty()) {
                    checkForScam(fullText)
                }
                // source.recycle() handled by system usually for the root of event, but children need recycle.
            } else {
                 // Fallback to event text
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
        // Log.d("ScamShield", "Checking text: $text")
        // Reduced log noise, only log unique/relevant check could be better but for now filtering inside detector is ok
        if (ScamDetector.analyze(text)) {
             Log.w("ScamShield", "SCAM DETECTED in text: $text")
             // Show Toast on Main Thread
             Handler(Looper.getMainLooper()).post {
                 Toast.makeText(this, "WARNING: SCAM LIKELY DETECTED: $text", Toast.LENGTH_LONG).show()
             }
        }
    }

    override fun onInterrupt() {
        Log.d("ScamShield", "CaptionScraperService interrupted")
    }
}
