import android.app.role.RoleManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.scamshield.R

class MainActivity : AppCompatActivity() {

    private val REQUEST_ID = 101

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Check if we were opened because of a spoof detection
        val isWarning = intent.getBooleanExtra("SPOOF_WARNING", false)

        if (isWarning) {
            setupWarningUI()
        } else {
            setupOnboardingUI()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupOnboardingUI() {
        // Normal setup screen for the elderly user
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = "System Active & Protecting You"

        // Trigger the system dialog to become the Call Screener
        requestCallScreeningRole()
    }

    private fun setupWarningUI() {
        // Change the background to red and show the alert
        val rootLayout = findViewById<View>(R.id.mainLayout)
        val alertText = findViewById<TextView>(R.id.statusText)

        rootLayout.setBackgroundColor(Color.RED)
        alertText.text = "⚠️ WARNING: SCAM DETECTED!\nThis caller is NOT who they claim to be."
        alertText.setTextColor(Color.WHITE)
        alertText.textSize = 32f
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            startActivityForResult(intent, REQUEST_ID)
        }
    }
}