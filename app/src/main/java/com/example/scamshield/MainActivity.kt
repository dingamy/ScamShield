package com.example.scamshield

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("ScamShield", "Call screening role granted!")
            } else {
                Log.d("ScamShield", "Call screening role NOT granted.")
            }
        }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val contactsGranted = permissions[android.Manifest.permission.READ_CONTACTS] ?: false
            val phoneStateGranted = permissions[android.Manifest.permission.READ_PHONE_STATE] ?: false
            Log.d("ScamShield", "Permissions granted: Contacts=$contactsGranted, PhoneState=$phoneStateGranted")
        }

    private val overlayPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                Log.d("ScamShield", "Overlay permission granted")
            } else {
                Log.d("ScamShield", "Overlay permission NOT granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkOverlayPermission()
        setupOnboardingUI()
    }

    private fun setupOnboardingUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = getString(R.string.system_active)

        // Trigger permissions first
        permissionsLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_PHONE_STATE
            )
        )
        requestCallScreeningRoleQPlus()
    }

    private fun requestCallScreeningRoleQPlus() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            requestRoleLauncher.launch(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermLauncher.launch(intent)
        }
    }
}