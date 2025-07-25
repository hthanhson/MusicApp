package com.example.musicapp.view

import com.example.musicapp.model.Track

interface MainView {
    fun showTracks(tracks: List<Track>)
    fun showError(error: String)
}