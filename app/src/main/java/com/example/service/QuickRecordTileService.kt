package com.example.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.camera.CameraRecorderManager

class QuickRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRecording = CameraRecorderManager.isRecordingActive
        tile.state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRecording) "Stop Recording" else "Quick Record"
        tile.contentDescription = if (isRecording) "Tap to stop recording" else "Tap to start background recording"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        val isRecording = CameraRecorderManager.isRecordingActive
        if (isRecording) {
            CameraRecorderManager.stopActiveRecording()
            updateTileState()
            return
        }

        // Start background recording immediately
        RecordingForegroundService.start(applicationContext, 0, 60)
        val recorderManager = CameraRecorderManager.getInstance(applicationContext)
        if (recorderManager.isInitialized) {
            recorderManager.startRecording(60)
        } else {
            recorderManager.initializeCamera(previewView = null, useFrontCamera = false) {
                recorderManager.startRecording(60)
            }
        }
        updateTileState()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                501,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

