package com.example.scamshield

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import android.telecom.TelecomManager


class CallScreening : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(
            "ScamShield",
            "onScreenCall fired: dir=${callDetails.callDirection}, handle=${callDetails.handle}"
        )

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        val isContact = checkContact(phoneNumber)
        val presentation = callDetails.handlePresentation

        if (isContact && isLikelySpoofed(presentation)) {
            triggerRedAlert(phoneNumber)
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

    private fun isLikelySpoofed(presentationVal : Int): Boolean {
        // For the demo, we assume any call from a saved contact is potentially spoofed
        // to trigger the warning UI.
//        when (presentationVal) {
//            TelecomManager.PRESENTATION_UNAVAILABLE -> {
//                // Carrier has it but won't tell us
//                return true
//            }
//            TelecomManager.PRESENTATION_UNKNOWN -> {
//                // Carrier genuinely doesn't know
//                return true
//            }
//        }
//        return false
        Log.d("ScamShield", "Checking spoof status for $presentationVal")
        return true
    }

    private fun triggerRedAlert(number: String) {
        val intent = Intent(this, WarningActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("CALLER_NUMBER", number)
        }
        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        } else {
            Log.e("ScamShield", "Overlay permission not granted, cannot show warning UI.")
        }
    }
}
