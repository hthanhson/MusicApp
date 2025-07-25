package com.example.musicapp.model

import com.google.gson.annotations.SerializedName

data class AudiusResponse(
    @SerializedName("data")
    val data: List<Track>
)