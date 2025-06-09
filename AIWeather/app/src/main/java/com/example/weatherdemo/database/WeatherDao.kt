package com.example.weatherdemo.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.data.AstroData

@Dao
interface WeatherDao {
    
    @Query("SELECT * FROM weather_data WHERE cityName = :cityName AND date = :date")
    suspend fun getWeatherData(cityName: String, date: String): WeatherData?
    
    @Query("SELECT * FROM weather_data WHERE cityName = :cityName ORDER BY timestamp DESC")
    fun getWeatherDataByCityLiveData(cityName: String): LiveData<List<WeatherData>>
    
    @Query("SELECT * FROM weather_data WHERE cityName = :cityName ORDER BY timestamp DESC")
    suspend fun getWeatherDataByCity(cityName: String): List<WeatherData>
    
    @Query("SELECT DISTINCT cityName FROM weather_data ORDER BY timestamp DESC")
    suspend fun getAllCities(): List<String>
    
    @Query("SELECT DISTINCT cityName FROM weather_data ORDER BY timestamp DESC")
    fun getAllCitiesLiveData(): LiveData<List<String>>
    
    @Query("SELECT * FROM weather_data ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentWeatherData(): List<WeatherData>
    
    @Query("SELECT * FROM weather_data ORDER BY timestamp DESC LIMIT 20")
    fun getRecentWeatherDataLiveData(): LiveData<List<WeatherData>>
    
    @Query("""
        SELECT * FROM weather_data w1 
        WHERE w1.timestamp = (
            SELECT MAX(w2.timestamp) 
            FROM weather_data w2 
            WHERE w2.cityName = w1.cityName
        ) 
        GROUP BY w1.cityName 
        ORDER BY w1.timestamp DESC
    """)
    suspend fun getLatestWeatherDataPerCity(): List<WeatherData>
    
    @Query("""
        SELECT * FROM weather_data w1 
        WHERE w1.isLocationCity = 0 AND w1.timestamp = (
            SELECT MAX(w2.timestamp) 
            FROM weather_data w2 
            WHERE w2.cityName = w1.cityName AND w2.isLocationCity = 0
        ) 
        GROUP BY w1.cityName 
        ORDER BY w1.timestamp DESC
    """)
    suspend fun getUserCitiesOnly(): List<WeatherData>
    
    @Query("""
        SELECT * FROM weather_data 
        WHERE isLocationCity = 1 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLocationCity(): WeatherData?
    
    @Query("DELETE FROM weather_data WHERE isLocationCity = 1")
    suspend fun deleteLocationCities()
    
    @Query("""
        DELETE FROM weather_data 
        WHERE cityName IN (
            SELECT cityName FROM weather_data WHERE isLocationCity = 1
        ) AND isLocationCity = 0
    """)
    suspend fun cleanDuplicateLocationCities()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherData(weatherData: WeatherData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherDataList(weatherDataList: List<WeatherData>)
    
    @Update
    suspend fun updateWeatherData(weatherData: WeatherData)
    
    @Delete
    suspend fun deleteWeatherData(weatherData: WeatherData)
    
    @Query("DELETE FROM weather_data WHERE cityName = :cityName")
    suspend fun deleteWeatherDataByCity(cityName: String)
    
    @Query("DELETE FROM weather_data WHERE cityName = :cityName AND isLocationCity = 0")
    suspend fun deleteUserCityData(cityName: String)
    
    @Query("DELETE FROM weather_data WHERE timestamp < :timestamp")
    suspend fun deleteOldWeatherData(timestamp: Long)
    
    @Query("DELETE FROM weather_data")
    suspend fun deleteAllWeatherData()
    
    @Query("SELECT * FROM hourly_weather_data WHERE cityName = :cityName AND date = :date ORDER BY hour ASC")
    suspend fun getHourlyWeatherData(cityName: String, date: String): List<HourlyWeatherData>
    
    @Query("SELECT * FROM hourly_weather_data WHERE cityName = :cityName AND date = :date ORDER BY hour ASC")
    fun getHourlyWeatherDataLiveData(cityName: String, date: String): LiveData<List<HourlyWeatherData>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyWeatherData(hourlyData: HourlyWeatherData)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyWeatherDataList(hourlyDataList: List<HourlyWeatherData>)
    
    @Query("DELETE FROM hourly_weather_data WHERE cityName = :cityName AND date = :date")
    suspend fun deleteHourlyWeatherDataByCity(cityName: String, date: String)
    
    @Query("DELETE FROM hourly_weather_data WHERE timestamp < :timestamp")
    suspend fun deleteOldHourlyWeatherData(timestamp: Long)
    
    @Query("SELECT * FROM astro_data WHERE cityName = :cityName AND date = :date")
    suspend fun getAstroData(cityName: String, date: String): AstroData?
    
    @Query("SELECT * FROM astro_data WHERE cityName = :cityName AND date = :date")
    fun getAstroDataLiveData(cityName: String, date: String): LiveData<AstroData?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAstroData(astroData: AstroData)
    
    @Query("DELETE FROM astro_data WHERE cityName = :cityName AND date = :date")
    suspend fun deleteAstroDataByCity(cityName: String, date: String)
    
    @Query("DELETE FROM astro_data WHERE timestamp < :timestamp")
    suspend fun deleteOldAstroData(timestamp: Long)
} 