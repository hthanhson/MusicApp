package com.example.musicapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.musicapp.service.MusicNotificationManager
import com.example.musicapp.service.MusicService

class MusicNotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.d("MusicNotificationReceiver", "Received action: ${intent.action}")
        
        when (intent.action) {
            MusicNotificationManager.ACTION_PLAY_PAUSE -> {
                sendActionToService(context, MusicService.ACTION_TOGGLE_PLAYBACK)
            }
            MusicNotificationManager.ACTION_PREVIOUS -> {
                sendActionToService(context, MusicService.ACTION_PREVIOUS)
            }
            MusicNotificationManager.ACTION_NEXT -> {
                sendActionToService(context, MusicService.ACTION_NEXT)
            }
            MusicNotificationManager.ACTION_CLOSE -> {
                sendActionToService(context, MusicService.ACTION_STOP)
            }
        }
    }
    
    private fun sendActionToService(context: Context, action: String) {
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        context.startService(serviceIntent)
    }
}
