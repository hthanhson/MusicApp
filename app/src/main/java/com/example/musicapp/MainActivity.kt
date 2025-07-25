package com.example.musicapp

import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicapp.databinding.ActivityMainBinding
import com.example.musicapp.model.Track
import com.example.musicapp.presenter.MainPresenter
import com.example.musicapp.service.MusicService
import com.example.musicapp.view.MainView
import com.example.musicapp.view.NowPlayingActivity
import com.example.musicapp.view.TrackAdapter

class MainActivity : AppCompatActivity(), MainView {
    private lateinit var binding: ActivityMainBinding
    private lateinit var presenter: MainPresenter
    private var currentTrack: Track? = null
    private var currentTrackIndex = -1
    private var tracks = mutableListOf<Track>()
    private var musicService: MusicService? = null
    private var playPauseUpdateHandler: Handler? = null
    private var playPauseUpdateRunnable: Runnable? = null

    private val trackAdapter = TrackAdapter(
        onItemClick = { track ->
            playTrack(track)
        },
        activity = this
    )

    private val nowPlayingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                when (intent.getStringExtra("action")) {
                    "close" -> {
                        stopPlayback()
                    }
                    else -> {
                        // Do nothing for unknown actions
                    }
                }
            }
        }
    }

    private val trackChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Received broadcast: ${intent?.action}")
            when (intent?.action) {
                MusicService.ACTION_TRACK_CHANGED -> {
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    track?.let {
                        Log.d("MainActivity", "Track change broadcast: ${it.title}, id: ${it.id}")
                        runOnUiThread {
                            currentTrack = it
                            currentTrackIndex = tracks.indexOf(it)
                            if (!tracks.contains(it)) {
                                tracks.add(it)
                            }
                            updateNowPlayingBar(it)
                            updatePlayPauseButton(musicService?.isPlaying() == true)
                        }
                    } ?: run {
                        Log.e("MainActivity", "Track change broadcast received but track is null")
                    }
                }
                MusicService.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
                    Log.d("MainActivity", "Playback state change - isPlaying: $isPlaying, track: ${track?.title}")
                    runOnUiThread {
                        track?.let {
                            currentTrack = it
                            currentTrackIndex = tracks.indexOf(it)
                            if (!tracks.contains(it)) {
                                tracks.add(it)
                            }
                            updateNowPlayingBar(it)
                        }
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
            Log.d("MainActivity", "MusicService connected")
            setupMusicService()
            musicService?.getCurrentTrack()?.let { track ->
                runOnUiThread {
                    currentTrack = track
                    currentTrackIndex = tracks.indexOf(track)
                    updateNowPlayingBar(track)
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                }
            } ?: run {
                runOnUiThread {
                    binding.nowPlayingBar.visibility = View.GONE
                    updatePlayPauseButton(false)
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MainActivity", "MusicService disconnected")
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        registerReceiver(
            trackChangeReceiver,
            IntentFilter().apply {
                addAction(MusicService.ACTION_TRACK_CHANGED)
                addAction(MusicService.ACTION_PLAYBACK_STATE_CHANGED)
            },
            receiverFlags
        )

        binding.trackRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.trackRecyclerView.adapter = trackAdapter

        presenter = MainPresenter(this)

        setupSearchUI()
        setupNowPlayingBar()

        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        musicService?.let { service ->
            service.getCurrentTrack()?.let { track ->
                currentTrack = track
                currentTrackIndex = tracks.indexOf(track)
                updateNowPlayingBar(track)
                updatePlayPauseButton(service.isPlaying())
                Log.d("MainActivity", "onResume: Synced with track ${track.title}, isPlaying: ${service.isPlaying()}")
            } ?: run {
                binding.nowPlayingBar.visibility = View.GONE
                updatePlayPauseButton(false)
                Log.d("MainActivity", "onResume: No current track")
            }
        } ?: run {
            Log.d("MainActivity", "MusicService not yet bound in onResume")
            binding.nowPlayingBar.visibility = View.GONE
            updatePlayPauseButton(false)
        }
        startPlayPauseUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopPlayPauseUpdate()
    }

    override fun onStop() {
        super.onStop()
        musicService?.setCallbacks(
            onCompletion = {},
            onPrepared = {},
            onProgressUpdate = { _, _ -> },
            onTrackChanged = {}
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackChangeReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
        try {
            unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unbinding service: ${e.message}")
        }
        musicService = null
    }

    private fun setupMusicService() {
        musicService?.setCallbacks(
            onCompletion = {
                runOnUiThread {
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                }
            },
            onPrepared = {
                runOnUiThread {
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                }
            },
            onProgressUpdate = { _, _ ->
                runOnUiThread {
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                }
            },
            onTrackChanged = { track ->
                Log.d("MainActivity", "Track changed callback received: ${track.title}")
                runOnUiThread {
                    currentTrack = track
                    currentTrackIndex = tracks.indexOf(track)
                    updateNowPlayingBar(track)
                    updatePlayPauseButton(musicService?.isPlaying() == true)
                }
            }
        )
    }

    private fun setupSearchUI() {
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    if (isNetworkAvailable()) {
                        presenter.searchTracks(query)
                    } else {
                        showError("Không có kết nối internet")
                    }
                } else {
                    showError("Vui lòng nhập từ khóa tìm kiếm")
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupNowPlayingBar() {
        binding.nowPlayingBar.setOnClickListener {
            currentTrack?.let { track ->
                val intent = NowPlayingActivity.createIntent(this, track, tracks)
                nowPlayingLauncher.launch(intent)
            }
        }

        binding.playPauseButton.setOnClickListener {
            if (musicService?.isPlaying() == true) {
                musicService?.pauseTrack()
            } else {
                musicService?.resumeTrack()
            }
            updatePlayPauseButton(musicService?.isPlaying() == true)
        }

        binding.closeButton.setOnClickListener {
            musicService?.stopPlayback()
            currentTrack = null
            currentTrackIndex = -1
            binding.nowPlayingBar.visibility = View.GONE
        }
    }

    private fun playTrack(track: Track) {
        Log.d("MainActivity", "Attempting to play track: ${track.title}, id: ${track.id}, isStreamable: ${track.isStreamable}")
        if (!track.isStreamable || track.id.isNullOrEmpty()) {
            showError("Track không thể stream hoặc thiếu ID: ${track.title}")
            return
        }
        if (musicService == null) {
            showError("Dịch vụ âm nhạc chưa sẵn sàng, vui lòng thử lại")
            return
        }

        currentTrack = track
        currentTrackIndex = tracks.indexOf(track)
        binding.nowPlayingBar.visibility = View.VISIBLE
        updateNowPlayingBar(track)

        musicService?.setTracks(tracks)
        musicService?.playTrack(track)

        val intent = NowPlayingActivity.createIntent(this, track, tracks)
        nowPlayingLauncher.launch(intent)
    }

    private fun updateNowPlayingBar(track: Track) {
        Log.d("MainActivity", "Updating now playing bar with track: ${track.title}")
        binding.nowPlayingBar.visibility = View.VISIBLE
        binding.nowPlayingTitle.text = track.title
        binding.nowPlayingArtist.text = track.artist.name
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        try {
            binding.playPauseButton.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            Log.d("MainActivity", "Updated play/pause button - isPlaying: $isPlaying")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating play/pause button: ${e.message}")
        }
    }

    private fun startPlayPauseUpdate() {
        stopPlayPauseUpdate()
        playPauseUpdateHandler = Handler(Looper.getMainLooper())
        playPauseUpdateRunnable = object : Runnable {
            override fun run() {
                try {
                    musicService?.let { service ->
                        val isPlaying = service.isPlaying()
                        val currentServiceTrack = service.getCurrentTrack()
                        runOnUiThread {
                            currentServiceTrack?.let { track ->
                                if (track != currentTrack) {
                                    currentTrack = track
                                    currentTrackIndex = tracks.indexOf(track)
                                    if (!tracks.contains(track)) {
                                        tracks.add(track)
                                    }
                                    updateNowPlayingBar(track)
                                    Log.d("MainActivity", "Periodic check: Updated track to ${track.title}")
                                }
                            }
                            updatePlayPauseButton(isPlaying)
                            Log.d("MainActivity", "Periodic check - isPlaying: $isPlaying")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in play/pause update: ${e.message}")
                }
                playPauseUpdateHandler?.postDelayed(this, 500)
            }
        }
        playPauseUpdateRunnable?.let { playPauseUpdateHandler?.post(it) }
    }

    private fun stopPlayPauseUpdate() {
        playPauseUpdateRunnable?.let { playPauseUpdateHandler?.removeCallbacks(it) }
        playPauseUpdateHandler = null
        playPauseUpdateRunnable = null
    }

    private fun stopPlayback() {
        musicService?.stopPlayback()
        currentTrack = null
        currentTrackIndex = -1
        binding.nowPlayingBar.visibility = View.GONE
        Log.d("MainActivity", "Playback stopped")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun showTracks(tracks: List<Track>) {
        this.tracks = tracks.toMutableList()
        trackAdapter.setTracks(tracks)
//        binding.searchEditText.text?.clear()
    }

    override fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }
}