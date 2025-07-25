package com.example.musicapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.musicapp.MainActivity
import com.example.musicapp.R
import com.example.musicapp.model.Track

class PlaybackNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.example.musicapp.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.musicapp.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.musicapp.ACTION_NEXT"
        const val ACTION_CLOSE = "com.example.musicapp.ACTION_CLOSE"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Controls for music playback"
                setShowBadge(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(false)
                enableVibration(false)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d("MusicNotificationManager", "Notification channel created with HIGH importance")
        }
    }
    
    fun createNotification(track: Track?, isPlaying: Boolean, progress: Int = 0): Notification {
        Log.d("MusicNotificationManager", "Creating notification for track: ${track?.title}, isPlaying: $isPlaying")

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track?.title ?: "No song playing")
            .setContentText(track?.artist?.name ?: "Unknown artist")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setColorized(true)
            .setDeleteIntent(createPendingIntent(ACTION_CLOSE))
            .setFullScreenIntent(contentPendingIntent, false)

        // Add action buttons (empty titles for icon-only)
        builder.addAction(
            R.drawable.ic_skip_previous,
            "",
            createPendingIntent(ACTION_PREVIOUS)
        )
        
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        builder.addAction(
            playPauseIcon,
            "",
            createPendingIntent(ACTION_PLAY_PAUSE)
        )
        
        builder.addAction(
            R.drawable.ic_skip_next,
            "",
            createPendingIntent(ACTION_NEXT)
        )
        
        // Apply MediaStyle so system shows icon-only controls
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0,1,2)
        )
        
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        Log.d("MusicNotificationManager", "Notification created successfully with HIGH priority")
        return notification
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    fun showNotification(track: Track?, isPlaying: Boolean, progress: Int = 0) {
        val notification = createNotification(track, isPlaying, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d("MusicNotificationManager", "Notification shown")
    }
    
    fun updateNotification(track: Track?, isPlaying: Boolean, progress: Int = 0) {
        showNotification(track, isPlaying, progress)
    }
    
    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d("MusicNotificationManager", "Notification hidden")
    }
}
