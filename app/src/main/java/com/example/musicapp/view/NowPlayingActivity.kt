package com.example.musicapp.view

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.musicapp.R
import com.example.musicapp.databinding.ActivityNowPlayingBinding
import com.example.musicapp.model.Track
import com.example.musicapp.presenter.NowPlayingPresenter

class NowPlayingActivity : AppCompatActivity(), NowPlayingView {
    private lateinit var binding: ActivityNowPlayingBinding
    private lateinit var presenter: NowPlayingPresenter

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

        presenter = NowPlayingPresenter(this, this)

        val track = intent.getParcelableExtra<Track>(EXTRA_TRACK)
        val tracksList = intent.getParcelableArrayListExtra<Track>(EXTRA_TRACKS)

        setupControls()
        setupSeekBar()
        setupVolumeControl()
        binding.backButton.setOnClickListener {
            finish()
        }

        presenter.onCreate(track, tracksList)
    }

    override fun onResume() {
        super.onResume()
        // Hide system notification while NowPlaying is visible
        startService(Intent(this, com.example.musicapp.service.MusicService::class.java).apply {
            action = com.example.musicapp.service.MusicService.ACTION_HIDE_NOTIFICATION
        })
        presenter.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Show notification again if music still playing
        startService(Intent(this, com.example.musicapp.service.MusicService::class.java).apply {
            action = com.example.musicapp.service.MusicService.ACTION_SHOW_NOTIFICATION
        })
        presenter.onPause()
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        presenter.onBackPressed()
    }

    private fun setupControls() {
        binding.playPauseButton.setOnClickListener {
            presenter.onPlayPauseClicked()
        }

        binding.previousButton.setOnClickListener {
            presenter.onPreviousClicked()
        }

        binding.nextButton.setOnClickListener {
            presenter.onNextClicked()
        }

        binding.shuffleButton.setOnClickListener {
            presenter.onShuffleClicked()
        }

        binding.repeatButton.setOnClickListener {
            presenter.onRepeatClicked()
        }

        binding.volumeButton.setOnClickListener {
            presenter.onVolumeToggled()
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.max = 1000
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                presenter.onSeekBarChanged(progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                presenter.onSeekBarStartTracking()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { presenter.onSeekBarStopTracking(it.progress) }
            }
        })
    }

    private fun setupVolumeControl() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSeekBar.max = maxVolume
        binding.volumeSeekBar.progress = currentVolume

        binding.volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    presenter.onVolumeChanged(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun updateTrackUI(track: Track) {
        binding.titleTextView.text = track.title
        binding.trackTitleTextView.text = track.title
        binding.artistTextView.text = track.artist.name
    }

    override fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.playPauseButton.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    override fun updateSeekBar(progress: Int, duration: Int) {
        binding.seekBar.progress = progress
    }

    override fun updateTime(currentTime: String, totalTime: String) {
        binding.currentTimeTextView.text = currentTime
        binding.totalTimeTextView.text = totalTime
    }

    override fun updateVolume(progress: Int, max: Int) {
        binding.volumeSeekBar.max = max
        binding.volumeSeekBar.progress = progress
        binding.volumeButton.setImageResource(
            if (progress == 0) R.drawable.ic_volume_off else R.drawable.ic_volume
        )
    }

    override fun updateShuffleButton(isEnabled: Boolean) {
        binding.shuffleButton.alpha = if (isEnabled) 1f else 0.5f
        binding.shuffleButton.setBackgroundResource(
            if (isEnabled) R.drawable.shuffle_active_background else android.R.color.transparent
        )
    }

    override fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    override fun finishActivity() {
        finish()
    }
}