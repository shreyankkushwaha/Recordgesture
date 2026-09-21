package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.service.RecordingState
import com.example.ui.components.RecordHud
import com.example.ui.theme.QuickRecordTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recording_hud_screenshot() {
        composeTestRule.setContent {
            QuickRecordTheme {
                RecordHud(
                    recordingState = RecordingState(
                        isRecording = true,
                        elapsedSeconds = 18,
                        maxDurationSeconds = 60,
                        audioAmplitude = 0.7f
                    ),
                    onStopRecording = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
