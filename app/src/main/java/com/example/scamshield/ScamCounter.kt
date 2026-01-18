package com.example.scamshield

import android.content.Context

object ScamCounter {
    private const val PREFS_NAME = "scam_shield_prefs"
    private const val KEY_SCAM_COUNT = "scam_count"

    fun getScamCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SCAM_COUNT, 0)
    }

    fun resetScamCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SCAM_COUNT, 0).apply()
    }
}
