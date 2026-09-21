package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.TriggerAction
import com.example.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Quick Recorder", appName)
    }

    @Test
    fun `settings repository stores and updates trigger action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        assertEquals(TriggerAction.VOLUME_DOWN_DOUBLE, repository.settings.value.triggerAction)

        repository.updateTriggerAction(TriggerAction.VOLUME_LONG_PRESS)
        assertEquals(TriggerAction.VOLUME_LONG_PRESS, repository.settings.value.triggerAction)

        repository.updateTriggerAction(TriggerAction.QUICK_SETTINGS_TILE)
        assertEquals(TriggerAction.QUICK_SETTINGS_TILE, repository.settings.value.triggerAction)

        repository.updateTriggerAction(TriggerAction.VOLUME_LONG_PRESS)
        assertEquals(TriggerAction.VOLUME_LONG_PRESS, repository.settings.value.triggerAction)

        repository.updateMaxDuration(180)
        assertEquals(180, repository.settings.value.maxDurationSeconds)
    }

    @Test
    fun `crypto manager encrypts and decrypts test file successfully`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cryptoManager = CryptoManager()

        val sampleFile = File(context.cacheDir, "sample_video.mp4").apply {
            writeBytes("HEADER_DATA_SAMPLE_VIDEO_CAPTURE_123456789".toByteArray())
        }
        val encryptedFile = File(context.cacheDir, "sample_video.enc")
        val decryptedFile = File(context.cacheDir, "sample_video_decrypted.mp4")

        val encryptSuccess = cryptoManager.encryptFile(sampleFile, encryptedFile)
        assertTrue(encryptSuccess)
        assertTrue(encryptedFile.exists())
        assertTrue(encryptedFile.length() > 0)

        val decryptSuccess = cryptoManager.decryptFile(encryptedFile, decryptedFile)
        assertTrue(decryptSuccess)
        assertTrue(decryptedFile.exists())
        assertEquals("HEADER_DATA_SAMPLE_VIDEO_CAPTURE_123456789", decryptedFile.readText())

        // Cleanup
        sampleFile.delete()
        encryptedFile.delete()
        decryptedFile.delete()
    }

    @Test
    fun `scheduled recording manager schedules, tracks and cancels schedule`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.schedule.ScheduledRecordingManager.getInstance(context)

        // Cancel any previous state
        manager.cancelSchedule()
        org.junit.Assert.assertNull(manager.activeSchedule.value)

        val futureTarget = System.currentTimeMillis() + 120_000L // 2 minutes in future
        val schedule = manager.scheduleRecording(
            targetTimeMillis = futureTarget,
            durationSeconds = 90,
            useFrontCamera = true,
            preAlertEnabled = true
        )

        assertNotNull(manager.activeSchedule.value)
        assertEquals(schedule.id, manager.activeSchedule.value?.id)
        assertEquals(90, manager.activeSchedule.value?.durationSeconds)
        assertEquals(true, manager.activeSchedule.value?.useFrontCamera)
        assertEquals(true, manager.activeSchedule.value?.preAlertEnabled)

        // Test formatting helpers
        val formattedDur = com.example.schedule.ScheduledRecordingManager.formatDuration(90)
        assertEquals("1m 30s", formattedDur)

        val formattedClock = com.example.schedule.ScheduledRecordingManager.formatSecondsToClock(75)
        assertEquals("01:15", formattedClock)

        // Cancel schedule
        manager.cancelSchedule()
        org.junit.Assert.assertNull(manager.activeSchedule.value)
        assertEquals(0L, manager.remainingSeconds.value)
    }
}
