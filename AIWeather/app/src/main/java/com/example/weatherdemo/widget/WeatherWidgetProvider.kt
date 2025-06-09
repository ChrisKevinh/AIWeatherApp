package com.example.weatherdemo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.weatherdemo.CityDetailActivity
import com.example.weatherdemo.R
import com.example.weatherdemo.WeatherApplication
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 天气小部件提供器
 * 显示当前定位城市的天气信息，点击进入详情页面
 */
class WeatherWidgetProvider : AppWidgetProvider() {
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    companion object {
        const val ACTION_WIDGET_UPDATE = "com.example.weatherdemo.widget.UPDATE"
        const val ACTION_WIDGET_CLICK = "com.example.weatherdemo.widget.CLICK"
        
        /**
         * 手动更新所有小部件
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("WeatherWidget", "onUpdate: 更新${appWidgetIds.size}个小部件")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_UPDATE -> {
                Log.d("WeatherWidget", "收到手动更新请求")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, widgetIds)
            }
            ACTION_WIDGET_CLICK -> {
                Log.d("WeatherWidget", "小部件被点击")
                openWeatherDetail(context)
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("WeatherWidget", "小部件已启用")
        // 启动后台更新服务
        WeatherWidgetUpdateService.startPeriodicUpdate(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("WeatherWidget", "所有小部件已移除")
        // 停止后台更新服务
        WeatherWidgetUpdateService.stopPeriodicUpdate(context)
    }
    
    /**
     * 更新单个小部件
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d("WeatherWidget", "更新小部件 ID: $appWidgetId")
        
        // 先显示加载状态
        val loadingViews = createLoadingViews(context, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, loadingViews)
        
        // 异步获取天气数据
        scope.launch {
            try {
                val weatherData = getLocationWeatherData(context)
                val views = if (weatherData != null) {
                    createWeatherViews(context, appWidgetId, weatherData)
                } else {
                    createErrorViews(context, appWidgetId)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                
            } catch (e: Exception) {
                Log.e("WeatherWidget", "更新小部件失败", e)
                val errorViews = createErrorViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, errorViews)
            }
        }
    }
    
    /**
     * 获取定位城市天气数据
     */
    private suspend fun getLocationWeatherData(context: Context): WeatherData? {
        return try {
            val application = context.applicationContext as WeatherApplication
            val repository = application.repository
            
            // 先从数据库获取定位城市数据
            var locationWeather = repository.getLocationCityData()
            
            // 如果没有数据或数据过期，尝试更新
            if (locationWeather == null || isDataExpired(locationWeather.timestamp)) {
                Log.d("WeatherWidget", "定位城市数据过期或不存在，尝试更新")
                
                // 尝试获取当前位置
                val locationManager = com.example.weatherdemo.LocationManager(context)
                val locationResult = locationManager.getCurrentLocation()
                
                locationResult.fold(
                    onSuccess = { cityName ->
                        Log.d("WeatherWidget", "获取到定位城市: $cityName")
                        val weatherResult = repository.getWeatherData(cityName, autoSave = false)
                        weatherResult.fold(
                            onSuccess = { weatherData ->
                                // 保存为定位城市
                                val locationWeatherData = weatherData.copy(
                                    id = "location_city_${weatherData.cityName}",
                                    isLocationCity = true
                                )
                                repository.saveWeatherData(locationWeatherData)
                                locationWeather = locationWeatherData
                            },
                            onFailure = { exception ->
                                Log.e("WeatherWidget", "获取天气数据失败", exception)
                            }
                        )
                    },
                    onFailure = { exception ->
                        Log.e("WeatherWidget", "获取位置失败", exception)
                    }
                )
            }
            
            locationWeather
        } catch (e: Exception) {
            Log.e("WeatherWidget", "获取定位城市天气数据异常", e)
            null
        }
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
     * 根据天气描述获取对应的图标资源
     */
    private fun getWeatherIcon(weatherDescription: String): Int {
        return when {
            weatherDescription.contains("晴", ignoreCase = true) ||
            weatherDescription.contains("Clear", ignoreCase = true) ||
            weatherDescription.contains("Sunny", ignoreCase = true) -> R.drawable.ic_weather_sunny
            
            weatherDescription.contains("雨", ignoreCase = true) ||
            weatherDescription.contains("Rain", ignoreCase = true) ||
            weatherDescription.contains("Drizzle", ignoreCase = true) ||
            weatherDescription.contains("Shower", ignoreCase = true) -> R.drawable.ic_weather_rainy
            
            weatherDescription.contains("雪", ignoreCase = true) ||
            weatherDescription.contains("Snow", ignoreCase = true) -> R.drawable.ic_weather_snowy
            
            weatherDescription.contains("雾", ignoreCase = true) ||
            weatherDescription.contains("霾", ignoreCase = true) ||
            weatherDescription.contains("Fog", ignoreCase = true) ||
            weatherDescription.contains("Mist", ignoreCase = true) ||
            weatherDescription.contains("Haze", ignoreCase = true) -> R.drawable.ic_weather_fog
            
            weatherDescription.contains("云", ignoreCase = true) ||
            weatherDescription.contains("Cloud", ignoreCase = true) ||
            weatherDescription.contains("Overcast", ignoreCase = true) -> R.drawable.ic_weather_cloudy
            
            else -> R.drawable.ic_weather_cloudy // 默认多云图标
        }
    }
    
    /**
     * 创建加载状态的视图
     */
    private fun createLoadingViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        
        views.setTextViewText(R.id.widget_city_name, "定位中...")
        views.setTextViewText(R.id.widget_temperature, "--°")
        views.setTextViewText(R.id.widget_weather_desc, "获取天气中...")
        views.setTextViewText(R.id.widget_temp_range, "最高--° 最低--°")
        views.setTextViewText(R.id.widget_humidity, "湿度 --%")
        views.setTextViewText(R.id.widget_feels_like, "体感 --°")
        views.setTextViewText(R.id.widget_update_time, "--:--")
        views.setImageViewResource(R.id.widget_weather_icon, R.drawable.ic_weather_loading)
        
        // 设置点击事件
        setClickListener(context, views, appWidgetId)
        
        return views
    }
    
    /**
     * 创建天气数据视图
     */
    private fun createWeatherViews(
        context: Context,
        appWidgetId: Int,
        weatherData: WeatherData
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        val settingsManager = SettingsManager.getInstance(context)
        
        // 设置天气数据 - 使用SettingsManager格式化温度
        views.setTextViewText(R.id.widget_city_name, weatherData.cityName)
        views.setTextViewText(R.id.widget_temperature, settingsManager.formatTemperatureValue(weatherData.temperature))
        views.setTextViewText(R.id.widget_weather_desc, weatherData.weatherDescription)
        views.setTextViewText(
            R.id.widget_temp_range,
            "最高${settingsManager.formatTemperatureValue(weatherData.temperatureMax)} 最低${settingsManager.formatTemperatureValue(weatherData.temperatureMin)}"
        )
        views.setTextViewText(R.id.widget_humidity, "湿度 ${weatherData.humidity}%")
        
        // 计算体感温度 (简单估算：湿度影响体感) - 使用SettingsManager格式化
        val feelsLike = when {
            weatherData.humidity > 80 -> weatherData.temperature + 2
            weatherData.humidity > 60 -> weatherData.temperature + 1
            weatherData.humidity < 30 -> weatherData.temperature - 1
            else -> weatherData.temperature
        }
        views.setTextViewText(R.id.widget_feels_like, "体感 ${settingsManager.formatTemperatureValue(feelsLike)}")
        
        // 设置天气图标
        val iconResource = getWeatherIcon(weatherData.weatherDescription)
        views.setImageViewResource(R.id.widget_weather_icon, iconResource)
        
        // 设置更新时间
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        views.setTextViewText(R.id.widget_update_time, timeFormat.format(Date()))
        
        // 设置点击事件
        setClickListener(context, views, appWidgetId, weatherData.cityName)
        
        Log.d("WeatherWidget", "小部件数据更新完成: ${weatherData.cityName}, ${settingsManager.formatTemperatureValue(weatherData.temperature)}, 图标: $iconResource")
        
        return views
    }
    
    /**
     * 创建错误状态视图
     */
    private fun createErrorViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        
        views.setTextViewText(R.id.widget_city_name, "无法获取位置")
        views.setTextViewText(R.id.widget_temperature, "--°")
        views.setTextViewText(R.id.widget_weather_desc, "天气数据获取失败")
        views.setTextViewText(R.id.widget_temp_range, "最高--° 最低--°")
        views.setTextViewText(R.id.widget_humidity, "湿度 --%")
        views.setTextViewText(R.id.widget_feels_like, "体感 --°")
        views.setImageViewResource(R.id.widget_weather_icon, R.drawable.ic_weather_loading)
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        views.setTextViewText(R.id.widget_update_time, timeFormat.format(Date()))
        
        // 设置点击事件（点击打开应用）
        setClickListener(context, views, appWidgetId)
        
        return views
    }
    
    /**
     * 设置小部件点击事件
     */
    private fun setClickListener(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        cityName: String? = null
    ) {
        val intent = if (cityName != null) {
            // 有城市数据，直接进入详情页
            Intent(context, CityDetailActivity::class.java).apply {
                putExtra(CityDetailActivity.EXTRA_CITY_NAME, cityName)
                putExtra(CityDetailActivity.EXTRA_FROM_SEARCH, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            // 没有数据，打开主页面
            Intent(context, com.example.weatherdemo.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_weather, pendingIntent)
    }
    
    /**
     * 打开天气详情页面
     */
    private fun openWeatherDetail(context: Context) {
        scope.launch {
            try {
                val application = context.applicationContext as WeatherApplication
                val repository = application.repository
                val locationWeather = repository.getLocationCityData()
                
                val intent = if (locationWeather != null) {
                    Intent(context, CityDetailActivity::class.java).apply {
                        putExtra(CityDetailActivity.EXTRA_CITY_NAME, locationWeather.cityName)
                        putExtra(CityDetailActivity.EXTRA_FROM_SEARCH, false)
                    }
                } else {
                    Intent(context, com.example.weatherdemo.MainActivity::class.java)
                }
                
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(intent)
                
            } catch (e: Exception) {
                Log.e("WeatherWidget", "打开详情页面失败", e)
                // 如果失败，打开主页面
                val intent = Intent(context, com.example.weatherdemo.MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(intent)
            }
        }
    }
} 