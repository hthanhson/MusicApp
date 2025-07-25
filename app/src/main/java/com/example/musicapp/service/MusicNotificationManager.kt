package com.example.musicapp.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.musicapp.MainActivity
import com.example.musicapp.R
import com.example.musicapp.model.Track

class MusicNotificationManager(private val context: Context) {
    
    companion object {
        // New channel id with LOW importance to suppress heads-up popups
        const val CHANNEL_ID = "music_playback_silent"
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
                "Music Playback (Silent)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
//    @SuppressLint("RemoteViewLayout")
    fun createNotification(track: Track?, isPlaying: Boolean, progress: Int = 0): Notification {
        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_music_player)
        
        track?.let {
            notificationLayout.setTextViewText(R.id.notification_song_title, it.title)
            notificationLayout.setTextViewText(R.id.notification_artist_name, it.artist.name)
        } ?: run {
            notificationLayout.setTextViewText(R.id.notification_song_title, "No song playing")
            notificationLayout.setTextViewText(R.id.notification_artist_name, "Unknown artist")
        }


        notificationLayout.setProgressBar(R.id.notification_progress_bar, 100, progress, false)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        notificationLayout.setImageViewResource(R.id.notification_btn_play_pause, playPauseIcon)

        notificationLayout.setOnClickPendingIntent(
            R.id.notification_btn_play_pause,
            createPendingIntent(ACTION_PLAY_PAUSE)
        )
        notificationLayout.setOnClickPendingIntent(
            R.id.notification_btn_previous,
            createPendingIntent(ACTION_PREVIOUS)
        )
        notificationLayout.setOnClickPendingIntent(
            R.id.notification_btn_next,
            createPendingIntent(ACTION_NEXT)
        )
        
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setCustomContentView(notificationLayout)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setDeleteIntent(createPendingIntent(ACTION_CLOSE))
            .build()
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
    }
    
    fun updateNotification(track: Track?, isPlaying: Boolean, progress: Int = 0) {
        showNotification(track, isPlaying, progress)
    }
    
    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
