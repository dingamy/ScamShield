import android.telecom.CallScreeningService
import android.telecom.Call
import android.content.Intent
import android.provider.ContactsContract
import android.net.Uri
import android.telecom.TelecomManager

class CallScreen : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        // 1. Check if the number is in the user's contact list
        val isContact = checkContact(phoneNumber)
        val presentation = callDetails.handlePresentation

        if (isContact){
            when (presentation){
                TelecomManager.PRESENTATION_UNKNOWN, TelecomManager.PRESENTATION_UNAVAILABLE, TelecomManager.PRESENTATION_RESTRICTED ->{
                    triggerRedAlert(phoneNumber)
                }
            }
        }
        // 2. Logic for Hackathon Demo:
        // If it's a "saved contact" but fails our (mock) STIR/SHAKEN check
//        if (isContact && isLikelySpoofed(phoneNumber)) {
//
//        }
        // 3. Tell the system to let the call through normally
        // We just want to provide our own UI overlay on top of it
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Get the STIR/SHAKEN verification status from the carrier
        val verificationStatus = callDetails.callerNumberVerificationStatus

    }

    private fun checkContact(phoneNumber: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        val cursor = contentResolver.query(uri, projection, null, null, null)

        val exists = cursor?.use { it.count > 0 } ?: false
        return exists
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