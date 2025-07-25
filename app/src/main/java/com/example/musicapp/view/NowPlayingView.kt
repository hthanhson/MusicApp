package com.example.musicapp.view

import com.example.musicapp.model.Track

interface NowPlayingView {
    fun updateTrackUI(track: Track)
    fun updatePlayPauseButton(isPlaying: Boolean)
    fun updateSeekBar(progress: Int, duration: Int)
    fun updateTime(currentTime: String, totalTime: String)
    fun updateVolume(progress: Int, max: Int)
    fun updateShuffleButton(isEnabled: Boolean)
    fun showError(error: String)
    fun finishActivity()
}