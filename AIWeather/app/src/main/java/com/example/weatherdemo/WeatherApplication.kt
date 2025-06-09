package com.example.weatherdemo

import android.app.Application
import com.example.weatherdemo.database.WeatherDatabase
import com.example.weatherdemo.network.WeatherApiService
import com.example.weatherdemo.network.OpenRouterApiService
import com.example.weatherdemo.repository.WeatherRepository

class WeatherApplication : Application() {
    
    val database by lazy { WeatherDatabase.getDatabase(this) }
    val apiService by lazy { WeatherApiService.getInstance() }
    val openRouterApiService by lazy { OpenRouterApiService.getInstance() }
    val repository by lazy { WeatherRepository(database.weatherDao(), apiService) }
    
    override fun onCreate() {
        super.onCreate()
    }
} 