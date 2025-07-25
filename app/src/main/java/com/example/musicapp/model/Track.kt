package com.example.musicapp.model

import android.os.Parcelable
import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Track(
    @SerializedName("title")
    val title: String,
    @SerializedName("user")
    val artist: Artist,
    @SerializedName("track_cid")
    val trackCid: String?,
    @SerializedName("id")
    val id: String?,
    @SerializedName("is_streamable")
    val isStreamable: Boolean = false
) : Parcelable {
    @Parcelize
    data class Artist(
        @SerializedName("name")
        val name: String
    ) : Parcelable

    init {
        Log.d("Track", "Initialized with title: $title, id: $id, trackCid: $trackCid, isStreamable: $isStreamable")
    }
}