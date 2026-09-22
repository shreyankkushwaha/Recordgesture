package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.example.camera.CameraRecorderManager
import com.example.data.preferences.TriggerAction
import com.example.service.ShakeDetector
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.QuickRecordTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var recorderManager: CameraRecorderManager
    private lateinit var previewView: PreviewView
    private var shakeDetector: ShakeDetector? = null
    private var isCameraReady: Boolean = false
    private var pendingTriggerIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenVisibility()

        // Cache incoming intent if triggered on launch
        if (intent?.action == "com.example.ACTION_TRIGGER_RECORD" || intent?.action == "com.example.ACTION_STOP_RECORD") {
            pendingTriggerIntent = Intent(intent)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        recorderManager = CameraRecorderManager.getInstance(applicationContext).apply {
            onRecordingFinished = { file, durationMs ->
                viewModel.onRecordingSaved(file, durationMs)
            }
        }

        // Initialize Camera with persistent lifecycle and explicit ready callback
        recorderManager.initializeCamera(
            previewView = previewView,
            useFrontCamera = viewModel.settings.value.useFrontCamera,
            onInitialized = {
                Log.d("MainActivity", "Camera fully initialized, processing trigger if pending")
                isCameraReady = true
                pendingTriggerIntent?.let { pendingIntent ->
                    pendingTriggerIntent = null
                    handleTriggerIntent(pendingIntent)
                } ?: run {
                    handleTriggerIntent(intent)
                }
            }
        )

        // Setup Shake Detector
        shakeDetector = ShakeDetector(this) {
            if (viewModel.settings.value.triggerAction == TriggerAction.SHAKE_GESTURE) {
                triggerStartRecording()
            }
        }

        setContent {
            QuickRecordTheme {
                MainScreen(
                    viewModel = viewModel,
                    recorderManager = recorderManager,
                    previewView = previewView,
                    onStartRecording = { triggerStartRecording() },
                    onStopRecording = { recorderManager.stopRecording() }
                )
            }
        }
    }

    private fun configureLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenVisibility()
        handleTriggerIntent(intent)
    }

    private fun handleTriggerIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != "com.example.ACTION_TRIGGER_RECORD" && action != "com.example.ACTION_STOP_RECORD") {
            return
        }
        // Consume intent action so activity recreation or configuration changes do not re-trigger
        intent.action = null

        when (action) {
            "com.example.ACTION_TRIGGER_RECORD" -> {
                val scheduledDuration = intent.getIntExtra("EXTRA_SCHEDULED_DURATION", -1)
                val useFront = if (intent.hasExtra("EXTRA_USE_FRONT_CAMERA")) {
                    intent.getBooleanExtra("EXTRA_USE_FRONT_CAMERA", false)
                } else null

                lifecycleScope.launch {
                    // If recording is already actively running (e.g. started by background service),
                    // reattach preview so user can monitor it and don't restart recording!
                    if (recorderManager.recordingState.value.isRecording || CameraRecorderManager.isRecordingActive) {
                        Log.d("MainActivity", "Recording already actively running, reattaching preview")
                        recorderManager.attachPreview(previewView)
                        return@launch
                    }

                    if (!isCameraReady) {
                        Log.d("MainActivity", "Camera not ready yet when trigger received, waiting...")
                        var attempts = 0
                        while (!isCameraReady && attempts < 15) {
                            delay(200L)
                            attempts++
                        }
                    }

                    if (recorderManager.recordingState.value.isRecording || CameraRecorderManager.isRecordingActive) {
                        recorderManager.attachPreview(previewView)
                        return@launch
                    }

                    if (useFront != null && useFront != viewModel.settings.value.useFrontCamera) {
                        recorderManager.bindCameraUseCases(previewView, useFront)
                        delay(250L)
                    } else {
                        // Small delay to ensure CameraX surface provider is attached
                        delay(200L)
                    }
                    val targetDuration = if (scheduledDuration > 0) scheduledDuration else viewModel.settings.value.maxDurationSeconds
                    triggerStartRecording(targetDuration)
                }
            }
            "com.example.ACTION_STOP_RECORD" -> {
                recorderManager.stopRecording()
            }
        }
    }

    private fun triggerStartRecording(durationOverride: Int? = null) {
        if (!recorderManager.recordingState.value.isRecording) {
            val duration = durationOverride ?: viewModel.settings.value.maxDurationSeconds
            recorderManager.startRecording(duration)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAccessibilityStatus(this)
        recorderManager.attachPreview(previewView)
        if (viewModel.settings.value.triggerAction == TriggerAction.SHAKE_GESTURE) {
            shakeDetector?.startListening()
        }
        if (viewModel.settings.value.enableLockScreenNotification) {
            com.example.service.StandbyNotificationManager.showStandbyNotification(this)
        }
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.stopListening()
    }

    override fun onDestroy() {
        // Only release camera resources if NOT actively recording in the background
        if (!recorderManager.recordingState.value.isRecording) {
            recorderManager.release()
        }
        super.onDestroy()
    }
}
