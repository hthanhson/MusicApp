package com.example.musicapp.view

import android.content.*
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.musicapp.R
import com.example.musicapp.databinding.ActivityNowPlayingBinding
import com.example.musicapp.model.Track
import com.example.musicapp.receiver.TrackChangeReceiver
import com.example.musicapp.service.MusicService
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class NowPlayingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNowPlayingBinding
    private lateinit var audioManager: AudioManager
    private var musicService: MusicService? = null
    private var isUserSeeking = false
    private var isShuffleEnabled = false
    private var isFullscreen = false
    private var currentTrackIndex = 0
    private var tracks = mutableListOf<Track>()
    private var currentTrack: Track? = null

    private val trackChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.ACTION_TRACK_CHANGED -> {
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    track?.let {
                        Log.d("NowPlayingActivity", "Received track change: ${it.title}")
                        currentTrack = it
                        currentTrackIndex = tracks.indexOf(it)
                        // Update tracks list if needed
                        if (!tracks.contains(it)) {
                            tracks.add(it)
                        }
                        runOnUiThread {
                            updateTrackUI(it)
                            updatePlayPauseButton(true) // New track should show pause
                            // Reset seekbar and time
                            binding.seekBar.progress = 0
                            binding.currentTimeTextView.text = "00:00"
                            val duration = musicService?.getDuration() ?: 0
                            binding.totalTimeTextView.text = formatDuration(duration.toLong())
                        }
                    }
                }
                MusicService.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    Log.d("NowPlayingActivity", "Received playback state change - isPlaying: $isPlaying")
                    track?.let {
                        currentTrack = it
                        currentTrackIndex = tracks.indexOf(it)
                        // Update tracks list if needed
                        if (!tracks.contains(it)) {
                            tracks.add(it)
                        }
                    }
                    runOnUiThread {
                        track?.let { updateTrackUI(it) }
                        updatePlayPauseButton(isPlaying)
                    }
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            setupMusicService()

            musicService?.getCurrentTrack()?.let { serviceTrack ->
                currentTrack = serviceTrack
                currentTrackIndex = tracks.indexOf(serviceTrack)

                runOnUiThread {
                    updateTrackUI(serviceTrack)

                    Handler(Looper.getMainLooper()).postDelayed({
                        val isActuallyPlaying = musicService?.isPlaying() == true
                        updatePlayPauseButton(isActuallyPlaying)
                        Log.d("NowPlayingActivity", "Delayed check - isPlaying: $isActuallyPlaying")
                    }, 300)
                }
            } ?: run {
                currentTrack?.let { track ->
                    musicService?.setTracks(tracks)
                    musicService?.playTrack(track)
                    runOnUiThread {
                        updateTrackUI(track)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    runOnUiThread {
                        updatePlayPauseButton(isPlaying)
                    }
                }
            }
        }
    }

    private fun startPlayback() {
        currentTrack()?.let { track ->
            Log.d("NowPlayingActivity", "Starting playback of track: ${track.title}")
            musicService?.setTracks(tracks)
            musicService?.playTrack(track)
            // Update UI immediately
            updatePlayPauseButton(true)
            updateTrackUI(track)
        }
    }

    companion object {
        private const val EXTRA_TRACK = "extra_track"
        private const val EXTRA_TRACKS = "extra_tracks"

        fun createIntent(context: Context, track: Track, tracks: List<Track>): Intent {
            return Intent(context, NowPlayingActivity::class.java).apply {
                putExtra(EXTRA_TRACK, track)
                putParcelableArrayListExtra(EXTRA_TRACKS, ArrayList(tracks))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        val filter = IntentFilter(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
//        registerReceiver(playbackReceiver, filter)
        // Register broadcast receiver with flags
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        // Register receiver before initializing UI
        registerReceiver(
            trackChangeReceiver,
            IntentFilter().apply {
                addAction(MusicService.ACTION_TRACK_CHANGED)
                addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
            },
            receiverFlags
        )

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val track = intent.getParcelableExtra<Track>(EXTRA_TRACK)
        val tracksList = intent.getParcelableArrayListExtra<Track>(EXTRA_TRACKS)

        if (track == null || tracksList == null) {
            finish()
            return
        }

        tracks = tracksList.toMutableList()
        currentTrack = track
        currentTrackIndex = tracks.indexOf(track)

        // Initialize UI first
        updateTrackUI(track)
        setupControls()
        setupSeekBar()
        setupVolumeControl()

        // Setup back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Start progress update immediately
        startProgressUpdate()

        // Bind to MusicService
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        
        // Re-register receiver
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        
        try {
            registerReceiver(
                trackChangeReceiver,
                IntentFilter().apply {
                    addAction(MusicService.ACTION_TRACK_CHANGED)
                    addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
                },
                receiverFlags
            )
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error registering receiver: ${e.message}")
        }

        // Sync with current track
        musicService?.getCurrentTrack()?.let { track ->
            currentTrack = track
            currentTrackIndex = tracks.indexOf(track)
            updateTrackUI(track)
            updatePlayPauseButton(musicService?.isPlaying() == true)
        }
        
        startProgressUpdate()
    }

override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(trackChangeReceiver)
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error unregistering receiver: ${e.message}")
        }
    }

override fun onStop() {
        super.onStop()
        stopProgressUpdate()
    }

private var progressUpdateHandler: Handler? = null
private var progressUpdateRunnable: Runnable? = null

private fun startProgressUpdate() {
        stopProgressUpdate() // Clear any existing updates
        
        progressUpdateHandler = Handler(Looper.getMainLooper())
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (!isFinishing && !isUserSeeking) {
                    try {
                        musicService?.let { service ->
                            val position = service.getCurrentPosition()
                            val duration = service.getDuration()

                            if (duration > 0) {
                                val progress = ((position.toFloat() / duration.toFloat()) * 1000).toInt()
                                binding.seekBar.progress = progress
                                binding.currentTimeTextView.text = formatDuration(position.toLong())
                                binding.totalTimeTextView.text = formatDuration(duration.toLong())
                            }

                            // Also check if we need to update the current track
                            service.getCurrentTrack()?.let { currentTrackFromService ->
                                if (currentTrack?.id != currentTrackFromService.id) {
                                    currentTrack = currentTrackFromService
                                    currentTrackIndex = tracks.indexOf(currentTrackFromService)
                                    updateTrackUI(currentTrackFromService)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NowPlayingActivity", "Error in progress update: ${e.message}")
                    }
                }
                progressUpdateHandler?.postDelayed(this, 100)
            }
        }
        progressUpdateRunnable?.let {
            progressUpdateHandler?.post(it)
        }
    }

private fun stopProgressUpdate() {
        progressUpdateRunnable?.let {
            progressUpdateHandler?.removeCallbacks(it)
        }
        progressUpdateHandler = null
        progressUpdateRunnable = null
    }

override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        try {
            unregisterReceiver(trackChangeReceiver)
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error unregistering receiver: ${e.message}")
        }
        try {
            unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error unbinding service: ${e.message}")
        }
        musicService = null
    }

    private fun setupMusicService() {
        musicService?.setCallbacks(
            onCompletion = {
                runOnUiThread {
                    binding.seekBar.progress = 0
                    binding.currentTimeTextView.text = "00:00"
                    updatePlayPauseButton(false)
                }
            },
            onPrepared = {
                runOnUiThread {
                    val duration = musicService?.getDuration() ?: 0
                    if (duration > 0) {
                        binding.totalTimeTextView.text = formatDuration(duration.toLong())
                    }
                    updatePlayPauseButton(true)
                }
            },
            onProgressUpdate = { position, duration ->
                if (!isFinishing && !isUserSeeking) {
                    runOnUiThread {
                        if (duration > 0) {
                            val progress = ((position.toFloat() / duration.toFloat()) * 1000).toInt().coerceIn(0, 1000)
                            binding.seekBar.progress = progress
                            binding.currentTimeTextView.text = formatDuration(position.toLong())
                            binding.totalTimeTextView.text = formatDuration(duration.toLong())
                        }
                    }
                }
            },
            onTrackChanged = { track ->
                runOnUiThread {
                    currentTrackIndex = tracks.indexOf(track)
                    updateTrackUI(track)
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                    // Update tracks list if needed
                    if (!tracks.contains(track)) {
                        tracks.add(track)
                    }
                }
            }
        )
    }

    private fun setupUI(track: Track) {
        binding.titleTextView.text = track.title
        binding.trackTitleTextView.text = track.title
        binding.artistTextView.text = track.artist.name

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupVolumeControl() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSeekBar.max = maxVolume
        binding.volumeSeekBar.progress = currentVolume

        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        0
                    )
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener {
            try {
                val isPlaying = musicService?.isPlaying() == true
                Log.d("NowPlayingActivity", "Play/Pause clicked, current state: $isPlaying")
                if (isPlaying) {
                    musicService?.pauseTrack()
                    updatePlayPauseButton(false)
                } else {
                    musicService?.resumeTrack()
                    updatePlayPauseButton(true)
                }
            } catch (e: Exception) {
                Log.e("NowPlayingActivity", "Error handling play/pause: ${e.message}")
            }
        }

        binding.previousButton.setOnClickListener {
            musicService?.playPreviousTrack()
        }

        binding.nextButton.setOnClickListener {
            musicService?.playNextTrack()
        }

        binding.shuffleButton.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            musicService?.setShuffleEnabled(isShuffleEnabled)
            binding.shuffleButton.alpha = if (isShuffleEnabled) 1f else 0.5f
            binding.shuffleButton.setBackgroundResource(
                if (isShuffleEnabled) R.drawable.shuffle_active_background else android.R.color.transparent
            )
        }

        binding.repeatButton.setOnClickListener {
            Toast.makeText(this, "Repeat functionality coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.volumeButton.setOnClickListener {
            val isMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            if (isMuted) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, binding.volumeSeekBar.progress, 0)
                binding.volumeButton.setImageResource(R.drawable.ic_volume)
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                binding.volumeButton.setImageResource(R.drawable.ic_volume_off)
            }
            binding.volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.max = 1000
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        val duration = musicService?.getDuration() ?: 0
                        if (duration > 0) {
                            val position = ((progress.toFloat() / 1000f) * duration).toLong()
                            binding.currentTimeTextView.text = formatDuration(position)
                            Log.d("NowPlayingActivity", "User dragging - progress: $progress, time: ${formatDuration(position)}")
                        }
                    } catch (e: Exception) {
                        Log.e("NowPlayingActivity", "Error in onProgressChanged: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                Log.d("NowPlayingActivity", "User started seeking")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                try {
                    seekBar?.let { bar ->
                        val duration = musicService?.getDuration() ?: 0
                        if (duration > 0) {
                            val position = ((bar.progress.toFloat() / 1000f) * duration).toInt()
                            musicService?.seekTo(position)
                            Log.d("NowPlayingActivity", "User seeked to: ${formatDuration(position.toLong())}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NowPlayingActivity", "Error in onStopTrackingTouch: ${e.message}")
                    e.printStackTrace()
                } finally {
                    isUserSeeking = false
                    Log.d("NowPlayingActivity", "User finished seeking")
                }
            }
        })
    }

    private fun currentTrack(): Track? {
        return if (currentTrackIndex in tracks.indices) {
            tracks[currentTrackIndex]
        } else null
    }

    private fun playNextTrack() {
        Log.d("NowPlayingActivity", "Playing next track")
        musicService?.playNextTrack()
    }

    private fun updateTrackUI(track: Track) {
        Log.d("NowPlayingActivity", "Updating UI for track: ${track.title}")
        binding.titleTextView.text = track.title
        binding.trackTitleTextView.text = track.title
        binding.artistTextView.text = track.artist.name
        binding.seekBar.progress = 0
        binding.currentTimeTextView.text = "00:00"
        val duration = musicService?.getDuration() ?: 0
        binding.totalTimeTextView.text = formatDuration(duration.toLong())
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            binding.topBar.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            binding.topBar.visibility = View.VISIBLE
            binding.bottomBar.visibility = View.VISIBLE
        }
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isFullscreen) {
            binding.topBar.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        // Stop playback when going back
        musicService?.stopPlayback()
        super.onBackPressed()
    }

    private fun updatePlayPauseButton() {
        try {
            val isPlaying = musicService?.isPlaying() == true
            binding.playPauseButton.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            Log.d("NowPlayingActivity", "Updated play/pause button - isPlaying: $isPlaying")
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error updating play/pause button: ${e.message}")
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        try {
            Log.d("NowPlayingActivity", "Updating play/pause button - isPlaying: $isPlaying")
            binding.playPauseButton.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        } catch (e: Exception) {
            Log.e("NowPlayingActivity", "Error updating play/pause button: ${e.message}")
        }
    }
}