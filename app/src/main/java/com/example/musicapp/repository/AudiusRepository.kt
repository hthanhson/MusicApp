package com.example.musicapp.repository

import android.util.Log
import com.example.musicapp.model.Track
import com.example.musicapp.service.AudiusApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL

class AudiusRepository {
    private var apiService: AudiusApiService? = null
    private var baseUrl: String = "https://discoveryprovider.audius.co/" // Ưu tiên node hoạt động
    private val fallbackNodes = listOf(
        "https://discoveryprovider.audius.co/",
        "https://audius-discovery-1.cultur3stake.com/",
        "https://discoveryprovider2.audius.co/",
        "https://api.audius.org/"
    )

    init {
        initializeApiService()
    }

    private fun initializeApiService() {
        try {
            selectDiscoveryNode()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(AudiusApiService::class.java)
        } catch (e: Exception) {
            Log.e("AudiusRepository", "Error initializing API service: ${e.message}")
            // Không throw exception ở đây, để xử lý lỗi khi gọi API
        }
    }

    private fun selectDiscoveryNode() {
        var selectedNode: String? = null

        for (node in fallbackNodes) {
            try {
                val connection = URL("$node/v1/health").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000 // Giảm timeout xuống 5 giây
                connection.readTimeout = 5000
                connection.connect()

                if (connection.responseCode == 200) {
                    selectedNode = node
                    Log.d("AudiusRepository", "Selected active node: $node")
                    connection.disconnect()
                    break
                } else {
                    Log.w("AudiusRepository", "Node $node returned ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w("AudiusRepository", "Node $node failed: ${e.message}")
            }
        }

        baseUrl = selectedNode ?: fallbackNodes[0]
        Log.d("AudiusRepository", "Using node: $baseUrl")
    }

    suspend fun searchTracks(query: String): Result<List<Track>> {
        return try {
            // Nếu apiService chưa được khởi tạo, thử khởi tạo lại
            if (apiService == null) {
                initializeApiService()
            }

            val service = apiService ?: throw Exception("Không thể kết nối đến API")

            Log.d("AudiusRepository", "Searching tracks for query: $query with baseUrl: $baseUrl")
            val response = service.searchTracks(query, "MyMusicApp")

            if (response.data.isEmpty()) {
                Log.w("AudiusRepository", "No tracks found for query: $query")
            }
            Result.success(response.data)
        } catch (e: Exception) {
            Log.e("AudiusRepository", "Search error: ${e.message}")
            Result.failure(Exception("Không thể tìm kiếm: ${e.message}"))
        }
    }
}