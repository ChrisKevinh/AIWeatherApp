package com.example.weatherdemo.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.data.AstroData
import com.example.weatherdemo.network.SearchResult
import com.example.weatherdemo.repository.WeatherRepository
import kotlinx.coroutines.launch
import android.util.Log

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    
    private val _currentWeatherData = MutableLiveData<WeatherData?>()
    val currentWeatherData: LiveData<WeatherData?> = _currentWeatherData
    
    private val _forecastWeatherData = MutableLiveData<List<WeatherData>>()
    val forecastWeatherData: LiveData<List<WeatherData>> = _forecastWeatherData
    
    private val _searchResults = MutableLiveData<List<SearchResult>>()
    val searchResults: LiveData<List<SearchResult>> = _searchResults
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _selectedCity = MutableLiveData<String>()
    val selectedCity: LiveData<String> = _selectedCity
    
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing
    
    // 首页刷新状态（区别于详情页的刷新状态）
    private val _isMainRefreshing = MutableLiveData<Boolean>()
    val isMainRefreshing: LiveData<Boolean> = _isMainRefreshing
    
    // 保存的城市列表
    private val _savedCities = MutableLiveData<List<WeatherData>>()
    val savedCities: LiveData<List<WeatherData>> = _savedCities
    
    // 定位城市数据
    private val _locationCity = MutableLiveData<WeatherData?>()
    val locationCity: LiveData<WeatherData?> = _locationCity
    
    // 小时级天气数据（用于数据可视化）
    private val _hourlyWeatherData = MutableLiveData<List<HourlyWeatherData>>()
    val hourlyWeatherData: LiveData<List<HourlyWeatherData>> = _hourlyWeatherData
    
    // 天文数据
    private val _astroData = MutableLiveData<AstroData?>()
    val astroData: LiveData<AstroData?> = _astroData
    
    // 获取所有缓存的城市
    val allCities: LiveData<List<String>> = repository.getAllCities()
    
    // 获取最近的天气数据
    val recentWeatherData: LiveData<List<WeatherData>> = repository.getRecentWeatherData()
    
    init {
        // 清理过期数据和重复的定位城市数据
        cleanOldData()
        cleanDuplicateLocationCities()
    }
    
    /**
     * 加载天气数据（用于详情页查看，不自动保存）
     */
    fun loadWeatherData(cityName: String, date: String? = null) {
        _isLoading.value = true
        _errorMessage.value = null
        _selectedCity.value = cityName
        
        // 立即清空之前的数据，避免显示错误信息
        _currentWeatherData.value = null
        _forecastWeatherData.value = emptyList()
        _hourlyWeatherData.value = emptyList()
        
        Log.d("WeatherViewModel", "加载天气数据用于查看：$cityName（不自动保存）")
        
        viewModelScope.launch {
            try {
                // 不自动保存，避免误创建用户城市记录
                val result = repository.getWeatherData(cityName, date, autoSave = false)
                result.fold(
                    onSuccess = { weatherData ->
                        _currentWeatherData.value = weatherData
                        // 同时加载预报数据
                        loadForecastData(cityName)
                        Log.d("WeatherViewModel", "天气数据加载成功：${weatherData.cityName}")
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "获取天气数据失败"
                        _currentWeatherData.value = null
                        Log.e("WeatherViewModel", "天气数据加载失败：${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "获取天气数据失败"
                _currentWeatherData.value = null
                Log.e("WeatherViewModel", "天气数据加载异常", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 加载天气预报数据
     */
    fun loadForecastData(cityName: String) {
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "加载预报数据：$cityName（不自动保存）")
                // 不自动保存，避免创建重复记录
                val result = repository.getWeatherForecast(cityName, 10, autoSave = false)
                result.fold(
                    onSuccess = { forecastList ->
                        _forecastWeatherData.value = forecastList
                        Log.d("WeatherViewModel", "预报数据加载成功，${forecastList.size}条记录")
                    },
                    onFailure = { exception ->
                        // 预报数据加载失败不显示错误消息，因为主要天气数据可能已经成功
                        _forecastWeatherData.value = emptyList()
                        Log.e("WeatherViewModel", "预报数据加载失败：${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _forecastWeatherData.value = emptyList()
                Log.e("WeatherViewModel", "预报数据加载异常", e)
            }
        }
    }
    
    /**
     * 加载小时级天气数据（用于数据可视化）
     */
    fun loadHourlyWeatherData(cityName: String, date: String? = null) {
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "加载小时级数据：$cityName")
                val result = repository.getHourlyWeatherData(cityName, date)
                result.fold(
                    onSuccess = { hourlyList ->
                        _hourlyWeatherData.value = hourlyList
                        Log.d("WeatherViewModel", "小时级数据加载成功，${hourlyList.size}条记录")
                    },
                    onFailure = { exception ->
                        _hourlyWeatherData.value = emptyList()
                        Log.e("WeatherViewModel", "小时级数据加载失败：${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _hourlyWeatherData.value = emptyList()
                Log.e("WeatherViewModel", "小时级数据加载异常", e)
            }
        }
    }
    
    /**
     * 加载天文数据
     */
    fun loadAstroData(cityName: String, date: String? = null) {
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "加载天文数据：$cityName")
                val result = repository.getAstroData(cityName, date)
                result.fold(
                    onSuccess = { astro ->
                        _astroData.value = astro
                        Log.d("WeatherViewModel", "天文数据加载成功：${astro.cityName}")
                    },
                    onFailure = { exception ->
                        _astroData.value = null
                        Log.e("WeatherViewModel", "天文数据加载失败：${exception.message}")
                    }
                )
            } catch (e: Exception) {
                _astroData.value = null
                Log.e("WeatherViewModel", "天文数据加载异常", e)
            }
        }
    }
    
    /**
     * 刷新当前城市的天气数据
     */
    fun refreshWeatherData() {
        val currentCity = _selectedCity.value
        if (currentCity != null) {
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    // 刷新时也不自动保存，避免创建重复记录
                    val result = repository.getWeatherData(currentCity, autoSave = false)
                    result.fold(
                        onSuccess = { weatherData ->
                            _currentWeatherData.value = weatherData
                            loadForecastData(currentCity)
                        },
                        onFailure = { exception ->
                            _errorMessage.value = exception.message ?: "刷新天气数据失败"
                        }
                    )
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "刷新天气数据失败"
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }
    
    /**
     * 搜索城市
     */
    fun searchCities(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            try {
                val result = repository.searchCities(query)
                result.fold(
                    onSuccess = { cities ->
                        _searchResults.value = cities
                    },
                    onFailure = { exception ->
                        _searchResults.value = emptyList()
                        _errorMessage.value = "搜索城市失败: ${exception.message}"
                    }
                )
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _errorMessage.value = "搜索城市失败: ${e.message}"
            }
        }
    }
    
    /**
     * 加载定位城市的天气数据
     */
    fun loadLocationWeatherData(cityName: String) {
        Log.d("WeatherViewModel", "加载定位城市天气数据：$cityName")
        
        viewModelScope.launch {
            try {
                val result = repository.getWeatherData(cityName)
                result.fold(
                    onSuccess = { weatherData ->
                        // 标记为定位城市，使用固定的唯一ID
                        val locationWeatherData = weatherData.copy(
                            id = "location_city_${weatherData.cityName}",  // 固定ID格式，避免重复
                            isLocationCity = true
                        )
                        _locationCity.value = locationWeatherData
                        
                        // 保存到数据库前，先删除旧的定位城市记录（如果有的话）
                        repository.deleteOldLocationCity()
                        repository.saveWeatherData(locationWeatherData)
                        
                        Log.d("WeatherViewModel", "定位城市天气数据加载成功：${locationWeatherData.cityName}, ID: ${locationWeatherData.id}")
                    },
                    onFailure = { exception ->
                        Log.e("WeatherViewModel", "加载定位城市天气数据失败：${exception.message}")
                        _errorMessage.value = "无法获取当前位置天气：${exception.message}"
                    }
                )
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "加载定位城市天气数据异常", e)
                _errorMessage.value = "获取位置天气失败：${e.message}"
            }
        }
    }
    
    /**
     * 从数据库加载已保存的定位城市数据
     */
    fun loadLocationCityFromDatabase() {
        Log.d("WeatherViewModel", "从数据库加载定位城市数据")
        
        viewModelScope.launch {
            try {
                val locationCity = repository.getLocationCityData()
                locationCity?.let { 
                    _locationCity.value = it
                    Log.d("WeatherViewModel", "从数据库加载定位城市成功：${it.cityName}")
                } ?: Log.d("WeatherViewModel", "数据库中没有找到定位城市")
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "从数据库加载定位城市失败", e)
            }
        }
    }
    
    /**
     * 删除特定的天气数据
     */
    fun deleteWeatherData(weatherData: WeatherData) {
        Log.d("WeatherViewModel", "=== 开始删除操作 ===")
        Log.d("WeatherViewModel", "删除目标：${weatherData.cityName}")
        Log.d("WeatherViewModel", "删除目标ID：${weatherData.id}")
        Log.d("WeatherViewModel", "删除目标isLocationCity：${weatherData.isLocationCity}")
        
        // 第一层保护：检查传入的对象
        if (weatherData.isLocationCity) {
            Log.w("WeatherViewModel", "🚫 第一层保护：尝试删除定位城市，操作被阻止")
            _errorMessage.value = "定位城市无法删除"
            return
        }
        
        // 第二层保护：检查当前定位城市
        val currentLocationCity = _locationCity.value
        if (currentLocationCity != null && currentLocationCity.cityName == weatherData.cityName) {
            Log.w("WeatherViewModel", "🚫 第二层保护：目标城市是当前定位城市，操作被阻止")
            _errorMessage.value = "定位城市无法删除"
            return
        }
        
        Log.d("WeatherViewModel", "✅ 通过所有保护检查，执行删除操作")
        
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "调用Repository删除用户城市：${weatherData.cityName}")
                // 只删除用户城市（非定位城市）的所有天气数据
                repository.deleteUserCityData(weatherData.cityName)
                
                Log.d("WeatherViewModel", "数据库删除完成，重新加载城市列表")
                // 重新加载城市列表（不影响定位城市）
                loadSavedCities()
                
                Log.d("WeatherViewModel", "=== 删除操作完成 ===")
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "删除天气数据失败", e)
                _errorMessage.value = "删除天气数据失败: ${e.message}"
            }
        }
    }
    
    /**
     * 删除城市的天气数据
     */
    fun deleteWeatherDataByCity(cityName: String) {
        viewModelScope.launch {
            try {
                repository.deleteWeatherDataByCity(cityName)
            } catch (e: Exception) {
                _errorMessage.value = "删除数据失败: ${e.message}"
            }
        }
    }
    
    /**
     * 清理过期数据
     */
    private fun cleanOldData() {
        viewModelScope.launch {
            try {
                repository.cleanOldData()
            } catch (e: Exception) {
                // 清理失败不需要显示错误消息
            }
        }
    }
    
    /**
     * 清理重复的定位城市数据
     */
    private fun cleanDuplicateLocationCities() {
        viewModelScope.launch {
            try {
                Log.d("WeatherViewModel", "开始清理重复的定位城市数据")
                repository.cleanDuplicateLocationCities()
                Log.d("WeatherViewModel", "重复定位城市数据清理完成")
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "清理重复数据失败", e)
                // 清理失败不需要显示错误消息
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * 清除搜索结果
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
    
    /**
     * 加载保存的城市列表
     */
    fun loadSavedCities() {
        Log.d("WeatherViewModel", "开始加载保存的城市列表")
        
        viewModelScope.launch {
            try {
                // 先清理重复数据
                repository.cleanDuplicateLocationCities()
                
                val cities = repository.getAllSavedWeatherData()
                Log.d("WeatherViewModel", "从数据库加载到${cities.size}个用户城市：${cities.map { it.cityName }}")
                _savedCities.value = cities
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "加载城市列表失败", e)
                _errorMessage.value = "加载城市列表失败: ${e.message}"
                _savedCities.value = emptyList()
            }
        }
    }
    
    /**
     * 保存天气数据到数据库
     */
    fun saveWeatherData(weatherData: WeatherData) {
        Log.d("WeatherViewModel", "保存天气数据：${weatherData.cityName}, isLocationCity=${weatherData.isLocationCity}")
        
        viewModelScope.launch {
            try {
                // 确保用户添加的城市不被标记为定位城市
                val userWeatherData = weatherData.copy(isLocationCity = false)
                repository.saveWeatherData(userWeatherData)
                
                Log.d("WeatherViewModel", "保存成功，重新加载用户城市列表（不影响定位城市）")
                // 只重新加载用户城市列表，不要重新加载定位城市
                loadSavedCities()
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "保存天气数据失败", e)
                _errorMessage.value = "保存天气数据失败: ${e.message}"
            }
        }
    }
    
    /**
     * 刷新首页所有城市的天气数据
     */
    fun refreshAllCitiesData() {
        Log.d("WeatherViewModel", "开始刷新首页所有城市数据")
        _isMainRefreshing.value = true
        
        viewModelScope.launch {
            try {
                // 刷新定位城市
                val locationCity = _locationCity.value
                if (locationCity != null) {
                    Log.d("WeatherViewModel", "刷新定位城市：${locationCity.cityName}")
                    val result = repository.getWeatherData(locationCity.cityName, autoSave = false)
                    result.fold(
                        onSuccess = { weatherData ->
                            // 验证新数据是否有效
                            if (isWeatherDataValid(weatherData)) {
                                val updatedLocationCity = weatherData.copy(
                                    id = "location_city_${weatherData.cityName}",
                                    isLocationCity = true
                                )
                                _locationCity.value = updatedLocationCity
                                // 更新数据库中的定位城市
                                repository.saveWeatherData(updatedLocationCity)
                                Log.d("WeatherViewModel", "定位城市刷新成功，温度：${weatherData.temperature}°")
                            } else {
                                Log.w("WeatherViewModel", "定位城市新数据无效，保留原数据")
                            }
                        },
                        onFailure = { exception ->
                            Log.e("WeatherViewModel", "定位城市刷新失败：${exception.message}")
                        }
                    )
                }
                
                // 刷新用户城市列表
                Log.d("WeatherViewModel", "重新加载用户城市列表")
                val cities = repository.getAllSavedWeatherData()
                
                // 为每个用户城市获取最新数据
                val refreshedCities = mutableListOf<WeatherData>()
                cities.forEach { city ->
                    try {
                        Log.d("WeatherViewModel", "刷新用户城市：${city.cityName}，当前温度：${city.temperature}°")
                        val result = repository.getWeatherData(city.cityName, autoSave = false)
                        result.fold(
                            onSuccess = { weatherData ->
                                // 验证新数据是否有效
                                if (isWeatherDataValid(weatherData)) {
                                    // 保持用户城市的标识不变
                                    val updatedWeatherData = weatherData.copy(
                                        id = city.id,
                                        isLocationCity = false
                                    )
                                    refreshedCities.add(updatedWeatherData)
                                    // 手动保存到数据库
                                    repository.saveWeatherData(updatedWeatherData)
                                    Log.d("WeatherViewModel", "用户城市${city.cityName}刷新成功，新温度：${weatherData.temperature}°")
                                } else {
                                    // 新数据无效，保留原数据
                                    refreshedCities.add(city)
                                    Log.w("WeatherViewModel", "用户城市${city.cityName}新数据无效（温度：${weatherData.temperature}°），保留原数据")
                                }
                            },
                            onFailure = { exception ->
                                // 如果刷新失败，保留原数据
                                refreshedCities.add(city)
                                Log.w("WeatherViewModel", "用户城市${city.cityName}刷新失败：${exception.message}，保留原数据")
                            }
                        )
                    } catch (e: Exception) {
                        refreshedCities.add(city)
                        Log.w("WeatherViewModel", "用户城市${city.cityName}刷新异常：${e.message}，保留原数据")
                    }
                }
                
                _savedCities.value = refreshedCities
                Log.d("WeatherViewModel", "所有城市刷新完成，共${refreshedCities.size}个用户城市")
                
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "刷新所有城市失败", e)
                _errorMessage.value = "刷新天气数据失败: ${e.message}"
            } finally {
                _isMainRefreshing.value = false
            }
        }
    }
    
    /**
     * 验证天气数据是否有效
     */
    private fun isWeatherDataValid(weatherData: WeatherData): Boolean {
        return try {
            // 检查关键字段是否有效
            val isValid = weatherData.temperature >= -273 && // 绝对零度以上（包含0度）
                         weatherData.temperature <= 100 && // 合理温度范围
                         weatherData.temperatureMax >= -273 &&
                         weatherData.temperatureMin >= -273 &&
                         weatherData.humidity >= 0 &&
                         weatherData.humidity <= 100 &&
                         weatherData.cityName.isNotBlank() &&
                         weatherData.weatherDescription.isNotBlank()
            
            if (!isValid) {
                Log.w("WeatherViewModel", "数据验证失败 - 城市：${weatherData.cityName}, 温度：${weatherData.temperature}°, 湿度：${weatherData.humidity}%")
            } else {
                Log.d("WeatherViewModel", "数据验证通过 - 城市：${weatherData.cityName}, 温度：${weatherData.temperature}°")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "数据验证异常", e)
            false
        }
    }
}

class WeatherViewModelFactory(private val repository: WeatherRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 