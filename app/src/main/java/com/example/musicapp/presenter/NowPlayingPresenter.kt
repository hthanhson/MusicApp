package com.example.musicapp.presenter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.musicapp.model.Track
import com.example.musicapp.service.MusicService
import com.example.musicapp.view.NowPlayingView
import java.util.concurrent.TimeUnit

class NowPlayingPresenter(
    private val view: NowPlayingView,
    private val context: Context
) {
    private var musicService: MusicService? = null
    private var currentTrack: Track? = null
    private var tracks = mutableListOf<Track>()
    private var currentTrackIndex = 0
    private var isUserSeeking = false
    private var progressUpdateHandler: Handler? = null
    private var progressUpdateRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            Log.d("NowPlayingPresenter", "MusicService connected")
            setupMusicService()

            musicService?.getCurrentTrack()?.let { serviceTrack ->
                currentTrack = serviceTrack
                currentTrackIndex = tracks.indexOf(serviceTrack)
                if (currentTrackIndex == -1) {
                    tracks.add(serviceTrack)
                    currentTrackIndex = tracks.size - 1
                }
                view.updateTrackUI(serviceTrack)
                val isPlaying = musicService?.isPlaying() == true
                view.updatePlayPauseButton(isPlaying)
                val duration = musicService?.getDuration() ?: 0
                val position = musicService?.getCurrentPosition() ?: 0
                view.updateSeekBar(
                    if (duration > 0) ((position.toFloat() / duration.toFloat()) * 1000).toInt() else 0,
                    duration
                )
                view.updateTime(
                    formatDuration(position.toLong()),
                    formatDuration(duration.toLong())
                )
                startProgressUpdate()
            } ?: run {

                currentTrack?.let { track ->
                    musicService?.setTracks(tracks)
                    musicService?.playTrack(track)
                    view.updateTrackUI(track)
                    view.updatePlayPauseButton(true) 
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("NowPlayingPresenter", "MusicService disconnected")
            musicService = null
        }
    }

    private val trackChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("NowPlayingPresenter", "Received broadcast: ${intent?.action}")
            when (intent?.action) {
                MusicService.ACTION_TRACK_CHANGED -> {
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    track?.let {
                        Log.d("NowPlayingPresenter", "Track change: ${it.title}")
                        currentTrack = it
                        currentTrackIndex = tracks.indexOf(it)
                        if (currentTrackIndex == -1) {
                            tracks.add(it)
                            currentTrackIndex = tracks.size - 1
                        }
                        view.updateTrackUI(it)
                        val isPlaying = musicService?.isPlaying() == true
                        view.updatePlayPauseButton(isPlaying)
                        val duration = musicService?.getDuration() ?: 0
                        val position = musicService?.getCurrentPosition() ?: 0
                        view.updateSeekBar(
                            if (duration > 0) ((position.toFloat() / duration.toFloat()) * 1000).toInt() else 0,
                            duration
                        )
                        view.updateTime(
                            formatDuration(position.toLong()),
                            formatDuration(duration.toLong())
                        )
                    } ?: run {
                        Log.e("NowPlayingPresenter", "Track change broadcast received but track is null")
                    }
                }
                MusicService.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    Log.d("NowPlayingPresenter", "Playback state change - isPlaying: $isPlaying, track: ${track?.title}")
                    track?.let {
                        currentTrack = it
                        currentTrackIndex = tracks.indexOf(it)
                        if (currentTrackIndex == -1) {
                            tracks.add(it)
                            currentTrackIndex = tracks.size - 1
                        }
                        view.updateTrackUI(it)
                        val duration = musicService?.getDuration() ?: 0
                        val position = musicService?.getCurrentPosition() ?: 0
                        view.updateSeekBar(
                            if (duration > 0) ((position.toFloat() / duration.toFloat()) * 1000).toInt() else 0,
                            duration
                        )
                        view.updateTime(
                            formatDuration(position.toLong()),
                            formatDuration(duration.toLong())
                        )
                    }
                    view.updatePlayPauseButton(isPlaying)
                }
            }
        }
    }

    fun onCreate(track: Track?, tracksList: List<Track>?) {
        if (track == null || tracksList == null) {
            view.showError("Dữ liệu bài hát không hợp lệ")
            view.finishActivity()
            return
        }

        currentTrack = track
        tracks = tracksList.toMutableList()
        currentTrackIndex = tracks.indexOf(track)

        context.bindService(
            Intent(context, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        try {
            context.registerReceiver(
                trackChangeReceiver,
                IntentFilter().apply {
                    addAction(MusicService.ACTION_TRACK_CHANGED)
                    addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
                },
                receiverFlags
            )
            Log.d("NowPlayingPresenter", "BroadcastReceiver registered")
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error registering receiver: ${e.message}")
        }

        view.updateTrackUI(track)
        view.updateSeekBar(0, 0)
        view.updateTime("00:00", "00:00")
    }

    fun onResume() {
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        try {
            context.registerReceiver(
                trackChangeReceiver,
                IntentFilter().apply {
                    addAction(MusicService.ACTION_TRACK_CHANGED)
                    addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
                },
                receiverFlags
            )
            Log.d("NowPlayingPresenter", "BroadcastReceiver registered in onResume")
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error registering receiver: ${e.message}")
        }

        musicService?.let { service ->
            service.getCurrentTrack()?.let { track ->
                currentTrack = track
                currentTrackIndex = tracks.indexOf(track)
                if (currentTrackIndex == -1) {
                    tracks.add(track)
                    currentTrackIndex = tracks.size - 1
                }
                view.updateTrackUI(track)
                val isPlaying = service.isPlaying()
                view.updatePlayPauseButton(isPlaying)
                val duration = service.getDuration()
                val position = service.getCurrentPosition()
                view.updateSeekBar(
                    if (duration > 0) ((position.toFloat() / duration.toFloat()) * 1000).toInt() else 0,
                    duration
                )
                view.updateTime(
                    formatDuration(position.toLong()),
                    formatDuration(duration.toLong())
                )
            } ?: run {
                view.updatePlayPauseButton(false)
                view.updateSeekBar(0, 0)
                view.updateTime("00:00", "00:00")
            }
        } ?: run {
            Log.d("NowPlayingPresenter", "MusicService not yet bound in onResume")
            view.updatePlayPauseButton(false)
            view.updateSeekBar(0, 0)
            view.updateTime("00:00", "00:00")
        }
        startProgressUpdate()
    }

    fun onPause() {
        try {
            context.unregisterReceiver(trackChangeReceiver)
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error unregistering receiver: ${e.message}")
        }
    }

    fun onStop() {
        stopProgressUpdate()
    }

    fun onDestroy() {
        stopProgressUpdate()
        try {
            context.unregisterReceiver(trackChangeReceiver)
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error unregistering receiver: ${e.message}")
        }
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error unbinding service: ${e.message}")
        }
        musicService = null
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressUpdateHandler = Handler(Looper.getMainLooper())
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (!isUserSeeking) {
                    try {
                        musicService?.let { service ->
                            val position = service.getCurrentPosition()
                            val duration = service.getDuration()
                            if (duration > 0) {
                                val progress = ((position.toFloat() / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
                                view.updateSeekBar(progress, duration)
                                view.updateTime(
                                    formatDuration(position.toLong()),
                                    formatDuration(duration.toLong())
                                )
                            }
                            service.getCurrentTrack()?.let { track ->
                                if (track.id != currentTrack?.id) {
                                    currentTrack = track
                                    currentTrackIndex = tracks.indexOf(track)
                                    if (currentTrackIndex == -1) {
                                        tracks.add(track)
                                        currentTrackIndex = tracks.size - 1
                                    }
                                    view.updateTrackUI(track)
                                    view.updatePlayPauseButton(service.isPlaying())
                                }
                            }
                            view.updatePlayPauseButton(service.isPlaying())
                        } ?: run {
                            view.updatePlayPauseButton(false)
                            view.updateSeekBar(0, 0)
                            view.updateTime("00:00", "00:00")
                        }
                    } catch (e: Exception) {
                        Log.e("NowPlayingPresenter", "Error in progress update: ${e.message}")
                    }
                }
                progressUpdateHandler?.postDelayed(this, 100)
            }
        }
        progressUpdateRunnable?.let { progressUpdateHandler?.post(it) }
    }

    private fun stopProgressUpdate() {
        progressUpdateRunnable?.let { progressUpdateHandler?.removeCallbacks(it) }
        progressUpdateHandler = null
        progressUpdateRunnable = null
    }

    private fun setupMusicService() {
        musicService?.setCallbacks(
            onCompletion = {
                view.updateSeekBar(0, 0)
                view.updateTime("00:00", "00:00")
                view.updatePlayPauseButton(false)
            },
            onPrepared = {
                Log.d("NowPlayingPresenter", "MediaPlayer prepared, updating UI")
                val duration = musicService?.getDuration() ?: 0
                if (duration > 0) {
                    view.updateTime("00:00", formatDuration(duration.toLong()))
                }
                view.updatePlayPauseButton(true)  
                view.updateSeekBar(0, duration)
                startProgressUpdate()
            },
            onProgressUpdate = { position, duration ->
                if (!isUserSeeking) {
                    val progress = ((position.toFloat() / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
                    view.updateSeekBar(progress, duration)
                    view.updateTime(
                        formatDuration(position.toLong()),
                        formatDuration(duration.toLong())
                    )
                }
            },
            onTrackChanged = { track ->
                currentTrack = track
                currentTrackIndex = tracks.indexOf(track)
                if (currentTrackIndex == -1) {
                    tracks.add(track)
                    currentTrackIndex = tracks.size - 1
                }
                view.updateTrackUI(track)
                view.updatePlayPauseButton(musicService?.isPlaying() == true)
                val duration = musicService?.getDuration() ?: 0
                view.updateSeekBar(0, duration)
                view.updateTime("00:00", formatDuration(duration.toLong()))
            }
        )
    }

    fun onPlayPauseClicked() {
        try {
            val isPlaying = musicService?.isPlaying() == true
            if (isPlaying) {
                musicService?.pauseTrack()
                view.updatePlayPauseButton(false)
            } else {
                musicService?.resumeTrack()
                view.updatePlayPauseButton(true)
            }
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error handling play/pause: ${e.message}")
            view.showError("Lỗi khi phát/tạm dừng bài hát")
        }
    }

    fun onNextClicked() {
        musicService?.playNextTrack()
    }

    fun onPreviousClicked() {
        musicService?.playPreviousTrack()
    }

    fun onShuffleClicked() {
        val newShuffleState = !(musicService?.isShuffleEnabled() ?: false)
        musicService?.setShuffleEnabled(newShuffleState)
        view.updateShuffleButton(newShuffleState)
    }

    fun onRepeatClicked() {
        view.showError("Chức năng lặp lại sẽ sớm được triển khai")
    }

    fun onVolumeChanged(progress: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            progress,
            0
        )
    }

    fun onVolumeToggled() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val isMuted = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0
        if (isMuted) {
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0)
            view.updateVolume(5, audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC))
        } else {
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
            view.updateVolume(0, audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC))
        }
    }

    fun onSeekBarChanged(progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val duration = musicService?.getDuration() ?: 0
            if (duration > 0) {
                val position = ((progress.toFloat() / 1000f) * duration).toLong()
                view.updateTime(formatDuration(position), formatDuration(duration.toLong()))
            }
        }
    }

    fun onSeekBarStartTracking() {
        isUserSeeking = true
    }

    fun onSeekBarStopTracking(progress: Int) {
        try {
            val duration = musicService?.getDuration() ?: 0
            if (duration > 0) {
                val position = ((progress.toFloat() / 1000f) * duration).toInt()
                musicService?.seekTo(position)
            }
        } catch (e: Exception) {
            Log.e("NowPlayingPresenter", "Error seeking: ${e.message}")
            view.showError("Lỗi khi tua bài hát")
        } finally {
            isUserSeeking = false
        }
    }

    fun onBackPressed() {
        musicService?.stopPlayback()
        view.finishActivity()
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }
}