package com.example.scamshield

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.Toast
import java.util.concurrent.ConcurrentHashMap

class CaptionScraperService : AccessibilityService(), SpellCheckerSession.SpellCheckerSessionListener {

    private val transcript = StringBuilder()
    private var wasCallActive = false
    private val CAPTION_HEADER = "[Call started. Captions will appear here.]"

    private var spellCheckerSession: SpellCheckerSession? = null
    private val spellCheckMap = ConcurrentHashMap<Int, String>()

    // Patterns and keywords to filter out from screen scraping
    private val TIME_PATTERN = Regex("\\d{1,2}:\\d{2}")
    // Matches "03 25" style times often seen in status bars
    private val LOOSE_TIME_PATTERN = Regex("\\b\\d{1,2}\\s+\\d{2}\\b")

    private val DATE_NOISE = Regex("\\b(Sun|Mon|Tue|Wed|Thu|Fri|Sat|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b", RegexOption.IGNORE_CASE)
    // Common carriers that appear in status bars
    private val CARRIER_NOISE = Regex("\\b(Cricket|Verizon|AT&T|T-Mobile|Sprint|Metro|Boost|Mint|Visible|Google Fi)\\b", RegexOption.IGNORE_CASE)

    private val PHONE_PATTERN = Regex("\\+?\\d{1,3}[-\\s.]?\\(?\\d{3}\\)?[-\\s.]?\\d{3}[-\\s.]?\\d{4}")
    private val IGNORED_PHRASES = listOf(
        "Wi-Fi call", "Wi‑Fi call", "Incoming call", "Incoming Wi-Fi call", "Incoming",
        "Answer", "Decline", "Screen", "Audio Emoji", "now",
        "English (United States)", "Chinese (Simplified)", "Sophie Shen", // In a real app, caller name would be dynamic
        "mobile", "calling", "…", // Sometimes ellipses are noise
        "from", // Common prefix noise
        "ing", "e p" // Specific noise fragments
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ScamShield", "CaptionScraperService connected")

        ScamDetector.init(this)

        val tsm = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as TextServicesManager
        spellCheckerSession = tsm.newSpellCheckerSession(null, null, this, true)

        Handler(Looper.getMainLooper()).post {
             Toast.makeText(this, "ScamShield Caption Scraper Active", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // handle call state changes to reset transcript
        val callActive = isCallActive()
        if (callActive && !wasCallActive) {
            Log.d("ScamShield", "Call started. Resetting transcript.")
            transcript.clear()
        }
        wasCallActive = callActive

        // optimize: don't process if no call is active
        if (!callActive) return

        // We listen primarily for content changes or text selection
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            // Log package to help debug where text comes from (e.g., com.google.android.as)
//             Log.d("ScamShield", "Event from: ${event.packageName}")

            val source = event.source
            if (source != null) {
                val sb = StringBuilder()
                traverseNode(source, sb)
                val fullText = sb.toString() // Don't trim yet
                if (fullText.isNotBlank()) {
                    processTranscript(fullText)
                }
                // source.recycle() handled by system usually for the root of event, but children need recycle.
            } else {
                 // Fallback to event text
                if (event.text.isNotEmpty()) {
                    val text = event.text.joinToString(" ")
                    processTranscript(text)
                }
            }
        }
    }

    private fun cleanText(text: String): String {
        var cleaned = text

        // Remove invisible bidi formatting characters (U+200E, U+200F, U+202A-E, U+2066-9, etc)
        cleaned = cleaned.replace("[\\p{Cf}]".toRegex(), "")

        // Remove timestamps
        cleaned = TIME_PATTERN.replace(cleaned, " ")
        cleaned = LOOSE_TIME_PATTERN.replace(cleaned, " ")

        // Remove date and carrier information
        cleaned = DATE_NOISE.replace(cleaned, " ")
        cleaned = CARRIER_NOISE.replace(cleaned, " ")

        // Remove phone numbers
        cleaned = PHONE_PATTERN.replace(cleaned, " ")

        // Remove known UI noise
        IGNORED_PHRASES.forEach { phrase ->
            cleaned = cleaned.replace(phrase, "", ignoreCase = true)
        }

        // Leniency: Remove punctuation and symbols so "One, two" matches "One two"
//        cleaned = cleaned.replace("[\\p{P}\\p{S}]+".toRegex(), " ")

        // Remove extra whitespace created by removals
        return cleaned.replace("\\s+".toRegex(), " ").trim()
    }

    private fun processTranscript(newSnapshot: String) {
        var cleanSnapshot = newSnapshot
        if (cleanSnapshot.startsWith(CAPTION_HEADER)) {
            cleanSnapshot = cleanSnapshot.substring(CAPTION_HEADER.length)
        }

        cleanSnapshot = cleanText(cleanSnapshot)

        if (cleanSnapshot.isEmpty()) return

        // Just take what is currently visible on the screen
        transcript.setLength(0)
        transcript.append(cleanSnapshot)

        val text = transcript.toString()
        if (text.length < 50) return

        if (spellCheckerSession != null) {
            val cookie = text.hashCode()
            spellCheckMap[cookie] = text
            // Note: TextInfo takes cookie and sequence.
            spellCheckerSession?.getSentenceSuggestions(arrayOf(TextInfo(text, cookie, cookie)), 5)
        } else {
            checkForScam(text)
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
        Log.d("ScamShield", "Checking text: $text")
        if (text.length < 50) return // Too short to be meaningful
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

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        // Deprecated, using onGetSentenceSuggestions
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        if (results == null) return

        Handler(Looper.getMainLooper()).post {
            for (result in results) {
                if (result.suggestionsCount > 0) {
                    val info = result.getSuggestionsInfoAt(0)
                    val cookie = info.cookie
                    val originalText = spellCheckMap.remove(cookie)

                    if (originalText != null) {
                        val corrected = applyCorrections(originalText, result)
                        checkForScam(corrected)
                    }
                }
            }
        }
    }

    private fun applyCorrections(text: String, info: SentenceSuggestionsInfo): String {
        val sb = StringBuilder()
        var lastEnd = 0

        for (i in 0 until info.suggestionsCount) {
            val suggestionsInfo = info.getSuggestionsInfoAt(i)
            val offset = info.getOffsetAt(i)
            val length = info.getLengthAt(i)

            // Append parts of text skipped by suggestions (whitespace/punctuation)
            if (offset > lastEnd) {
                sb.append(text.substring(lastEnd, offset))
            }

            if ((suggestionsInfo.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0 &&
                suggestionsInfo.suggestionsCount > 0) {
                // Apply first suggestion
                sb.append(suggestionsInfo.getSuggestionAt(0))
            } else {
                // Keep original
                sb.append(text.substring(offset, offset + length))
            }
            lastEnd = offset + length
        }

        if (lastEnd < text.length) {
            sb.append(text.substring(lastEnd))
        }

        return sb.toString()
    }
}
