package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.ComponentName
import com.example.JarvisApp
import com.example.service.JarvisAccessibilityService
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Snapshot of runtime grants, recomputed whenever a permission dialog returns. */
private data class GrantedPermissions(
    val microphone: Boolean,
    val notifications: Boolean,
    val camera: Boolean,
    val contacts: Boolean,
    val calendar: Boolean,
    val phone: Boolean,
    val overlay: Boolean,
    val folders: List<String>,
    val accessibility: Boolean,
    val smsAndCalls: Boolean,
    val notificationAccess: Boolean
)

/** True when JARVIS is switched on under Settings > Accessibility. */
private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val mine = ComponentName(context, JarvisAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // Bumped by every permission launcher callback. It is only ever read as the remember key
    // below: previously it was incremented and never read, so the composable never
    // recomposed and the cards kept showing the grants from first composition forever.
    var refreshCounter by remember { mutableStateOf(0) }

    val permissions = remember(refreshCounter) {
        fun granted(perm: String): Boolean =
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

        GrantedPermissions(
            microphone = granted(Manifest.permission.RECORD_AUDIO),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(Manifest.permission.POST_NOTIFICATIONS),
            camera = granted(Manifest.permission.CAMERA),
            contacts = granted(Manifest.permission.READ_CONTACTS),
            calendar = granted(Manifest.permission.READ_CALENDAR),
            phone = granted(Manifest.permission.CALL_PHONE),
            overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context),
            folders = (context.applicationContext as JarvisApp).workspaceStore.list().map { it.label },
            accessibility = isAccessibilityEnabled(context),
            smsAndCalls = listOf(
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG
            ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
            notificationAccess = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context).contains(context.packageName)
        )
    }

    // Settings pages (overlay, accessibility) return without a result callback, so re-read the
    // grants every time this screen comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshCounter++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsCallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshCounter++
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) (context.applicationContext as JarvisApp).workspaceStore.add(uri)
        refreshCounter++
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCounter++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "NEURAL INITIALIZATION PROTOCOL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Subsystem Permissions & Access",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "JARVIS operates directly on your device. Every permission grants a specific capability. Denying any permission simply limits that subsystem without breaking the voice AI.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            // Permission Items
            PermissionItemCard(
                title = "Audio Ingestion (Microphone)",
                description = "Required to stream real-time 16 kHz voice to the Gemini Live WebSocket and detect voice commands.",
                isGranted = permissions.microphone,
                onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItemCard(
                    title = "System Notifications",
                    description = "Required for proactive alarms, reminders, daily morning briefings, and background listening status.",
                    isGranted = permissions.notifications,
                    onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            PermissionItemCard(
                title = "Optical Sensors (Camera)",
                description = "Enables visual understanding via CameraX and flashlight torch controls.",
                isGranted = permissions.camera,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )

            PermissionItemCard(
                title = "Contacts Directory",
                description = "Allows looking up phone numbers and contacts upon voice command.",
                isGranted = permissions.contacts,
                onRequest = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )

            PermissionItemCard(
                title = "Personal Calendar",
                description = "Enables scheduling events and reading your upcoming daily agenda.",
                isGranted = permissions.calendar,
                onRequest = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
            )

            PermissionItemCard(
                title = "Telephony (Phone Calls)",
                description = "Allows initiating authorized calls to contacts upon confirmation.",
                isGranted = permissions.phone,
                onRequest = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) }
            )

            PermissionItemCard(
                title = "Floating HUD Overlay",
                description = "Allows JARVIS to appear as a floating interactive bubble over other applications.",
                isGranted = permissions.overlay,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        // Some OEM builds ship this activity behind Settings>Apps, where the
                        // action exists but resolves to nothing.
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                )
                            }
                    }
                }
            )

            PermissionItemCard(
                title = "Folder Access (Files)",
                description = "Lets JARVIS create folders and create, edit and delete files (for example write code into a file) " +
                    "inside a folder you pick. It can only touch the folders you choose here." +
                    if (permissions.folders.isNotEmpty()) "\nGranted: ${permissions.folders.joinToString()}" else "",
                isGranted = permissions.folders.isNotEmpty(),
                onRequest = { folderLauncher.launch(null) },
                extraActionLabel = if (permissions.folders.isNotEmpty()) "Add folder" else null,
                onExtraAction = { folderLauncher.launch(null) }
            )

            PermissionItemCard(
                title = "Accessibility (Control Other Apps)",
                description = "Lets JARVIS tap, type, scroll, open and close apps for you, for example search YouTube and play a video. " +
                    "Android asks you to switch it on yourself: find JARVIS in the list and turn it on.",
                isGranted = permissions.accessibility,
                onRequest = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            )

            PermissionItemCard(
                title = "SMS & Call Announcements",
                description = "Lets JARVIS tell you who is texting or calling. Optional: you choose whether to switch " +
                    "each announcement on in Settings, and you can turn them off any time.",
                isGranted = permissions.smsAndCalls,
                onRequest = {
                    smsCallLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG
                        )
                    )
                }
            )

            PermissionItemCard(
                title = "Notification Access (Read & Reply)",
                description = "Lets JARVIS read your message notifications (WhatsApp, SMS...) and reply when you ask. " +
                    "Android opens a list: find JARVIS and switch it on. Messages stay in memory only.",
                isGranted = permissions.notificationAccess,
                onRequest = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF001F26)
                )
            ) {
                Text(
                    text = "INITIALIZE JARVIS HUD",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1424))
            .border(
                1.dp,
                if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!isGranted) {
                OutlinedButton(
                    onClick = onRequest,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Grant", fontSize = 11.sp)
                }
            } else if (extraActionLabel != null && onExtraAction != null) {
                OutlinedButton(
                    onClick = onExtraAction,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(extraActionLabel, fontSize = 11.sp)
                }
            }
        }
    }
}
