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

    // Progress tracking
    private val handler = Handler(Looper.getMainLooper())
    private var isTracking = false

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
                        // Update playback state during progress update
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

            // Continue updating while tracking is enabled
            if (isTracking) {
                handler.postDelayed(this, 1000) // Update every second
            } else {
                Log.d("MusicService", "Tracking is disabled")
            }
        }
    }

    companion object {
        const val ACTION_TRACK_CHANGED = "com.example.musicapp.ACTION_TRACK_CHANGED"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.example.musicapp.ACTION_PLAYBACK_STATE_CHANGED"
        const val EXTRA_TRACK = "extra_track"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
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
        Log.d("MusicService", "Starting playback of track: ${track.title}")

        if (track.id == currentTrack?.id && mediaPlayer?.isPlaying == true) {
            Log.d("MusicService", "Track is already playing")
            // Still send playback state to sync UI
            sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                putExtra(EXTRA_IS_PLAYING, true)
                putExtra(EXTRA_TRACK, track)
            })
            return
        }

        stopPlayback()
        currentTrack = track
        currentTrackIndex = tracks.indexOf(track)

        // Notify track change immediately to update UI
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
                    // Send playback state change after MediaPlayer is ready
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, true)
                        putExtra(EXTRA_TRACK, track)
                    })
                }
                setOnCompletionListener {
                    Log.d("MusicService", "Track completed")
                    stopTracking()
                    onCompletionListener?.invoke()
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, false)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
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

    private fun notifyTrackChanged(track: Track) {
        Log.d("MusicService", "Notifying track changed: ${track.title}")
        currentTrack = track
        onTrackChangedListener?.invoke(track)
        // Send both track changed and playback state changed broadcasts
        sendBroadcast(Intent(ACTION_TRACK_CHANGED).apply {
            putExtra(EXTRA_TRACK, track)
        })
        sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_PLAYING, false)
            putExtra(EXTRA_TRACK, track)
        })
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
        // Notify track change before starting playback
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
        // Notify track change before starting playback
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
                    // Notify state change immediately
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, false)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                } else {
                    Log.d("MusicService", "Track is already paused")
                }
            } ?: run {
                Log.e("MusicService", "Cannot pause - MediaPlayer is null")
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
                    // Notify state change immediately
                    sendBroadcast(Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
                        putExtra(EXTRA_IS_PLAYING, true)
                        putExtra(EXTRA_TRACK, currentTrack)
                    })
                } else {
                    Log.d("MusicService", "Track is already playing")
                }
            } ?: run {
                Log.e("MusicService", "Cannot resume - MediaPlayer is null")
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
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
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

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }
}