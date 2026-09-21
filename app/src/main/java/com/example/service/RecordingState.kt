package com.example.service

data class RecordingState(
    val isRecording: Boolean = false,
    val isPreparing: Boolean = false,
    val elapsedSeconds: Int = 0,
    val maxDurationSeconds: Int = 60,
    val currentFilePath: String? = null,
    val statusMessage: String = "Ready",
    val error: String? = null,
    val audioAmplitude: Float = 0f
)
