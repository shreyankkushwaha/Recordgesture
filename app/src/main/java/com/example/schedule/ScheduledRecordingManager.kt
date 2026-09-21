package com.example.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Singleton manager that coordinates scheduled future video and audio recordings.
 * Manages exact AlarmManager registrations, persistent preferences, active countdown state,
 * and system notifications with action controls.
 */
class ScheduledRecordingManager private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    private val _activeSchedule = MutableStateFlow<ScheduledRecording?>(null)
    val activeSchedule: StateFlow<ScheduledRecording?> = _activeSchedule.asStateFlow()

    private val _remainingSeconds = MutableStateFlow<Long>(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _formattedCountdown = MutableStateFlow("00:00:00")
    val formattedCountdown: StateFlow<String> = _formattedCountdown.asStateFlow()

    init {
        createNotificationChannel()
        loadPersistedSchedule()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_SCHEDULE,
                "Scheduled Video Recording",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications and controls for scheduled future recordings"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadPersistedSchedule() {
        val id = prefs.getString(KEY_SCHEDULE_ID, null) ?: return
        val targetTime = prefs.getLong(KEY_TARGET_TIME, 0L)
        val duration = prefs.getInt(KEY_DURATION_SECONDS, 60)
        val front = prefs.getBoolean(KEY_USE_FRONT, false)
        val preAlert = prefs.getBoolean(KEY_PRE_ALERT, true)
        val createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())

        val now = System.currentTimeMillis()
        if (targetTime <= now - 60_000L) {
            // Schedule already expired
            clearSchedulePrefs()
            return
        }

        val schedule = ScheduledRecording(
            id = id,
            targetTimeMillis = targetTime,
            durationSeconds = duration,
            useFrontCamera = front,
            preAlertEnabled = preAlert,
            createdAtMillis = createdAt
        )
        _activeSchedule.value = schedule
        startCountdownTimer(schedule)
    }

    /**
     * Schedules a future recording for the specified target timestamp and duration.
     */
    fun scheduleRecording(
        targetTimeMillis: Long,
        durationSeconds: Int,
        useFrontCamera: Boolean,
        preAlertEnabled: Boolean = true
    ): ScheduledRecording {
        val schedule = ScheduledRecording(
            targetTimeMillis = targetTimeMillis,
            durationSeconds = durationSeconds,
            useFrontCamera = useFrontCamera,
            preAlertEnabled = preAlertEnabled
        )

        // Save to preferences
        prefs.edit()
            .putString(KEY_SCHEDULE_ID, schedule.id)
            .putLong(KEY_TARGET_TIME, schedule.targetTimeMillis)
            .putInt(KEY_DURATION_SECONDS, schedule.durationSeconds)
            .putBoolean(KEY_USE_FRONT, schedule.useFrontCamera)
            .putBoolean(KEY_PRE_ALERT, schedule.preAlertEnabled)
            .putLong(KEY_CREATED_AT, schedule.createdAtMillis)
            .apply()

        _activeSchedule.value = schedule

        // Set AlarmManager exact wakeup alarm
        registerAlarm(schedule)

        // Show persistent notification with cancel action
        showScheduleNotification(schedule)

        // Start in-memory ticker
        startCountdownTimer(schedule)

        Log.d(TAG, "Scheduled recording for $targetTimeMillis (in ${(targetTimeMillis - System.currentTimeMillis()) / 1000}s) with duration ${durationSeconds}s")
        return schedule
    }

    private fun registerAlarm(schedule: ScheduledRecording) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerIntent = Intent(appContext, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_DURATION_SECONDS, schedule.durationSeconds)
            putExtra(EXTRA_USE_FRONT_CAMERA, schedule.useFrontCamera)
            putExtra(EXTRA_PRE_ALERT, schedule.preAlertEnabled)
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            triggerIntent,
            pendingFlags
        )

        val showIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            appContext,
            ALARM_SHOW_REQUEST_CODE,
            showIntent,
            pendingFlags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(schedule.targetTimeMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, schedule.targetTimeMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to setAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, schedule.targetTimeMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, schedule.targetTimeMillis, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to register alarm", ex)
            }
        }
    }

    /**
     * Cancels any pending scheduled recording.
     */
    fun cancelSchedule() {
        countdownJob?.cancel()
        countdownJob = null

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cancelIntent = Intent(appContext, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            cancelIntent,
            pendingFlags
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        dismissScheduleNotification()
        clearSchedulePrefs()
        _activeSchedule.value = null
        _remainingSeconds.value = 0L
        _formattedCountdown.value = "00:00:00"

        Log.d(TAG, "Scheduled recording canceled")
    }

    /**
     * Immediately triggers the scheduled recording without waiting for timer to expire.
     */
    fun triggerNow() {
        val current = _activeSchedule.value ?: return
        cancelSchedule()

        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            putExtra("EXTRA_SCHEDULED_DURATION", current.durationSeconds)
            putExtra("EXTRA_USE_FRONT_CAMERA", current.useFrontCamera)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        appContext.startActivity(intent)
    }

    private fun startCountdownTimer(schedule: ScheduledRecording) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val diffSeconds = maxOf(0L, (schedule.targetTimeMillis - now) / 1000L)
                _remainingSeconds.value = diffSeconds
                _formattedCountdown.value = formatSecondsToClock(diffSeconds)

                if (diffSeconds <= 0L) {
                    Log.d(TAG, "Countdown reached zero in foreground manager")
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun showScheduleNotification(schedule: ScheduledRecording) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Open app intent
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_OPEN_REQUEST_CODE,
            openIntent,
            pendingFlags
        )

        // Cancel action intent
        val cancelActionIntent = Intent(appContext, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_CANCEL_SCHEDULE
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_CANCEL_REQUEST_CODE,
            cancelActionIntent,
            pendingFlags
        )

        val formattedTarget = formatTargetTime(schedule.targetTimeMillis)
        val formattedDur = formatDuration(schedule.durationSeconds)
        val lensStr = if (schedule.useFrontCamera) "Front Camera" else "Back Camera HD"

        val notification = NotificationCompat.Builder(appContext, CHANNEL_SCHEDULE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recording Scheduled: $formattedTarget")
            .setContentText("Duration: $formattedDur • $lensStr")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Recording will start automatically on $formattedTarget for a duration of $formattedDur using $lensStr.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Schedule", cancelPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SCHEDULE, notification)
    }

    fun dismissScheduleNotification() {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_SCHEDULE)
    }

    private fun clearSchedulePrefs() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "ScheduledRecordingMgr"
        private const val PREFS_NAME = "scheduled_recording_prefs"

        private const val KEY_SCHEDULE_ID = "sched_id"
        private const val KEY_TARGET_TIME = "sched_target_time"
        private const val KEY_DURATION_SECONDS = "sched_duration"
        private const val KEY_USE_FRONT = "sched_use_front"
        private const val KEY_PRE_ALERT = "sched_pre_alert"
        private const val KEY_CREATED_AT = "sched_created_at"

        const val CHANNEL_SCHEDULE = "quick_record_schedule_channel"
        const val NOTIFICATION_ID_SCHEDULE = 3001

        const val ACTION_ALARM_TRIGGER = "com.example.schedule.ACTION_ALARM_TRIGGER"
        const val ACTION_CANCEL_SCHEDULE = "com.example.schedule.ACTION_CANCEL_SCHEDULE"

        const val EXTRA_SCHEDULE_ID = "extra_sched_id"
        const val EXTRA_DURATION_SECONDS = "extra_sched_duration"
        const val EXTRA_USE_FRONT_CAMERA = "extra_sched_use_front"
        const val EXTRA_PRE_ALERT = "extra_sched_pre_alert"

        private const val ALARM_REQUEST_CODE = 4001
        private const val ALARM_SHOW_REQUEST_CODE = 4002
        private const val NOTIFICATION_OPEN_REQUEST_CODE = 4003
        private const val NOTIFICATION_CANCEL_REQUEST_CODE = 4004

        @Volatile
        private var instance: ScheduledRecordingManager? = null

        fun getInstance(context: Context): ScheduledRecordingManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduledRecordingManager(context.applicationContext).also { instance = it }
            }
        }

        fun formatSecondsToClock(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSec = seconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainingSec)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, remainingSec)
            }
        }

        fun formatDuration(seconds: Int): String {
            if (seconds <= 0) return "Unlimited"
            val minutes = seconds / 60
            val remainingSec = seconds % 60
            return when {
                minutes > 0 && remainingSec > 0 -> "${minutes}m ${remainingSec}s"
                minutes > 0 -> "${minutes} min"
                else -> "${seconds} sec"
            }
        }

        fun formatTargetTime(timeMillis: Long): String {
            val targetCal = Calendar.getInstance().apply { timeInMillis = timeMillis }
            val nowCal = Calendar.getInstance()

            val isToday = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                    targetCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

            val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                    targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedTime = timeFormat.format(Date(timeMillis))

            return when {
                isToday -> "Today at $formattedTime"
                isTomorrow -> "Tomorrow at $formattedTime"
                else -> {
                    val fullFormat = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
                    fullFormat.format(Date(timeMillis))
                }
            }
        }
    }
}
