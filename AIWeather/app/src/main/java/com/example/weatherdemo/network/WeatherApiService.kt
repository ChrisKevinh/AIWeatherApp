package com.example.weatherdemo.network

import android.util.Log
import com.example.weatherdemo.data.WeatherResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class WeatherApiService {
    
    // 🔑 WeatherAPI.com API密钥
    private val apiKey = "bbb15ae0bad54e398ee144025250506"
    private val baseUrl = "http://api.weatherapi.com/v1"
    
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    private val gson = Gson()
    
    /**
     * 获取当前天气和未来几天的预报
     */
    suspend fun getCurrentWeatherAndForecast(
        location: String,
        days: Int = 7
    ): Result<WeatherResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/forecast.json?key=$apiKey&q=$location&days=$days&aqi=yes&alerts=yes"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val weatherResponse = gson.fromJson(responseBody, WeatherResponse::class.java)
                    Result.success(weatherResponse)
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取历史天气数据
     */
    suspend fun getHistoryWeather(
        location: String,
        date: String
    ): Result<WeatherResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/history.json?key=$apiKey&q=$location&dt=$date"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val weatherResponse = gson.fromJson(responseBody, WeatherResponse::class.java)
                    Result.success(weatherResponse)
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 搜索城市
     */
    suspend fun searchCities(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/search.json?key=$apiKey&q=$query"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                
                if (responseBody != null) {
                    val searchResults = gson.fromJson(responseBody, Array<SearchResult>::class.java)
                    Result.success(searchResults.toList())
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                Result.failure(Exception("请求失败: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: WeatherApiService? = null
        
        fun getInstance(): WeatherApiService {
            return INSTANCE ?: synchronized(this) {
                val instance = WeatherApiService()
                INSTANCE = instance
                instance
            }
        }
    }
}

// 搜索结果数据类
data class SearchResult(
    val id: Long,
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val url: String
) 