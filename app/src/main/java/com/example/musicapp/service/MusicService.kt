package com.example.musicapp.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.musicapp.model.Track

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private var currentTrack: Track? = null
    private var tracks = mutableListOf<Track>()
    private var isShuffleEnabled = false
    private var currentTrackIndex = -1
    private var onCompletionListener: (() -> Unit)? = null
    private var onPreparedListener: (() -> Unit)? = null
    private var onProgressUpdateListener: ((Int, Int) -> Unit)? = null
    private var onTrackChangedListener: ((Track) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTracking = false
    private var suppressNotification = false
    
    private lateinit var notificationManager: PlaybackNotificationManager

    companion object {
        const val ACTION_TRACK_CHANGED = "com.example.musicapp.ACTION_TRACK_CHANGED"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.example.musicapp.ACTION_PLAYBACK_STATE_CHANGED"
        const val EXTRA_TRACK = "extra_track"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        
        const val ACTION_TOGGLE_PLAYBACK = "com.example.musicapp.ACTION_TOGGLE_PLAYBACK"
        const val ACTION_PREVIOUS = "com.example.musicapp.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.musicapp.ACTION_NEXT"
        const val ACTION_STOP = "com.example.musicapp.ACTION_STOP"
        const val ACTION_HIDE_NOTIFICATION = "com.example.musicapp.ACTION_HIDE_NOTIFICATION"
        const val ACTION_SHOW_NOTIFICATION = "com.example.musicapp.ACTION_SHOW_NOTIFICATION"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = PlaybackNotificationManager(this)
        Log.d("MusicService", "Service created with fixed notification manager")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            Log.d("MusicService", "Received action: $action")
            when (action) {
                ACTION_TOGGLE_PLAYBACK -> {
                    if (isPlaying()) {
                        pauseTrack()
                    } else {
                        resumeTrack()
                    }
                }
                ACTION_PREVIOUS -> {
                    playPreviousTrack()
                }
                ACTION_NEXT -> {
                    playNextTrack()
                }
                ACTION_HIDE_NOTIFICATION -> {
                    stopForeground(false)
                    notificationManager.hideNotification()
                    suppressNotification = true
                }
                ACTION_SHOW_NOTIFICATION -> {
                    suppressNotification = false
                    if (isPlaying()) updateNotification()
                }
                ACTION_STOP -> {
                    stopPlayback()
                    notificationManager.hideNotification()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun setCallbacks(
        onCompletion: () -> Unit,
        onPrepared: () -> Unit,
        onProgressUpdate: (Int, Int) -> Unit,
        onTrackChanged: (Track) -> Unit
    ) {
        onCompletionListener = onCompletion
        onPreparedListener = onPrepared
        onProgressUpdateListener = onProgressUpdate
        onTrackChangedListener = onTrackChanged
    }

    fun setTracks(tracks: List<Track>) {
        this.tracks = tracks.toMutableList()
    }

    fun getCurrentTrack(): Track? = currentTrack

    fun isShuffleEnabled() = isShuffleEnabled

    fun setShuffleEnabled(enabled: Boolean) {
        isShuffleEnabled = enabled
    }

    fun playTrack(track: Track) {
        Log.d("MusicService", "Starting playback of track: ${track.title}, id: ${track.id}")

        if (track.id == currentTrack?.id && mediaPlayer?.isPlaying == true) {
            Log.d("MusicService", "Track is already playing")
            sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_PLAYING, true)
                putExtra(EXTRA_TRACK, track)
            })
            updateNotification()
            return
        }

        stopPlayback()
        currentTrack = track
        currentTrackIndex = tracks.indexOf(track)

        notifyTrackChanged(track)  

        val streamUrl = "https://discoveryprovider.audius.co/v1/tracks/${track.id}/stream?app_name=MyMusicApp"
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(streamUrl)
                setOnPreparedListener {
                    Log.d("MusicService", "MediaPlayer prepared, starting playback")
                    start()
                    startTracking()
                    onPreparedListener?.invoke()
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, true)
                        putExtra(EXTRA_TRACK, track)
                    })
                    updateNotification()
                    Log.d("MusicService", "Sent ACTION_PLAYBACK_STATE_CHANGED broadcast - isPlaying: true")
                }
                setOnCompletionListener {
                    Log.d("MusicService", "Track completed")
                    stopTracking()
                    onCompletionListener?.invoke()
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, false)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                    updateNotification()
                    playNextTrack()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
                    stopTracking()
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, false)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing track: ${e.message}")
            stopTracking()
            sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_PLAYING, false)
                putExtra(EXTRA_TRACK, currentTrack)
            })
        }
    }

    private fun startTracking() {
        Log.d("MusicService", "Starting progress tracking")
        isTracking = true
        handler.removeCallbacks(progressUpdateRunnable)
        handler.post(progressUpdateRunnable)
    }

    private fun stopTracking() {
        Log.d("MusicService", "Stopping progress tracking")
        isTracking = false
        handler.removeCallbacks(progressUpdateRunnable)
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                try {
                    val isPlaying = player.isPlaying
                    val currentPosition = player.currentPosition
                    val totalDuration = player.duration

                    Log.d("MusicService", "MediaPlayer state - isPlaying: $isPlaying, position: $currentPosition, duration: $totalDuration")

                    if (isPlaying && totalDuration > 0) {
                        onProgressUpdateListener?.invoke(currentPosition, totalDuration)
                        if (!suppressNotification) {
                            updateNotification(currentPosition, totalDuration)
                        }
                        sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                            putExtra(EXTRA_IS_PLAYING, true)
                            putExtra(EXTRA_TRACK, currentTrack)
                        })
                    } else {
                        Log.d("MusicService", "Skipping progress update - isPlaying: $isPlaying, duration: $totalDuration")
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Error updating progress: ${e.message}")
                }
            } ?: run {
                Log.d("MusicService", "MediaPlayer is null")
            }

            if (isTracking) {
                handler.postDelayed(this, 1000) 
            } else {
                Log.d("MusicService", "Tracking is disabled")
            }
        }
    }

    private fun notifyTrackChanged(track: Track) {
        Log.d("MusicService", "Notifying track changed: ${track.title}")
        currentTrack = track
        onTrackChangedListener?.invoke(track)
        sendBroadcast(Intent(ACTION_TRACK_CHANGED).apply {
            putExtra(EXTRA_TRACK, track)
        })
        updateNotification()
//        sendBroadcast(intent)
    }

    fun playNextTrack() {
        if (tracks.isEmpty()) return

        val nextTrack = if (isShuffleEnabled) {
            tracks.random()
        } else if (currentTrackIndex < tracks.size - 1) {
            tracks[currentTrackIndex + 1]
        } else {
            tracks.firstOrNull()
        } ?: return

        Log.d("MusicService", "Playing next track: ${nextTrack.title}")
        notifyTrackChanged(nextTrack)
        playTrack(nextTrack)
    }

    fun playPreviousTrack() {
        if (tracks.isEmpty() || currentTrackIndex <= 0) return

        val previousTrack = if (isShuffleEnabled) {
            tracks.random()
        } else {
            tracks[currentTrackIndex - 1]
        }

        Log.d("MusicService", "Playing previous track: ${previousTrack.title}")
        notifyTrackChanged(previousTrack)
        playTrack(previousTrack)
    }

    fun pauseTrack() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    Log.d("MusicService", "Pausing track")
                    player.pause()
                    stopTracking()
                    onProgressUpdateListener?.invoke(player.currentPosition, player.duration)
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, false)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                    updateNotification()
                } else {
                    Log.d("MusicService", "Track is already paused")
                }
            } ?: run {
                Log.e("MusicService", "Cannot pause - MediaPlayer is null")
                sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                    putExtra(EXTRA_IS_PLAYING, false)
                    putExtra(EXTRA_TRACK, currentTrack)
                })
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error pausing track: ${e.message}")
        }
    }

    fun resumeTrack() {
        try {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    Log.d("MusicService", "Resuming track")
                    player.start()
                    startTracking()
                    onProgressUpdateListener?.invoke(player.currentPosition, player.duration)
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, true)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                    updateNotification()
                } else {
                    Log.d("MusicService", "Track is already playing")
                }
            } ?: run {
                Log.e("MusicService", "Cannot resume - MediaPlayer is null")
                sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                    putExtra(EXTRA_IS_PLAYING, false)
                    putExtra(EXTRA_TRACK, currentTrack)
                })
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error resuming track: ${e.message}")
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.let { player ->
                Log.d("MusicService", "Seeking to position: $position")
                player.seekTo(position)
            } ?: run {
                Log.e("MusicService", "Cannot seek - MediaPlayer is null")
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error seeking: ${e.message}")
        }
    }

    fun isPlaying(): Boolean {
        return try {
            val isPlaying = mediaPlayer?.isPlaying == true
            Log.d("MusicService", "isPlaying called, result: $isPlaying")
            isPlaying
        } catch (e: IllegalStateException) {
            Log.e("MusicService", "Error checking isPlaying: ${e.message}")
            false
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                player.currentPosition
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error getting position: ${e.message}")
            0
        }
    }

    fun getDuration(): Int {
        return try {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                player.duration
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error getting duration: ${e.message}")
            0
        }
    }

    fun stopPlayback() {
        stopTracking()
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
            mediaPlayer = null
            currentTrack = null
            currentTrackIndex = -1
        } catch (e: Exception) {
            Log.e("MusicService", "Error stopping playback: ${e.message}")
        }
    }

    private fun updateNotification(progressMs: Int? = null, durationMs: Int? = null) {
        if (suppressNotification) return
        currentTrack?.let { track ->
            val playing = isPlaying()
            val progressPercent = if (progressMs != null && durationMs != null && durationMs > 0) {
                (progressMs * 100 / durationMs)
            } else 0

            val notification = notificationManager.createNotification(track, playing, progressPercent)

            if (playing) {
                startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notification)
            } else {
                notificationManager.updateNotification(track, false, progressPercent)
                stopForeground(false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        notificationManager.hideNotification()
        stopForeground(true)
        handler.removeCallbacksAndMessages(null)
        Log.d("MusicService", "Service destroyed, cleaned up notifications and foreground service")
    }
}