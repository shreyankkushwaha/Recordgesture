package com.example.ui.components

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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserSettings
import com.example.ui.theme.CrimsonPrimary

@Composable
fun SettingsSection(
    settings: UserSettings,
    onMaxDurationChanged: (Int) -> Unit,
    onUseFrontCameraChanged: (Boolean) -> Unit,
    onAutoEncryptChanged: (Boolean) -> Unit,
    onAutoCloudBackupChanged: (Boolean) -> Unit,
    onLockScreenNotifChanged: (Boolean) -> Unit,
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recording & Security Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Max Duration Selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Max Duration Limit",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val durationOptions = listOf(
                30 to "30s",
                60 to "1m",
                180 to "3m",
                300 to "5m",
                0 to "Unlimited"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durationOptions.forEach { (seconds, label) ->
                    val isSelected = settings.maxDurationSeconds == seconds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onMaxDurationChanged(seconds) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("duration_chip_$label"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonPrimary,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Camera Lens Toggle
            SettingsToggleRow(
                icon = Icons.Default.Cameraswitch,
                title = "Front Facing Camera",
                subtitle = "Record with selfie camera instead of main back lens",
                checked = settings.useFrontCamera,
                onCheckedChange = onUseFrontCameraChanged,
                testTag = "toggle_front_camera"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Auto AES-256 Encryption
            SettingsToggleRow(
                icon = Icons.Default.Lock,
                title = "Hardware AES-256 Encryption",
                subtitle = "Automatically encrypt saved files using Android KeyStore",
                checked = settings.autoEncrypt,
                onCheckedChange = onAutoEncryptChanged,
                testTag = "toggle_auto_encrypt"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Auto Cloud Backup
            SettingsToggleRow(
                icon = Icons.Default.CloudQueue,
                title = "Automatic Cloud Backup",
                subtitle = "Upload recordings to secure cloud storage upon completion",
                checked = settings.autoCloudBackup,
                onCheckedChange = onAutoCloudBackupChanged,
                testTag = "toggle_auto_backup"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lock Screen Notification
            SettingsToggleRow(
                icon = Icons.Default.NotificationsActive,
                title = "Lock Screen Quick-Start Notification",
                subtitle = "Keep an instant record action in lock screen notification shade",
                checked = settings.enableLockScreenNotification,
                onCheckedChange = onLockScreenNotifChanged,
                testTag = "toggle_lockscreen_notif"
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CrimsonPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = CrimsonPrimary
            )
        )
    }
}
