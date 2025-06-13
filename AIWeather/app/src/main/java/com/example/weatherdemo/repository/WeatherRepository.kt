package com.example.weatherdemo.repository

import androidx.lifecycle.LiveData
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.data.AstroData
import com.example.weatherdemo.data.WeatherResponse
import com.example.weatherdemo.database.WeatherDao
import com.example.weatherdemo.network.SearchResult
import com.example.weatherdemo.network.WeatherApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val apiService: WeatherApiService
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * 获取天气数据，优先从本地数据库获取，如果没有或过期则从网络获取
     */
    suspend fun getWeatherData(cityName: String, date: String? = null, autoSave: Boolean = true): Result<WeatherData> {
        return withContext(Dispatchers.IO) {
            val targetDate = date ?: dateFormat.format(Date())
            
            Log.d("WeatherRepository", "获取天气数据：$cityName, 自动保存：$autoSave")
            
            // 先检查本地数据库
            val localData = weatherDao.getWeatherData(cityName, targetDate)
            
            // 如果本地有数据且不超过1小时，直接返回（但要验证数据完整性）
            if (localData != null && !isDataExpired(localData.timestamp)) {
                // 验证本地数据的温度字段是否完整
                if (isWeatherDataComplete(localData)) {
                    Log.d("WeatherRepository", "返回完整的本地缓存数据：${localData.cityName}")
                    return@withContext Result.success(localData)
                } else {
                    Log.w("WeatherRepository", "本地缓存数据不完整，尝试重新获取：${localData.cityName}")
                }
            }
            
            // 从网络获取数据（带重试机制）
            val maxAttempts = 3
            
            for (attempt in 1..maxAttempts) {
                Log.d("WeatherRepository", "网络请求尝试 $attempt/$maxAttempts")
                
                try {
                    val networkResult = if (date == null) {
                        // 获取当前天气
                        apiService.getCurrentWeatherAndForecast(cityName, 1)
                    } else {
                        // 获取历史天气
                        apiService.getHistoryWeather(cityName, date)
                    }
                    
                    val result = networkResult.fold(
                        onSuccess = { weatherResponse ->
                            val weatherData = convertResponseToWeatherData(weatherResponse, targetDate)
                            
                            // 验证转换后的数据完整性
                            if (isWeatherDataComplete(weatherData)) {
                                // 只有明确要求自动保存时才保存（避免误保存定位城市为用户城市）
                                if (autoSave) {
                                    Log.d("WeatherRepository", "自动保存天气数据到数据库：${weatherData.cityName}")
                                    weatherDao.insertWeatherData(weatherData)
                                } else {
                                    Log.d("WeatherRepository", "跳过自动保存，仅返回数据：${weatherData.cityName}")
                                }
                                
                                Result.success(weatherData)
                            } else {
                                Log.w("WeatherRepository", "转换后的数据不完整，尝试重试")
                                if (attempt == maxAttempts) {
                                    // 最后一次尝试失败，返回不完整的数据而不是失败
                                    Log.w("WeatherRepository", "多次尝试失败，返回不完整的数据")
                                    Result.success(weatherData)
                                } else {
                                    null // 返回null表示需要重试
                                }
                            }
                        },
                        onFailure = { exception ->
                            Log.e("WeatherRepository", "网络请求失败 (尝试 $attempt): ${exception.message}")
                            if (attempt == maxAttempts) {
                                // 最后一次尝试失败，返回本地缓存数据（如果有的话）
                                if (localData != null) {
                                    Log.d("WeatherRepository", "网络失败，返回本地缓存：${localData.cityName}")
                                    Result.success(localData)
                                } else {
                                    Result.failure(exception)
                                }
                            } else {
                                null // 返回null表示需要重试
                            }
                        }
                    )
                    
                    // 如果有结果（不是null），直接返回
                    if (result != null) {
                        return@withContext result
                    }
                    
                } catch (e: Exception) {
                    Log.e("WeatherRepository", "网络请求异常 (尝试 $attempt): ${e.message}")
                    if (attempt == maxAttempts) {
                        return@withContext if (localData != null) {
                            Log.d("WeatherRepository", "异常情况，返回本地缓存：${localData.cityName}")
                            Result.success(localData)
                        } else {
                            Result.failure(e)
                        }
                    }
                }
                
                // 重试前等待一段时间
                if (attempt < maxAttempts) {
                    Log.d("WeatherRepository", "等待 ${attempt * 1000}ms 后重试")
                    kotlinx.coroutines.delay(attempt * 1000L)
                }
            }
            
            // 理论上不会到达这里
            Result.failure(Exception("获取天气数据失败"))
        }
    }
    
    /**
     * 验证天气数据的完整性 - 增强版本
     */
    private fun isWeatherDataComplete(weatherData: WeatherData): Boolean {
        // 🔧 增强数据验证逻辑，确保温度数据的有效性
        
        // 1. 基本非空检查
        if (weatherData.cityName.isEmpty() || weatherData.weatherDescription.isEmpty()) {
            Log.w("WeatherRepository", "基本数据不完整：城市名或天气描述为空")
            return false
        }
        
        // 2. 温度合理性检查（-60°C到60°C是地球上合理的温度范围）
        val tempRange = -60..60
        if (weatherData.temperature !in tempRange) {
            Log.w("WeatherRepository", "主要温度超出合理范围：${weatherData.temperature}°C")
            return false
        }
        
        if (weatherData.temperatureMax !in tempRange) {
            Log.w("WeatherRepository", "最高温度超出合理范围：${weatherData.temperatureMax}°C")
            return false
        }
        
        if (weatherData.temperatureMin !in tempRange) {
            Log.w("WeatherRepository", "最低温度超出合理范围：${weatherData.temperatureMin}°C")
            return false
        }
        
        // 3. 温度逻辑检查（最高温度应该大于等于最低温度）
        if (weatherData.temperatureMax < weatherData.temperatureMin) {
            Log.w("WeatherRepository", "温度逻辑错误：最高温${weatherData.temperatureMax}°C < 最低温${weatherData.temperatureMin}°C")
            return false
        }
        
        // 4. 当前温度应该在最高最低温度范围内（允许适当超出）
        val tempRangeWithBuffer = (weatherData.temperatureMin - 5)..(weatherData.temperatureMax + 5)
        if (weatherData.temperature !in tempRangeWithBuffer) {
            Log.w("WeatherRepository", "当前温度${weatherData.temperature}°C超出预期范围[${weatherData.temperatureMin}, ${weatherData.temperatureMax}]°C")
            // 注意：这里只记录警告，不返回false，因为有时当前温度可能略超出预报范围
        }
        
        // 5. 湿度范围检查（0-100%）
        if (weatherData.humidity !in 0..100) {
            Log.w("WeatherRepository", "湿度超出合理范围：${weatherData.humidity}%")
            return false
        }
        
        Log.d("WeatherRepository", "数据完整性验证通过：${weatherData.cityName} 温度:${weatherData.temperature}°C")
        return true
    }
    
    /**
     * 获取多天的天气预报数据
     */
    suspend fun getWeatherForecast(cityName: String, days: Int = 7, autoSave: Boolean = false): Result<List<WeatherData>> {
        return withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "获取天气预报：$cityName, 自动保存：$autoSave")
            
            try {
                val networkResult = apiService.getCurrentWeatherAndForecast(cityName, days)
                
                networkResult.fold(
                    onSuccess = { weatherResponse ->
                        val weatherDataList = mutableListOf<WeatherData>()
                        
                        // 添加当前天气
                        val currentWeatherData = convertResponseToWeatherData(weatherResponse, dateFormat.format(Date()))
                        weatherDataList.add(currentWeatherData)
                        
                        // 添加预报天气
                        weatherResponse.forecast.forecastday.forEach { forecastDay ->
                            // 验证预报数据完整性
                            val avgTemp = if (forecastDay.day.avgtempC.isFinite()) forecastDay.day.avgtempC.toInt() else 20
                            val minTemp = if (forecastDay.day.mintempC.isFinite()) forecastDay.day.mintempC.toInt() else avgTemp - 5
                            val maxTemp = if (forecastDay.day.maxtempC.isFinite()) forecastDay.day.maxtempC.toInt() else avgTemp + 5
                            val humidity = if (forecastDay.day.avghumidity.isFinite()) forecastDay.day.avghumidity.toInt() else 60
                            val windSpeed = if (forecastDay.day.maxwindKph.isFinite()) forecastDay.day.maxwindKph else 10.0
                            val visibility = if (forecastDay.day.avgvisKm.isFinite()) forecastDay.day.avgvisKm.toInt() else 10
                            val uvIndex = if (forecastDay.day.uv.isFinite()) forecastDay.day.uv else 5.0
                            
                            Log.d("WeatherRepository", "预报数据[${forecastDay.date}] - 平均:${avgTemp}°, 最低:${minTemp}°, 最高:${maxTemp}°")
                            
                            val forecastWeatherData = WeatherData(
                                id = "${cityName}_${forecastDay.date}",
                                cityName = cityName,
                                date = forecastDay.date,
                                temperature = avgTemp,
                                temperatureMin = minTemp,
                                temperatureMax = maxTemp,
                                weather = forecastDay.day.condition.text.ifEmpty { "未知" },
                                weatherDescription = forecastDay.day.condition.text.ifEmpty { "未知天气状况" },
                                icon = forecastDay.day.condition.icon,
                                humidity = humidity.coerceIn(0, 100),
                                pressure = 1013, // 预报数据中没有提供气压，使用标准值
                                windSpeed = windSpeed,
                                windDegree = 0, // 预报数据中没有提供具体风向
                                visibility = visibility,
                                uvIndex = uvIndex,
                                feelsLike = avgTemp // 预报数据使用平均温度作为体感温度
                            )
                            weatherDataList.add(forecastWeatherData)
                        }
                        
                        // 只有明确要求时才保存到数据库（避免误保存）
                        if (autoSave) {
                            Log.d("WeatherRepository", "保存预报数据到数据库：$cityName")
                            weatherDao.insertWeatherDataList(weatherDataList)
                        } else {
                            Log.d("WeatherRepository", "跳过预报数据保存，仅返回数据：$cityName")
                        }
                        
                        Result.success(weatherDataList)
                    },
                    onFailure = { exception ->
                        // 从本地数据库获取
                        val localData = weatherDao.getWeatherDataByCity(cityName)
                        if (localData.isNotEmpty()) {
                            Log.d("WeatherRepository", "预报网络失败，返回本地缓存：$cityName")
                            Result.success(localData)
                        } else {
                            Result.failure(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                val localData = weatherDao.getWeatherDataByCity(cityName)
                if (localData.isNotEmpty()) {
                    Log.d("WeatherRepository", "预报异常，返回本地缓存：$cityName")
                    Result.success(localData)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * 搜索城市
     */
    suspend fun searchCities(query: String): Result<List<SearchResult>> {
        return apiService.searchCities(query)
    }
    
    /**
     * 获取所有缓存的城市
     */
    fun getAllCities(): LiveData<List<String>> {
        return weatherDao.getAllCitiesLiveData()
    }
    
    /**
     * 获取最近的天气数据
     */
    fun getRecentWeatherData(): LiveData<List<WeatherData>> {
        return weatherDao.getRecentWeatherDataLiveData()
    }
    
    /**
     * 删除城市的所有天气数据
     */
    suspend fun deleteWeatherDataByCity(cityName: String) {
        weatherDao.deleteWeatherDataByCity(cityName)
    }
    
    /**
     * 删除用户城市数据（保护定位城市不被删除）
     */
    suspend fun deleteUserCityData(cityName: String) {
        withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "=== Repository删除操作 ===")
            Log.d("WeatherRepository", "删除目标城市：$cityName")
            
            // 额外保护：检查是否有定位城市使用此城市名
            val locationCity = weatherDao.getLocationCity()
            if (locationCity != null && locationCity.cityName == cityName) {
                Log.w("WeatherRepository", "🚫 Repository保护：目标城市是定位城市，拒绝删除")
                return@withContext
            }
            
            Log.d("WeatherRepository", "✅ Repository检查通过，执行删除")
            weatherDao.deleteUserCityData(cityName)
            Log.d("WeatherRepository", "✅ Repository删除完成")
        }
    }
    
    /**
     * 清理过期数据（超过7天的数据）
     */
    suspend fun cleanOldData() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        weatherDao.deleteOldWeatherData(sevenDaysAgo)
    }
    
    /**
     * 将网络响应转换为WeatherData - 增强版本
     */
    private fun convertResponseToWeatherData(response: WeatherResponse, date: String): WeatherData {
        Log.d("WeatherRepository", "转换天气数据：${response.location.name}")
        
        // 验证API响应的完整性
        val current = response.current
        val forecast = response.forecast.forecastday.firstOrNull()
        
        // 🔧 增强温度数据处理逻辑，添加边界验证和异常值处理
        
        // 1. 提取原始温度数据并验证有效性
        val rawCurrentTemp = if (current.tempC.isFinite()) current.tempC else Double.NaN
        val rawMinTemp = forecast?.day?.mintempC ?: Double.NaN
        val rawMaxTemp = forecast?.day?.maxtempC ?: Double.NaN
        val rawFeelsLike = if (current.feelslikeC.isFinite()) current.feelslikeC else Double.NaN
        
        Log.d("WeatherRepository", "原始温度数据 - 当前:$rawCurrentTemp°, 最低:$rawMinTemp°, 最高:$rawMaxTemp°, 体感:$rawFeelsLike°")
        
        // 2. 温度合理性验证和智能回退处理
        fun validateAndFallbackTemperature(temp: Double, fallbackValue: Int, name: String): Int {
            return when {
                !temp.isFinite() -> {
                    Log.w("WeatherRepository", "$name 温度数据无效(${temp})，使用回退值：${fallbackValue}°C")
                    fallbackValue
                }
                temp < -60 || temp > 60 -> {
                    Log.w("WeatherRepository", "$name 温度超出合理范围(${temp}°C)，使用回退值：${fallbackValue}°C")
                    fallbackValue
                }
                else -> {
                    temp.toInt()
                }
            }
        }
        
        // 3. 智能温度回退策略
        // 先尝试获取一个基础温度值作为回退基准
        val baseTemp = when {
            rawCurrentTemp.isFinite() && rawCurrentTemp in -60.0..60.0 -> rawCurrentTemp.toInt()
            rawMaxTemp.isFinite() && rawMaxTemp in -60.0..60.0 -> rawMaxTemp.toInt()
            rawMinTemp.isFinite() && rawMinTemp in -60.0..60.0 -> rawMinTemp.toInt()
            else -> 20 // 最后的默认值
        }
        
        // 4. 按逻辑顺序处理各个温度值
        val currentTemp = validateAndFallbackTemperature(rawCurrentTemp, baseTemp, "当前")
        val maxTemp = validateAndFallbackTemperature(rawMaxTemp, currentTemp + 5, "最高")
        val minTemp = validateAndFallbackTemperature(rawMinTemp, currentTemp - 5, "最低")
        val feelsLike = validateAndFallbackTemperature(rawFeelsLike, currentTemp, "体感")
        
        // 5. 最终逻辑检查和修正
        val finalMaxTemp = if (maxTemp < minTemp) {
            Log.w("WeatherRepository", "温度逻辑错误：最高温($maxTemp) < 最低温($minTemp)，执行修正")
            maxOf(minTemp, currentTemp + 2)
        } else {
            maxTemp
        }
        
        val finalMinTemp = if (minTemp > finalMaxTemp) {
            Log.w("WeatherRepository", "温度逻辑错误：最低温($minTemp) > 最高温($finalMaxTemp)，执行修正")
            minOf(finalMaxTemp, currentTemp - 2)
        } else {
            minTemp
        }
        
        // 6. 确保当前温度在合理范围内
        val finalCurrentTemp = currentTemp.coerceIn(finalMinTemp - 3, finalMaxTemp + 3)
        
        Log.d("WeatherRepository", "最终温度数据 - 当前:${finalCurrentTemp}°, 最低:${finalMinTemp}°, 最高:${finalMaxTemp}°, 体感:${feelsLike}°")
        
        return WeatherData(
            id = "${response.location.name}_$date",
            cityName = response.location.name,
            date = date,
            temperature = finalCurrentTemp,
            temperatureMin = finalMinTemp,
            temperatureMax = finalMaxTemp,
            weather = current.condition.text.ifEmpty { "未知" },
            weatherDescription = current.condition.text.ifEmpty { "未知天气状况" },
            icon = current.condition.icon,
            humidity = current.humidity.coerceIn(0, 100),
            pressure = if (current.pressureMb.isFinite()) current.pressureMb.toInt() else 1013,
            windSpeed = if (current.windKph.isFinite()) current.windKph else 0.0,
            windDegree = current.windDegree.coerceIn(0, 360),
            visibility = if (current.visKm.isFinite()) current.visKm.toInt() else 10,
            uvIndex = if (current.uv.isFinite()) current.uv else 0.0,
            feelsLike = feelsLike
        )
    }
    
    /**
     * 检查数据是否过期（超过1小时）
     */
    private fun isDataExpired(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneHourInMillis = 60 * 60 * 1000
        return (currentTime - timestamp) > oneHourInMillis
    }
    
    /**
     * 获取所有保存的天气数据（用于城市列表显示，不包含定位城市）
     */
    suspend fun getAllSavedWeatherData(): List<WeatherData> {
        return withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "查询用户保存的城市数据（排除定位城市）")
            // 只获取用户添加的城市，不包含定位城市
            val result = weatherDao.getUserCitiesOnly()
            Log.d("WeatherRepository", "查询结果：${result.size}个用户城市，${result.map { "${it.cityName}(${it.id})" }}")
            result
        }
    }
    
    /**
     * 获取定位城市数据
     */
    suspend fun getLocationCityData(): WeatherData? {
        return withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "查询定位城市数据")
            val result = weatherDao.getLocationCity()
            Log.d("WeatherRepository", "定位城市：${result?.cityName ?: "无"}")
            result
        }
    }
    
    /**
     * 删除旧的定位城市记录，确保定位城市唯一
     */
    suspend fun deleteOldLocationCity() {
        return withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "删除旧的定位城市记录")
            weatherDao.deleteLocationCities()
            Log.d("WeatherRepository", "旧定位城市记录已删除")
        }
    }
    
    /**
     * 清理重复的定位城市数据（删除与定位城市同名的用户城市）
     */
    suspend fun cleanDuplicateLocationCities() {
        return withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "=== 开始清理重复定位城市数据 ===")
            
            // 获取当前定位城市
            val locationCity = weatherDao.getLocationCity()
            if (locationCity != null) {
                Log.d("WeatherRepository", "当前定位城市：${locationCity.cityName}")
                
                // 查询所有与定位城市同名的用户城市
                val duplicates = weatherDao.getWeatherDataByCity(locationCity.cityName)
                    .filter { !it.isLocationCity }
                
                Log.d("WeatherRepository", "发现${duplicates.size}个重复的用户城市记录：${duplicates.map { it.id }}")
                
                if (duplicates.isNotEmpty()) {
                    // 删除重复的用户城市记录
                    weatherDao.cleanDuplicateLocationCities()
                    Log.d("WeatherRepository", "✅ 已清理${duplicates.size}个重复记录")
                } else {
                    Log.d("WeatherRepository", "✅ 没有发现重复记录")
                }
            } else {
                Log.d("WeatherRepository", "没有定位城市，跳过清理")
            }
            
            Log.d("WeatherRepository", "=== 清理重复数据完成 ===")
        }
    }
    
    /**
     * 保存天气数据到数据库
     */
    suspend fun saveWeatherData(weatherData: WeatherData) {
        withContext(Dispatchers.IO) {
            weatherDao.insertWeatherData(weatherData)
        }
    }
    
    /**
     * 删除特定的天气数据
     */
    suspend fun deleteWeatherData(weatherData: WeatherData) {
        Log.d("WeatherRepository", "准备删除天气数据：${weatherData.cityName}, ID：${weatherData.id}")
        
        withContext(Dispatchers.IO) {
            Log.d("WeatherRepository", "调用Dao删除数据")
            weatherDao.deleteWeatherData(weatherData)
            Log.d("WeatherRepository", "Dao删除操作完成")
        }
    }
    
    /**
     * 获取24小时详细天气数据（用于数据可视化）
     */
    suspend fun getHourlyWeatherData(cityName: String, date: String? = null): Result<List<HourlyWeatherData>> {
        return withContext(Dispatchers.IO) {
            val targetDate = date ?: dateFormat.format(Date())
            Log.d("WeatherRepository", "获取小时级天气数据：$cityName, 日期：$targetDate")
            
            try {
                // 先检查本地数据（查询所有相关数据支持跨天）
                val localData = weatherDao.getAllHourlyWeatherDataByCity(cityName)
                
                // 如果本地有数据且不超过1小时，直接返回
                if (localData.isNotEmpty() && !isDataExpired(localData.first().timestamp)) {
                    Log.d("WeatherRepository", "返回本地小时级数据：${localData.size}条记录")
                    return@withContext Result.success(localData)
                }
                
                // 从网络获取2天天气数据（包含48小时数据，支持跨天24小时预报）
                val networkResult = apiService.getCurrentWeatherAndForecast(cityName, 2)
                
                networkResult.fold(
                    onSuccess = { weatherResponse ->
                        val hourlyDataList = mutableListOf<HourlyWeatherData>()
                        
                        // 转换所有天的小时级数据（支持跨天24小时预报）
                        weatherResponse.forecast.forecastday.forEach { forecastDay ->
                            forecastDay.hour.forEach { hour ->
                                val hourlyData = HourlyWeatherData(
                                    id = "${cityName}_${forecastDay.date}_${hour.time.substring(11, 13)}", // 使用具体日期
                                    cityName = cityName,
                                    date = forecastDay.date, // 使用实际的日期
                                    hour = hour.time.substring(11, 13).toInt(), // 提取小时数
                                    timeEpoch = hour.timeEpoch,
                                    temperature = if (hour.tempC.isFinite()) hour.tempC else 20.0,
                                    feelsLike = if (hour.feelslikeC.isFinite()) hour.feelslikeC else hour.tempC,
                                    humidity = hour.humidity.coerceIn(0, 100),
                                    pressure = if (hour.pressureMb.isFinite()) hour.pressureMb else 1013.0,
                                    windSpeed = if (hour.windKph.isFinite()) hour.windKph else 0.0,
                                    windDegree = hour.windDegree.coerceIn(0, 360),
                                    precipitationMm = if (hour.precipMm.isFinite()) hour.precipMm else 0.0,
                                    chanceOfRain = hour.chanceOfRain.coerceIn(0, 100),
                                    chanceOfSnow = hour.chanceOfSnow.coerceIn(0, 100),
                                    cloudCover = hour.cloud.coerceIn(0, 100),
                                    visibility = if (hour.visKm.isFinite()) hour.visKm else 10.0,
                                    uvIndex = if (hour.uv.isFinite()) hour.uv else 0.0,
                                    isDayTime = hour.isDay == 1,
                                    weatherDescription = hour.condition.text.ifEmpty { "未知" },
                                    weatherIcon = hour.condition.icon
                                )
                                hourlyDataList.add(hourlyData)
                            }
                        }
                        
                        // 保存到数据库
                        if (hourlyDataList.isNotEmpty()) {
                            // 按日期分组删除旧数据，再插入新数据
                            val dateGroups = hourlyDataList.groupBy { it.date }
                            dateGroups.keys.forEach { date ->
                                weatherDao.deleteHourlyWeatherDataByCity(cityName, date)
                            }
                            weatherDao.insertHourlyWeatherDataList(hourlyDataList)
                            Log.d("WeatherRepository", "保存小时级数据：${hourlyDataList.size}条记录，涵盖${dateGroups.size}天")
                        }
                        
                        Result.success(hourlyDataList)
                    },
                    onFailure = { exception ->
                        // 网络失败，返回本地缓存（如果有的话）
                        val localData = weatherDao.getAllHourlyWeatherDataByCity(cityName)
                        if (localData.isNotEmpty()) {
                            Log.d("WeatherRepository", "网络失败，返回本地小时级缓存：${localData.size}条记录")
                            Result.success(localData)
                        } else {
                            Result.failure(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("WeatherRepository", "获取小时级数据异常", e)
                // 异常时也尝试返回本地缓存
                val localData = weatherDao.getAllHourlyWeatherDataByCity(cityName)
                if (localData.isNotEmpty()) {
                    Result.success(localData)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * 获取天文数据（日出日落、月相等）
     */
    suspend fun getAstroData(cityName: String, date: String? = null): Result<AstroData> {
        return withContext(Dispatchers.IO) {
            val targetDate = date ?: dateFormat.format(Date())
            Log.d("WeatherRepository", "获取天文数据：$cityName, 日期：$targetDate")
            
            try {
                // 先检查本地数据
                val localData = weatherDao.getAstroData(cityName, targetDate)
                
                // 如果本地有数据且不超过24小时，直接返回（天文数据变化较慢）
                if (localData != null && !isDataExpiredLong(localData.timestamp)) {
                    Log.d("WeatherRepository", "返回本地天文数据：${localData.cityName}")
                    return@withContext Result.success(localData)
                }
                
                // 从网络获取天文数据
                val networkResult = apiService.getCurrentWeatherAndForecast(cityName, 1)
                
                networkResult.fold(
                    onSuccess = { weatherResponse ->
                        val astroInfo = weatherResponse.forecast.forecastday.firstOrNull()?.astro
                        
                        if (astroInfo != null) {
                            val astroData = AstroData(
                                id = "${cityName}_${targetDate}",
                                cityName = cityName,
                                date = targetDate,
                                sunrise = astroInfo.sunrise,
                                sunset = astroInfo.sunset,
                                moonrise = astroInfo.moonrise,
                                moonset = astroInfo.moonset,
                                moonPhase = astroInfo.moonPhase,
                                moonIllumination = astroInfo.moonIllumination
                            )
                            
                            // 保存到数据库
                            weatherDao.insertAstroData(astroData)
                            Log.d("WeatherRepository", "保存天文数据：${astroData.cityName}")
                            
                            Result.success(astroData)
                        } else {
                            Result.failure(Exception("天文数据为空"))
                        }
                    },
                    onFailure = { exception ->
                        // 网络失败，返回本地缓存（如果有的话）
                        val localData = weatherDao.getAstroData(cityName, targetDate)
                        if (localData != null) {
                            Log.d("WeatherRepository", "网络失败，返回本地天文缓存：${localData.cityName}")
                            Result.success(localData)
                        } else {
                            Result.failure(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("WeatherRepository", "获取天文数据异常", e)
                // 异常时也尝试返回本地缓存
                val localData = weatherDao.getAstroData(cityName, targetDate)
                if (localData != null) {
                    Result.success(localData)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
    
    /**
     * 检查数据是否过期（超过24小时，用于天文数据）
     */
    private fun isDataExpiredLong(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000
        return (currentTime - timestamp) > oneDayInMillis
    }
} 