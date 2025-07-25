package com.example.musicapp.presenter

import android.util.Log
import com.example.musicapp.repository.AudiusRepository
import com.example.musicapp.model.Track
import com.example.musicapp.view.MainView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainPresenter(private val view: MainView) {
    private val repository = AudiusRepository()

    fun searchTracks(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.searchTracks(query)
            withContext(Dispatchers.Main) {
                result.onSuccess { tracks ->
                    view.showTracks(tracks)
                }.onFailure { error ->
                    view.showError("Lỗi khi tìm kiếm: ${error.message}")
                }
            }
        }
    }
}