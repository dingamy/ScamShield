package com.example.scamshield

import android.Manifest
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class MicTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val micPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startLiveTranscription()
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // use your existing layout with a TextView
        statusText = findViewById(R.id.statusText)

        // Ask for mic permission
        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startLiveTranscription() {
        statusText.text = "Listening..."
        val transcriptionHelper = TranscriptionHelper(this)
        transcriptionHelper.startListening()
    }
}
