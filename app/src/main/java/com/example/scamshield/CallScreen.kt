package com.example.scamshield
import android.telecom.CallScreeningService
import android.telecom.Call
import android.content.Intent
import android.provider.ContactsContract
import android.net.Uri
import android.util.Log
import com.example.scamshield.MainActivity

class CallScreen : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d("ScamShield", "onScreenCall fired: dir=${callDetails.callDirection}, handle=${callDetails.handle}")

        val phoneNumber = callDetails.handle.schemeSpecificPart

        // 1. Check if the number is in the user's contact list
        val isContact = checkContact(phoneNumber)

        // 2. Logic for Hackathon Demo:
        // If it's a "saved contact" but fails our (mock) STIR/SHAKEN check
        if (isContact && isLikelySpoofed(phoneNumber)) {
            triggerRedAlert(phoneNumber)
        }

        // 3. Tell the system to let the call through normally
        // We just want to provide our own UI overlay on top of it
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }

    private fun checkContact(phoneNumber: String): Boolean {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("ScamShield", "READ_CONTACTS not granted")
            return false
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { return it.count > 0 }

        return false
//        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
//        val cursor = contentResolver.query(uri, projection, null, null, null)
//
//        val exists = cursor?.use { it.count > 0 } ?: false
//        return exists
    }

    private fun isLikelySpoofed(number: String): Boolean {
        // HACKATHON SHORTCUT:
        // In a real app, you'd check a STIR/SHAKEN API here.
        // For the demo, return 'true' if the number matches your teammate's phone.
        return true
    }

    private fun triggerRedAlert(number: String) {
        // This launches your warning UI
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("SPOOF_WARNING", true)
            putExtra("CALLER_NUMBER", number)
        }
        startActivity(intent)
    }
}