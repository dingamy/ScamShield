package com.example.scamshield
import android.util.Log
import android.widget.Toast
import android.content.Context
class TranscriptionHelper {

    private fun processText(text: String) {
        Log.d("TranscriptionHelper", "Transcribed: $text")
        Toast.makeText(context, "Heard: $text", Toast.LENGTH_SHORT).show()
    }

}