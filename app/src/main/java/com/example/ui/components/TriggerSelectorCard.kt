package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.TriggerAction
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun TriggerSelectorCard(
    currentTrigger: TriggerAction,
    isAccessibilityEnabled: Boolean,
    onSelectTrigger: (TriggerAction) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Quick Launch Action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose 1 trigger to start recording even when locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Options
            TriggerAction.entries.forEach { action ->
                val isSelected = action == currentTrigger
                TriggerOptionItem(
                    action = action,
                    isSelected = isSelected,
                    onClick = { onSelectTrigger(action) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Accessibility Warning if Volume buttons are selected but service is disabled
            val requiresAccessibility = currentTrigger == TriggerAction.VOLUME_LONG_PRESS ||
                    currentTrigger == TriggerAction.VOLUME_DOWN_DOUBLE ||
                    currentTrigger == TriggerAction.VOLUME_UP_DOUBLE

            if (requiresAccessibility) {
                Spacer(modifier = Modifier.height(10.dp))
                AccessibilityStatusBanner(
                    isEnabled = isAccessibilityEnabled,
                    onOpenSettings = onOpenAccessibilitySettings
                )
            }
        }
    }
}

@Composable
private fun TriggerOptionItem(
    action: TriggerAction,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (action) {
        TriggerAction.VOLUME_LONG_PRESS -> Icons.AutoMirrored.Filled.VolumeUp
        TriggerAction.VOLUME_DOWN_DOUBLE -> Icons.AutoMirrored.Filled.VolumeDown
        TriggerAction.VOLUME_UP_DOUBLE -> Icons.AutoMirrored.Filled.VolumeUp
        TriggerAction.QUICK_SETTINGS_TILE -> Icons.Default.ScreenLockPortrait
        TriggerAction.LOCKSCREEN_NOTIFICATION -> Icons.Default.Notifications
        TriggerAction.SHAKE_GESTURE -> Icons.Default.Sensors
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("trigger_option_${action.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CrimsonPrimary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                CrimsonPrimary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CrimsonPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) CrimsonPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) CrimsonPrimary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AccessibilityStatusBanner(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isEnabled) SuccessGreen.copy(alpha = 0.12f) else WarningAmber.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isEnabled) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnabled) {
                        "Accessibility Key Monitor: ACTIVE"
                    } else {
                        "Accessibility Permission Required for Hardware Buttons"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) SuccessGreen else WarningAmber
                )
            }

            if (!isEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android privacy protections require the Accessibility Service to detect hardware volume buttons while the app is in the background or locked. Tap below to enable 'Volume Button Recording Trigger' in Accessibility Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmber,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enable_accessibility_button")
                ) {
                    Text("Enable in Accessibility Settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
