package com.example.scamshield
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.example.scamshield.R

class MainActivity : AppCompatActivity() {

    private val REQUEST_ID = 101

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("ScamShield", "Call screening role granted!")
            } else {
                Log.d("ScamShield", "Call screening role NOT granted.")
            }
        }

    private val contactsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("ScamShield", "READ_CONTACTS granted? $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ScamShield", "hellohello")

        super.onCreate(savedInstanceState)
        checkOverlayPermission()
        setContentView(R.layout.activity_main)

//         1. Check if we were opened because of a spoof detection
        val isWarning = intent.getBooleanExtra("SPOOF_WARNING", false)

        if (isWarning) {
            setupWarningUI()
        } else {
            setupOnboardingUI()
        }
    }

    private fun setupOnboardingUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "ScamShield running"
        statusText.text = "System Active & Protecting You"

        // Trigger the system dialog to become the Call Screener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contactsPermLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            requestCallScreeningRole_QPlus()
        } else {
            statusText.text = "Requires Android 10+ for call screening"
        }
    }

    private fun setupWarningUI() {
        // Change the background to red and show the alert
        val rootLayout = findViewById<View>(R.id.mainLayout)
        val alertText = findViewById<TextView>(R.id.statusText)

        rootLayout.setBackgroundColor(Color.RED)
        alertText.text = "WARNING: SCAM DETECTED!\nThis caller is NOT who they claim to be."
        alertText.setTextColor(Color.WHITE)
        alertText.textSize = 32f
    }

    private fun requestCallScreeningRole_QPlus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d("ScamShield", "Call screening requires Android 10+")
            return
        }

        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            requestRoleLauncher.launch(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1001)
            }
        }
    }
}