package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.QuickRecordApplication
import com.example.service.RecordingForegroundService
import com.example.service.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraRecorderManager(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") externalScope: CoroutineScope? = null
) {
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // Application-level coroutine scope that is NEVER cancelled when Activities are stopped or destroyed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Dedicated persistent lifecycle owner so CameraX recording never stops when user minimizes or leaves app
    private val persistentLifecycleOwner = PersistentRecordingLifecycleOwner()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var timerJob: Job? = null
    private var currentOutputFile: File? = null

    private var currentPreviewView: PreviewView? = null
    private var currentUseFrontCamera: Boolean = false

    var isInitialized: Boolean = false
        private set

    init {
        activeInstance = this
        if (instance == null) {
            instance = this
        }
    }

    // Callback when recording is successfully finalized
    var onRecordingFinished: ((savedFile: File, durationMs: Long) -> Unit)? = null
    var onRecordingError: ((errorMessage: String) -> Unit)? = null

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner? = null,
        previewView: PreviewView? = null,
        useFrontCamera: Boolean = false,
        onInitialized: (() -> Unit)? = null
    ) {
        if (isInitialized && cameraProvider != null) {
            if (previewView != null) {
                attachPreview(previewView)
            }
            onInitialized?.invoke()
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView, useFrontCamera)
                isInitialized = true
                onInitialized?.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
                _recordingState.value = _recordingState.value.copy(
                    error = "Failed to initialize camera: ${e.localizedMessage}"
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Reconnects or attaches a PreviewView to the active camera session.
     * This safely allows the user to leave the app and come back while recording continues uninterrupted.
     */
    fun attachPreview(previewView: PreviewView) {
        currentPreviewView = previewView
        try {
            preview?.setSurfaceProvider(previewView.surfaceProvider)
            Log.d("CameraRecorderManager", "PreviewView reattached to active camera stream")
        } catch (e: Exception) {
            Log.w("CameraRecorderManager", "Could not attach PreviewView surface: ${e.message}")
        }
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = false
    ) {
        bindCameraUseCases(previewView, useFrontCamera)
    }

    fun bindCameraUseCases(
        previewView: PreviewView? = null,
        useFrontCamera: Boolean = false
    ) {
        if (isRecordingActive) {
            Log.d("CameraRecorderManager", "Recording is active, skipping unbind to protect background capture")
            if (previewView != null) {
                attachPreview(previewView)
            }
            return
        }

        currentPreviewView = previewView
        currentUseFrontCamera = useFrontCamera

        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()
            persistentLifecycleOwner.start()

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val newPreview = Preview.Builder().build().also { p ->
                previewView?.let { pv ->
                    p.setSurfaceProvider(pv.surfaceProvider)
                }
            }
            this.preview = newPreview

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            provider.bindToLifecycle(
                persistentLifecycleOwner,
                cameraSelector,
                newPreview,
                videoCapture
            )
            Log.d("CameraRecorderManager", "Camera use cases bound with persistent lifecycle (front=$useFrontCamera)")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CameraRecorderManager", "Error binding camera: ${e.localizedMessage}", e)
            _recordingState.value = _recordingState.value.copy(
                error = "Error binding camera: ${e.localizedMessage}"
            )
        }
    }

    fun startRecording(maxDurationSeconds: Int = 60, retryCount: Int = 0) {
        if (isRecordingActive) {
            Log.d("CameraRecorderManager", "Recording is already active, ignoring start request")
            return
        }

        val capture = videoCapture
        if (capture == null) {
            if (retryCount < 8) {
                Log.d("CameraRecorderManager", "VideoCapture not ready yet. Scheduling auto-retry #${retryCount + 1}")
                _recordingState.value = _recordingState.value.copy(
                    isPreparing = true,
                    statusMessage = "Initializing camera sensor… (${retryCount + 1}/8)",
                    error = null
                )
                if (cameraProvider != null) {
                    bindCameraUseCases(currentPreviewView, currentUseFrontCamera)
                }
                scope.launch(Dispatchers.Main) {
                    delay(350L)
                    startRecording(maxDurationSeconds, retryCount + 1)
                }
                return
            }
            _recordingState.value = _recordingState.value.copy(
                isPreparing = false,
                error = "Camera not ready yet. Please wait a moment."
            )
            onRecordingError?.invoke("Camera not initialized")
            return
        }

        if (activeRecording != null) {
            return
        }

        val hasCameraPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            _recordingState.value = _recordingState.value.copy(
                error = "Camera permission not granted"
            )
            onRecordingError?.invoke("Camera permission required")
            return
        }

        // Start foreground service early so the system grants background camera/mic privileges
        try {
            RecordingForegroundService.start(context, 0, maxDurationSeconds)
        } catch (e: Exception) {
            Log.w("CameraRecorderManager", "Early foreground service start: ${e.message}")
        }

        // Prepare output file in app-specific accessible media folder
        val outputDir = getRecordingOutputDir()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(outputDir, "REC_$timeStamp.mp4")
        currentOutputFile = videoFile

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        _recordingState.value = _recordingState.value.copy(
            isPreparing = true,
            statusMessage = "Starting capture…",
            error = null
        )

        try {
            var pendingRecording = capture.output.prepareRecording(context, outputOptions)

            val hasAudioPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission) {
                @Suppress("MissingPermission")
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                handleRecordEvent(event, maxDurationSeconds)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _recordingState.value = _recordingState.value.copy(
                isPreparing = false,
                isRecording = false,
                error = "Failed to start recording: ${e.localizedMessage}"
            )
            onRecordingError?.invoke("Recording start error: ${e.localizedMessage}")
        }
    }

    private fun handleRecordEvent(event: VideoRecordEvent, maxDurationSeconds: Int) {
        when (event) {
            is VideoRecordEvent.Start -> {
                isRecordingActive = true
                _recordingState.value = _recordingState.value.copy(
                    isRecording = true,
                    isPreparing = false,
                    elapsedSeconds = 0,
                    maxDurationSeconds = maxDurationSeconds,
                    currentFilePath = currentOutputFile?.absolutePath,
                    statusMessage = "Recording Active (Background Ready)",
                    error = null
                )

                // Start foreground service with persistent notification
                RecordingForegroundService.start(context, 0, maxDurationSeconds)

                // Launch timer on application scope
                startTimer(maxDurationSeconds)
            }

            is VideoRecordEvent.Status -> {
                val stats = event.recordingStats
                val audioStats = stats.audioStats
                val hasAudio = audioStats.audioState == androidx.camera.video.AudioStats.AUDIO_STATE_ACTIVE
                val amplitude = if (hasAudio) 0.65f else 0.0f

                _recordingState.value = _recordingState.value.copy(
                    audioAmplitude = amplitude
                )
            }

            is VideoRecordEvent.Finalize -> {
                isRecordingActive = false
                stopTimer()
                RecordingForegroundService.stop(context)

                val file = currentOutputFile
                val durationMs = (_recordingState.value.elapsedSeconds * 1000L).coerceAtLeast(1000L)

                _recordingState.value = _recordingState.value.copy(
                    isRecording = false,
                    isPreparing = false,
                    audioAmplitude = 0f,
                    statusMessage = if (event.hasError()) "Recording Interrupted" else "Recording Saved"
                )

                activeRecording = null

                if (file != null && file.exists() && file.length() > 0) {
                    saveRecordingDirectly(file, durationMs)
                    onRecordingFinished?.invoke(file, durationMs)
                }

                if (event.hasError()) {
                    val errorMsg = "Recording finished with error: code ${event.error}"
                    Log.w("CameraRecorderManager", errorMsg)
                    if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                        _recordingState.value = _recordingState.value.copy(error = errorMsg)
                        onRecordingError?.invoke(errorMsg)
                    }
                }
            }
        }
    }

    private fun saveRecordingDirectly(file: File, durationMs: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val app = QuickRecordApplication.instance
                val titleFormat = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
                val title = "Quick Recording " + titleFormat.format(Date())
                val settings = app.settingsRepository.settings.value
                val entity = app.recordingsRepository.saveRecording(
                    title = title,
                    filePath = file.absolutePath,
                    uriString = file.toURI().toString(),
                    durationMs = durationMs,
                    fileSize = file.length(),
                    autoEncrypt = settings.autoEncrypt
                )
                if (settings.autoCloudBackup) {
                    app.cloudBackupManager.backupRecording(entity, settings.cloudEndpointUrl)
                }
                Log.d("CameraRecorderManager", "Recording saved directly to DB: ${file.absolutePath}, size=${file.length()}")
            } catch (e: Exception) {
                Log.e("CameraRecorderManager", "Error saving recording directly: ${e.message}", e)
            }
        }
    }

    private fun startTimer(maxDurationSeconds: Int) {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main) {
            var seconds = 0
            while (isActive) {
                delay(1000L)
                seconds++
                _recordingState.value = _recordingState.value.copy(elapsedSeconds = seconds)
                RecordingForegroundService.update(context, seconds, maxDurationSeconds)

                if (maxDurationSeconds > 0 && seconds >= maxDurationSeconds) {
                    stopRecording()
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRecordingOutputDir(): File {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "recordings")
        val dir = File(moviesDir, "QuickRecord")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun release() {
        if (isRecordingActive) {
            Log.d("CameraRecorderManager", "Ignoring release() because recording is currently active in background")
            return
        }
        stopRecording()
        stopTimer()
        cameraProvider?.unbindAll()
        persistentLifecycleOwner.destroy()
        isRecordingActive = false
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    companion object {
        @Volatile
        private var instance: CameraRecorderManager? = null

        fun getInstance(context: Context): CameraRecorderManager {
            return instance ?: synchronized(this) {
                instance ?: CameraRecorderManager(context.applicationContext).also {
                    instance = it
                    activeInstance = it
                }
            }
        }

        @Volatile
        var activeInstance: CameraRecorderManager? = null
            private set

        @Volatile
        var isRecordingActive: Boolean = false
            internal set

        fun stopActiveRecording(): Boolean {
            val inst = activeInstance ?: instance
            return if (inst != null && inst.recordingState.value.isRecording) {
                inst.stopRecording()
                true
            } else {
                false
            }
        }
    }
}
