package com.example.scamshield

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // refresh UI state on resume
        }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // refresh UI state on resume
        }

    private val overlayPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // refresh UI state on resume
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingScreen(
                        onRequestRole = { requestCallScreeningRole() },
                        onRequestPermissions = { requestRuntimePermissions() },
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestAccessibility = { requestAccessibilityPermission() }
                    )
                }
            }
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                requestRoleLauncher.launch(intent)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun OnboardingScreen(
    onRequestRole: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val context = LocalContext.current
    var hasRole by remember { mutableStateOf(checkRole(context)) }
    var hasRuntimePerms by remember { mutableStateOf(checkRuntimePerms(context)) }
    var hasOverlay by remember { mutableStateOf(checkOverlay(context)) }
    var hasAccessibility by remember { mutableStateOf(checkAccessibility(context)) }
    var scamCount by remember { mutableStateOf(ScamCounter.getScamCount(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasRole = checkRole(context)
                hasRuntimePerms = checkRuntimePerms(context)
                hasOverlay = checkOverlay(context)
                hasAccessibility = checkAccessibility(context)
                scamCount = ScamCounter.getScamCount(context)
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_scam_shield_logo),
                contentDescription = "ScamShield Logo",
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ScamShield",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scam Counter Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Protected you",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$scamCount",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (scamCount == 1) "time" else "times",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 1. Call Screening Role
        PermissionItem(
            title = "Set as Default Caller ID App",
            description = "Required to screen calls.",
            isGranted = hasRole,
            onClick = {
                onRequestRole()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Runtime Permissions
        PermissionItem(
            title = "Allow Contacts & Phone Access",
            description = "Required to identify contacts.",
            isGranted = hasRuntimePerms,
            onClick = {
                onRequestPermissions()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Overlay Permission
        PermissionItem(
            title = "Display Over Other Apps",
            description = "Required to show warnings.",
            isGranted = hasOverlay,
            onClick = {
                onRequestOverlay()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Accessibility Permission
        PermissionItem(
            title = "Enable Caption Scanning",
            description = "Required to detect scams in real-time.",
            isGranted = hasAccessibility,
            onClick = {
                onRequestAccessibility()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        if (hasRole && hasRuntimePerms && hasOverlay && hasAccessibility) {
            Text(
                text = "System Active & Protecting You",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isGranted,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isGranted) Color.Gray else MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = if (isGranted) "✅ $title" else title, fontWeight = FontWeight.Bold)
            if (!isGranted) {
                Text(text = description, fontSize = 12.sp)
            }
        }
    }
}

fun checkRole(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }
    return true
}

fun checkRuntimePerms(context: Context): Boolean {
    val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val phone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    return contacts && phone
}

fun checkOverlay(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

fun checkAccessibility(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val serviceName = CaptionScraperService::class.java.name
    val packageName = context.packageName

    for (enabledService in enabledServices) {
        val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
        if (enabledServiceInfo.packageName == packageName && enabledServiceInfo.name == serviceName) {
            return true
        }
    }
    return false
}

