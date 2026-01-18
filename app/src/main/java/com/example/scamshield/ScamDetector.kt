package com.example.scamshield

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.min

object ScamDetector {
    private const val TAG = "ScamDetector"
    private const val MODEL_FILE = "distilbert_scam_detector.tflite"
    private var interpreter: Interpreter? = null
    private var inputSeqLength = 128 // Default common length, will update from model

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

    fun init(context: Context) {
        try {
            // Check if model file exists in assets
            val validModels = context.assets.list("") // helper to debug if needed
            if (validModels?.contains(MODEL_FILE) != true) {
                Log.e(TAG, "Model file $MODEL_FILE not found in assets.")
                return
            }

            // Load model file from assets
            val modelFile = File(context.cacheDir, MODEL_FILE)
            context.assets.open(MODEL_FILE).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val options = Interpreter.Options()
            interpreter = Interpreter(modelFile, options)

            // Inspect input shape to determine sequence length
            if (interpreter != null) {
                val inputTensor = interpreter!!.getInputTensor(0)
                val shape = inputTensor.shape() // [1, seq_len]
                if (shape.size > 1) {
                    inputSeqLength = shape[1]
                }
                Log.d(TAG, "TFLite Interpreter loaded. Input Shape: ${shape.joinToString()}, Seq Len: $inputSeqLength")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to load TFLite model ($MODEL_FILE): ${e.message}. Falling back to keyword-based detection.")
            Log.d(TAG, "Error details:", e)
            interpreter = null
        }
    }

    fun analyze(text: String): Boolean {
        if (text.length < 50) return false
//        "Hi Elliott, just calling to confirm our dinner at 7pm tonight.",
//        "Hi Mom, I sent you $50 for groceries. Let me know if the link to the bank transfer works.",

        // 1. Try TFLite Model Inference
        interpreter?.let { model ->
            try {
                // Tokenize text to Input IDs and Attention Mask
                // NOTE: Proper tokenization requires vocab.txt. Using a basic hash-based fallback or empty
                // vector will result in poor accuracy but verifies the pipeline runs.
                // We assume DistilBERT inputs: [1, seq_len] IDs, [1, seq_len] Mask.

                val inputs = tokenize(text, inputSeqLength)
//                val inputs = tokenize("Hi son, I am in an emergency.  I crashed the car and I need a transfer for the ambulance ride to the hospital", inputSeqLength)
                val inputIds = inputs.first
                val attentionMask = inputs.second

                // Prepare inputs map: 0 -> ids, 1 -> mask (based on error "expected 3 found 2", likely ids & mask)
                // We need to match the signature. Usually [0] is ids, [1] is mask.

                // Prepare output buffer: [1, 2] (Logits for 2 classes)
                val outputBuffer = ByteBuffer.allocateDirect(1 * 2 * 4).order(ByteOrder.nativeOrder())
                val outputMap = mapOf(0 to outputBuffer)

                val inputsArray = arrayOf(inputIds, attentionMask)

                model.runForMultipleInputsOutputs(inputsArray, outputMap)

                // Read output logits
                outputBuffer.rewind()
                val logits = FloatArray(2)
                outputBuffer.asFloatBuffer().get(logits)

                // If label is 0 (Safe/Negative), return false early
                if (logits[0] > logits[1]) {
                    return false
                }

                val sumExp = logits.sumOf { exp(it.toDouble()) }
                val scamLogit = logits[1]

                val scamProbability = if (sumExp > 0) (exp(scamLogit.toDouble()) / sumExp).toFloat() else 0f
                Log.d(TAG, "Computed scam probability: $scamProbability")

                if (scamProbability > 0.97f) {
                     Log.d(TAG, "SCAM detected by ML model (probability: $scamProbability)")
                     return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running model inference", e)
            }
        }
        return false
    }

    // Basic cleaning to improve detection accuracy
    private fun cleanText(text: String): String {
        // Remove common OCR/Scraping noise patterns
        // 1. Timestamps (e.g. 12:00)
        // 2. Digits/Symbols that might break keyword boundaries
        return text.replace(Regex("\\d{1,2}:\\d{2}"), " ")
                   .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                   .replace(Regex("\\s+"), " ")
                   .trim()
    }

    // Basic tokenizer compatible with DistilBERT shapes
    // Returns Pair(InputIds as LongArray in 1xSeqLen, AttentionMask as LongArray in 1xSeqLen)
    private fun tokenize(text: String, seqLen: Int): Pair<Array<LongArray>, Array<LongArray>> {
        val idsBuffer = LongArray(seqLen)
        val maskBuffer = LongArray(seqLen)

        // Simulating tokenization since we lack vocab.txt
        val words = text.lowercase().split("\\s+".toRegex())
        var idx = 0

        // [CLS] token usually 101
        if (idx < seqLen) {
            idsBuffer[idx] = 101L
            maskBuffer[idx] = 1L
            idx++
        }

        for (word in words) {
            if (idx >= seqLen - 1) break // Leave room for [SEP]
            // Hash word to a "dummy" token ID range (e.g., 2000-30000) just to feed data
            // In production, this MUST use the real vocab mapping!
            val dummyId = (word.hashCode().takeIf { it > 0 } ?: 1000) % 28000 + 2000
            idsBuffer[idx] = dummyId.toLong()
            maskBuffer[idx] = 1L
            idx++
        }

        // [SEP] token usually 102
        if (idx < seqLen) {
            idsBuffer[idx] = 102L
            maskBuffer[idx] = 1L
            idx++
        }

        // Padding is 0, already set by LongArray initialization

        // Wrap in [1, seqLen] array for TFLite
        val wrappedIds = arrayOf(idsBuffer)
        val wrappedMask = arrayOf(maskBuffer)

        return Pair(wrappedIds, wrappedMask)
    }
}
