package com.example.musicapp.response

import com.example.musicapp.model.Track
import com.google.gson.annotations.SerializedName

data class AudiusResponse(
    @SerializedName("data")
    val data: List<Track>
)