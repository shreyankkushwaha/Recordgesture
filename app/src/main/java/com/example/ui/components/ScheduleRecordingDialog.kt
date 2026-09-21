package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.schedule.ScheduledRecordingManager
import com.example.ui.theme.CrimsonPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleRecordingDialog(
    initialDurationSeconds: Int = 60,
    initialUseFrontCamera: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSchedule: (targetTimeMillis: Long, durationSeconds: Int, useFrontCamera: Boolean, preAlert: Boolean) -> Unit
) {
    // 0: Quick delay presets, 1: Exact Clock Time
    var timeModeTab by remember { mutableIntStateOf(0) }

    // Quick delay selection in seconds
    val quickDelays = remember {
        listOf(
            15 to "+15s (Test)",
            30 to "+30s",
            60 to "+1 min",
            180 to "+3 min",
            300 to "+5 min",
            600 to "+10 min",
            900 to "+15 min",
            1800 to "+30 min",
            3600 to "+1 hour"
        )
    }
    var selectedQuickDelaySec by remember { mutableIntStateOf(60) }

    // Exact time selection
    val calendarNow = remember { Calendar.getInstance() }
    var exactHour by remember {
        val nextHour = (calendarNow.get(Calendar.HOUR_OF_DAY) + 1) % 24
        mutableIntStateOf(nextHour)
    }
    var exactMinute by remember { mutableIntStateOf(0) }
    var isTomorrow by remember { mutableStateOf(false) }

    // Recording duration presets
    val durationPresets = remember {
        listOf(
            15 to "15s",
            30 to "30s",
            60 to "1 min",
            120 to "2 min",
            180 to "3 min",
            300 to "5 min",
            600 to "10 min",
            900 to "15 min",
            1800 to "30 min"
        )
    }
    var selectedDurationSec by remember { mutableIntStateOf(initialDurationSeconds) }

    // Camera Lens
    var useFrontCamera by remember { mutableStateOf(initialUseFrontCamera) }

    // Pre-alert vibration
    var preAlertEnabled by remember { mutableStateOf(true) }

    // Calculated target time epoch millis
    val calculatedTargetTimeMillis by remember(timeModeTab, selectedQuickDelaySec, exactHour, exactMinute, isTomorrow) {
        derivedStateOf {
            if (timeModeTab == 0) {
                System.currentTimeMillis() + (selectedQuickDelaySec * 1000L)
            } else {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, exactHour)
                    set(Calendar.MINUTE, exactMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (isTomorrow || timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                cal.timeInMillis
            }
        }
    }

    val previewDateStr by remember(calculatedTargetTimeMillis) {
        derivedStateOf {
            ScheduledRecordingManager.formatTargetTime(calculatedTargetTimeMillis)
        }
    }

    val previewDurationStr by remember(selectedDurationSec) {
        derivedStateOf {
            ScheduledRecordingManager.formatDuration(selectedDurationSec)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Schedule Recording",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select start time & capture duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Mode Tabs
                TabRow(
                    selectedTabIndex = timeModeTab,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[timeModeTab]),
                            color = CrimsonPrimary,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = timeModeTab == 0,
                        onClick = { timeModeTab = 0 },
                        text = {
                            Text(
                                "Quick Delay",
                                fontWeight = if (timeModeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (timeModeTab == 0) CrimsonPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = timeModeTab == 1,
                        onClick = { timeModeTab = 1 },
                        text = {
                            Text(
                                "Specific Clock Time",
                                fontWeight = if (timeModeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (timeModeTab == 1) CrimsonPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Selection Body
                if (timeModeTab == 0) {
                    Text(
                        text = "START RECORDING AFTER:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickDelays.forEach { (sec, label) ->
                            val isSelected = selectedQuickDelaySec == sec
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedQuickDelaySec = sec },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                    selectedLabelColor = CrimsonPrimary,
                                    selectedLeadingIconColor = CrimsonPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = CrimsonPrimary
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = "SET EXACT START TIME:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Hour & Minute Steppers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HOUR (24h)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { exactHour = (exactHour + 23) % 24 },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", exactHour),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                Button(
                                    onClick = { exactHour = (exactHour + 1) % 24 },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                        // Minute Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MINUTE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { exactMinute = (exactMinute + 55) % 60 },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("-", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", exactMinute),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                Button(
                                    onClick = { exactMinute = (exactMinute + 5) % 60 },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("+", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Today vs Tomorrow selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = !isTomorrow,
                            onClick = { isTomorrow = false },
                            label = { Text("Today (or next available)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CrimsonPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        FilterChip(
                            selected = isTomorrow,
                            onClick = { isTomorrow = true },
                            label = { Text("Tomorrow") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CrimsonPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Duration selection
                Text(
                    text = "RECORDING DURATION (AUTO-STOPS AFTER):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    durationPresets.forEach { (sec, label) ->
                        val isSelected = selectedDurationSec == sec
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDurationSec = sec },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CrimsonPrimary,
                                selectedLeadingIconColor = CrimsonPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = CrimsonPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: Camera Lens & Pre-Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Camera Lens", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (useFrontCamera) "Front Selfie Camera" else "Back HD Camera",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        FilterChip(
                            selected = !useFrontCamera,
                            onClick = { useFrontCamera = false },
                            label = { Text("Back") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CrimsonPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = useFrontCamera,
                            onClick = { useFrontCamera = true },
                            label = { Text("Front") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = CrimsonPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Pre-Alert Vibration", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tactile feedback 3s before recording",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = preAlertEnabled,
                        onCheckedChange = { preAlertEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Summary Preview Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CrimsonPrimary.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SCHEDULE SUMMARY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Starts: $previewDateStr",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Duration: $previewDurationStr (auto-saves on finish)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• Lens: ${if (useFrontCamera) "Front Camera" else "Back Camera HD"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            onConfirmSchedule(
                                calculatedTargetTimeMillis,
                                selectedDurationSec,
                                useFrontCamera,
                                preAlertEnabled
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_schedule_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm Schedule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
