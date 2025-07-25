package com.example.musicapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.musicapp.model.Track
import com.example.musicapp.service.MusicService

class TrackChangeReceiver : BroadcastReceiver() {
    private var onTrackChanged: ((Track) -> Unit)? = null

    fun setOnTrackChangedListener(listener: (Track) -> Unit) {
        onTrackChanged = listener
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MusicService.ACTION_TRACK_CHANGED) {
            val track = intent.getParcelableExtra<Track>(MusicService.EXTRA_TRACK)
            track?.let {
                Log.d("TrackChangeReceiver", "Received track change: ${it.title}")
                onTrackChanged?.invoke(it)
            }
        }
    }
} 