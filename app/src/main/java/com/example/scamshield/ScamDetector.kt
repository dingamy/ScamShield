package com.example.scamshield

import android.util.Log

object ScamDetector {
    // Placeholder keywords for scam detection
    private val SCAM_KEYWORDS = listOf(
        "password",
        "bank account",
        "social security",
        "gift card",
        "verify",
        "urgent",
        "credit card",
        "refund",
        "irs",
        "sheriff"
    )

    fun analyze(text: String): Boolean {
        if (text.isBlank()) return false
        val lowerText = text.lowercase()
        Log.d("ScamDetector", "Analyzing text for scams: $text")
        return SCAM_KEYWORDS.any { lowerText.contains(it) }
    }
}
