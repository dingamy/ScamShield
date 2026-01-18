package com.example.scamshield

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import android.telecom.Connection


class

CallScreening : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(
            "ScamShield",
            "onScreenCall fired: dir=${callDetails.callDirection}, handle=${callDetails.handle}"
        )

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        val isContact = checkContact(phoneNumber)

        if (isContact && isLikelySpoofed(callDetails.callerNumberVerificationStatus)) {
            // Set flag to enable caption scam detection for this call
            val prefs = getSharedPreferences("scam_shield_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("red_alert_active", true).apply()
            Log.d("ScamShield", "Red alert triggered - caption scam detection enabled")

            triggerRedAlert(phoneNumber)
        } else {
            val prefs = getSharedPreferences("scam_shield_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("red_alert_active", false).apply()
            Log.d("ScamShield", "Normal call - caption scam detection disabled")
        }

        val response =
            CallResponse.Builder().setDisallowCall(false).setRejectCall(false).setSkipCallLog(false)
                .setSkipNotification(false).build         ()

        respondToCall(callDetails, response)
    }


    private fun checkContact(phoneNumber: String): Boolean {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d("ScamShield", "READ_CONTACTS not granted")
            return false
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)
        )
        contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { return it.count > 0 }

        return false
    }

    private fun isLikelySpoofed(verificationStatus : Int): Boolean {
        // For the demo, we assume any call from a saved contact is potentially spoofed
        // to trigger the warning UI.
        Log.d("ScamShield", "Caller number verification status: $verificationStatus")

        return verificationStatus != Connection.VERIFICATION_STATUS_PASSED
    }

    private fun triggerRedAlert(number: String) {
        // Get caller name from contacts
        val callerName = getCallerName(number)

        val intent = Intent(this, WarningActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("CALLER_NUMBER", number)
            putExtra("CALLER_NAME", callerName) // Add caller name
        }
        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        } else {
            Log.e("ScamShield", "Overlay permission not granted, cannot show warning UI.")
        }
    }

    private fun getCallerName(phoneNumber: String): String {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "Unknown Caller"
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex) ?: "Unknown Caller"
                }
            }
        }

        return "Unknown Caller"
    }

}
