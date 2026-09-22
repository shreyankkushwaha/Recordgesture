package com.example.ui

import android.content.Intent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.CameraRecorderManager
import com.example.data.db.RecordingEntity
import com.example.service.QuickActionAccessibilityService
import com.example.service.RecordingState
import com.example.ui.components.AccessibilityStatusBanner
import com.example.ui.components.BackgroundProtectionCard
import com.example.ui.components.PermissionsCard
import com.example.ui.components.RecordHud
import com.example.ui.components.RecordingsListSection
import com.example.ui.components.ScheduleRecordingDialog
import com.example.ui.components.ScheduledRecordingCard
import com.example.ui.components.SettingsSection
import com.example.ui.components.TriggerSelectorCard
import com.example.ui.components.VideoPlaybackDialog
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.RecordRed

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    recorderManager: CameraRecorderManager,
    previewView: PreviewView,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val recordingState by recorderManager.recordingState.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val isBackingUpId by viewModel.isBackingUp.collectAsState()
    val activeSchedule by viewModel.activeSchedule.collectAsState()
    val formattedCountdown by viewModel.formattedCountdown.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedPlaybackRecording by remember { mutableStateOf<RecordingEntity?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // Auto-switch to Record tab if recording starts while on another tab
    LaunchedEffect(recordingState.isRecording) {
        if (recordingState.isRecording) {
            selectedTab = 0
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Capture"
                        )
                    },
                    label = { Text("Capture") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CrimsonPrimary,
                        selectedTextColor = CrimsonPrimary,
                        indicatorColor = CrimsonPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_tab_capture")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Recordings"
                        )
                    },
                    label = { Text("Recordings (${recordings.size})") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CrimsonPrimary,
                        selectedTextColor = CrimsonPrimary,
                        indicatorColor = CrimsonPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_tab_recordings")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CrimsonPrimary,
                        selectedTextColor = CrimsonPrimary,
                        indicatorColor = CrimsonPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> CaptureTab(
                    recordingState = recordingState,
                    settings = settings,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    activeSchedule = activeSchedule,
                    formattedCountdown = formattedCountdown,
                    previewView = previewView,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onOpenScheduleDialog = { showScheduleDialog = true },
                    onCancelSchedule = { viewModel.cancelScheduledRecording() },
                    onStartScheduledNow = { viewModel.triggerScheduledRecordingNow() },
                    onOpenAccessibility = {
                        context.startActivity(QuickActionAccessibilityService.openAccessibilitySettingsIntent())
                    },
                    onPermissionsUpdated = {
                        viewModel.checkAccessibilityStatus(context)
                    }
                )

                1 -> RecordingsTab(
                    recordings = recordings,
                    isBackingUpId = isBackingUpId,
                    onPlay = { selectedPlaybackRecording = it },
                    onEncrypt = { viewModel.encryptRecording(it) },
                    onDecrypt = { viewModel.decryptRecording(it) },
                    onBackup = { viewModel.backupRecording(it) },
                    onDelete = { viewModel.deleteRecording(it) }
                )

                2 -> SettingsTab(
                    settings = settings,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    activeSchedule = activeSchedule,
                    formattedCountdown = formattedCountdown,
                    onOpenScheduleDialog = { showScheduleDialog = true },
                    onCancelSchedule = { viewModel.cancelScheduledRecording() },
                    onStartScheduledNow = { viewModel.triggerScheduledRecordingNow() },
                    onSelectTrigger = { viewModel.updateTriggerAction(it) },
                    onOpenAccessibility = {
                        context.startActivity(QuickActionAccessibilityService.openAccessibilitySettingsIntent())
                    },
                    onMaxDurationChanged = { viewModel.updateMaxDuration(it) },
                    onUseFrontCameraChanged = { front ->
                        viewModel.updateUseFrontCamera(front)
                        if (!recordingState.isRecording) {
                            recorderManager.bindCameraUseCases(previewView, front)
                        }
                    },
                    onAutoEncryptChanged = { viewModel.updateAutoEncrypt(it) },
                    onAutoCloudBackupChanged = { viewModel.updateAutoCloudBackup(it) },
                    onLockScreenNotifChanged = { viewModel.updateEnableLockScreenNotification(it) },
                    onPermissionsUpdated = {
                        viewModel.checkAccessibilityStatus(context)
                    }
                )
            }

            // In-app Playback Dialog
            selectedPlaybackRecording?.let { recording ->
                VideoPlaybackDialog(
                    recording = recording,
                    onDismiss = { selectedPlaybackRecording = null },
                    onDecryptToPlay = { entity ->
                        viewModel.decryptRecording(entity)
                    }
                )
            }

            // Schedule Recording Dialog
            if (showScheduleDialog) {
                ScheduleRecordingDialog(
                    initialDurationSeconds = settings.maxDurationSeconds,
                    initialUseFrontCamera = settings.useFrontCamera,
                    onDismiss = { showScheduleDialog = false },
                    onConfirmSchedule = { targetTimeMillis, durationSeconds, useFrontCamera, preAlert ->
                        viewModel.scheduleRecording(
                            targetTimeMillis = targetTimeMillis,
                            durationSeconds = durationSeconds,
                            useFrontCamera = useFrontCamera,
                            preAlertEnabled = preAlert
                        )
                        showScheduleDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptureTab(
    recordingState: RecordingState,
    settings: com.example.data.preferences.UserSettings,
    isAccessibilityEnabled: Boolean,
    activeSchedule: com.example.schedule.ScheduledRecording?,
    formattedCountdown: String,
    previewView: PreviewView,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenScheduleDialog: () -> Unit,
    onCancelSchedule: () -> Unit,
    onStartScheduledNow: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onPermissionsUpdated: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Quick Recorder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Hardware & lockscreen fast video capture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Current Trigger Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CrimsonPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CrimsonPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = settings.triggerAction.title.substringBefore(" ("),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Permissions Check Banner
        PermissionsCard(onPermissionsUpdated = onPermissionsUpdated)

        val requiresAccessibility = settings.triggerAction == com.example.data.preferences.TriggerAction.VOLUME_DOWN_5X ||
                settings.triggerAction == com.example.data.preferences.TriggerAction.VOLUME_LONG_PRESS ||
                settings.triggerAction == com.example.data.preferences.TriggerAction.VOLUME_DOWN_DOUBLE ||
                settings.triggerAction == com.example.data.preferences.TriggerAction.VOLUME_UP_DOUBLE

        if (requiresAccessibility && !isAccessibilityEnabled) {
            Spacer(modifier = Modifier.height(14.dp))
            AccessibilityStatusBanner(
                isEnabled = isAccessibilityEnabled,
                onOpenSettings = onOpenAccessibility
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Camera Viewfinder Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Live Android CameraX preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay grid lines / frame corners
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .border(
                            1.dp,
                            if (recordingState.isRecording) RecordRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(16.dp)
                        )
                )

                // Top camera status pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (recordingState.isRecording) RecordRed else Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recordingState.isRecording) "LIVE CAPTURE" else "CAMERA READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Lens badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (settings.useFrontCamera) "FRONT" else "BACK HD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Active Recording HUD (if recording)
        AnimatedVisibility(
            visible = recordingState.isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            RecordHud(
                recordingState = recordingState,
                onStopRecording = onStopRecording
            )
        }

        // Manual Record Action Button (when not recording)
        if (!recordingState.isRecording) {
            Button(
                onClick = onStartRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("start_recording_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "RECORD NOW (VIDEO + AUDIO)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lock screen hint card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lock Screen Trigger Configured",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Action: ${settings.triggerAction.title}. Works directly on lock screen without unlocking!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Background & AutoStart Protection Card
            BackgroundProtectionCard()

            Spacer(modifier = Modifier.height(14.dp))

            // Scheduled Recording Section
            ScheduledRecordingCard(
                activeSchedule = activeSchedule,
                formattedCountdown = formattedCountdown,
                onOpenScheduleDialog = onOpenScheduleDialog,
                onCancelSchedule = onCancelSchedule,
                onStartNow = onStartScheduledNow
            )
        }
    }
}

@Composable
private fun RecordingsTab(
    recordings: List<RecordingEntity>,
    isBackingUpId: Long?,
    onPlay: (RecordingEntity) -> Unit,
    onEncrypt: (RecordingEntity) -> Unit,
    onDecrypt: (RecordingEntity) -> Unit,
    onBackup: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        RecordingsListSection(
            recordings = recordings,
            isBackingUpId = isBackingUpId,
            onPlayRecording = onPlay,
            onEncrypt = onEncrypt,
            onDecrypt = onDecrypt,
            onCloudBackup = onBackup,
            onDelete = onDelete
        )
    }
}

@Composable
private fun SettingsTab(
    settings: com.example.data.preferences.UserSettings,
    isAccessibilityEnabled: Boolean,
    activeSchedule: com.example.schedule.ScheduledRecording?,
    formattedCountdown: String,
    onOpenScheduleDialog: () -> Unit,
    onCancelSchedule: () -> Unit,
    onStartScheduledNow: () -> Unit,
    onSelectTrigger: (com.example.data.preferences.TriggerAction) -> Unit,
    onOpenAccessibility: () -> Unit,
    onMaxDurationChanged: (Int) -> Unit,
    onUseFrontCameraChanged: (Boolean) -> Unit,
    onAutoEncryptChanged: (Boolean) -> Unit,
    onAutoCloudBackupChanged: (Boolean) -> Unit,
    onLockScreenNotifChanged: (Boolean) -> Unit,
    onPermissionsUpdated: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Scheduled Recording Option Card
        ScheduledRecordingCard(
            activeSchedule = activeSchedule,
            formattedCountdown = formattedCountdown,
            onOpenScheduleDialog = onOpenScheduleDialog,
            onCancelSchedule = onCancelSchedule,
            onStartNow = onStartScheduledNow
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Permissions Card
        PermissionsCard(onPermissionsUpdated = onPermissionsUpdated)

        Spacer(modifier = Modifier.height(14.dp))

        // Trigger selection
        TriggerSelectorCard(
            currentTrigger = settings.triggerAction,
            isAccessibilityEnabled = isAccessibilityEnabled,
            onSelectTrigger = onSelectTrigger,
            onOpenAccessibilitySettings = onOpenAccessibility
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Background Protection & Auto-Start (Anti-Force Stop)
        BackgroundProtectionCard()

        Spacer(modifier = Modifier.height(14.dp))

        // General settings
        SettingsSection(
            settings = settings,
            onMaxDurationChanged = onMaxDurationChanged,
            onUseFrontCameraChanged = onUseFrontCameraChanged,
            onAutoEncryptChanged = onAutoEncryptChanged,
            onAutoCloudBackupChanged = onAutoCloudBackupChanged,
            onLockScreenNotifChanged = onLockScreenNotifChanged
        )
    }
}
