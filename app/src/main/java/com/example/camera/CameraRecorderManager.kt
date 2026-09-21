package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
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
import com.example.service.RecordingForegroundService
import com.example.service.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val coroutineScope: CoroutineScope
) {
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var timerJob: Job? = null
    private var currentOutputFile: File? = null

    // Callback when recording is successfully finalized
    var onRecordingFinished: ((savedFile: File, durationMs: Long) -> Unit)? = null
    var onRecordingError: ((errorMessage: String) -> Unit)? = null

    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = false,
        onInitialized: (() -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView, useFrontCamera)
                onInitialized?.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
                _recordingState.value = _recordingState.value.copy(
                    error = "Failed to initialize camera: ${e.localizedMessage}"
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontCamera: Boolean = false
    ) {
        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _recordingState.value = _recordingState.value.copy(
                error = "Error binding camera: ${e.localizedMessage}"
            )
        }
    }

    fun startRecording(maxDurationSeconds: Int = 60) {
        val capture = videoCapture
        if (capture == null) {
            _recordingState.value = _recordingState.value.copy(
                error = "Camera not ready yet. Please wait a moment."
            )
            onRecordingError?.invoke("Camera not initialized")
            return
        }

        if (activeRecording != null) {
            // Already recording
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
                    statusMessage = "Recording Active",
                    error = null
                )

                // Start foreground service with persistent notification
                RecordingForegroundService.start(context, 0, maxDurationSeconds)

                // Launch timer
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

                if (event.hasError()) {
                    val errorMsg = "Recording finished with error: code ${event.error}"
                    _recordingState.value = _recordingState.value.copy(error = errorMsg)
                    onRecordingError?.invoke(errorMsg)
                } else if (file != null && file.exists()) {
                    onRecordingFinished?.invoke(file, durationMs)
                }
            }
        }
    }

    private fun startTimer(maxDurationSeconds: Int) {
        timerJob?.cancel()
        timerJob = coroutineScope.launch(Dispatchers.Main) {
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
        stopRecording()
        stopTimer()
        cameraProvider?.unbindAll()
        isRecordingActive = false
    }

    companion object {
        @Volatile
        var isRecordingActive: Boolean = false
            private set
    }
}
