package com.example.schedule

import java.util.UUID

/**
 * Model representing a user-scheduled future video and audio recording.
 *
 * @param id Unique identifier for the scheduled task
 * @param targetTimeMillis Exact epoch timestamp in milliseconds when recording should begin
 * @param durationSeconds Duration in seconds for the recorded video (e.g. 60 for 1 min, 180 for 3 min)
 * @param useFrontCamera Whether to record with the front selfie camera or back HD camera
 * @param preAlertEnabled Whether to vibrate / sound an alert 3 seconds prior to start
 * @param createdAtMillis Epoch timestamp when this schedule was registered
 */
data class ScheduledRecording(
    val id: String = UUID.randomUUID().toString(),
    val targetTimeMillis: Long,
    val durationSeconds: Int = 60,
    val useFrontCamera: Boolean = false,
    val preAlertEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)
