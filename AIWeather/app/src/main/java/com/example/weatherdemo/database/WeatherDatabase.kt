package com.example.weatherdemo.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.data.AstroData

@Database(
    entities = [WeatherData::class, HourlyWeatherData::class, AstroData::class],
    version = 3,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    
    abstract fun weatherDao(): WeatherDao
    
    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null
        
        fun getDatabase(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "weather_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 