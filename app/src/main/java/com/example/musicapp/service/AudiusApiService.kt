package com.example.musicapp.service

import com.example.musicapp.response.AudiusResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApiService {
    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String
    ): AudiusResponse
}