package com.example.weatherdemo.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.weatherdemo.R
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.utils.SettingsManager
import java.util.*

class HourlyWeatherAdapter(private val context: Context) : RecyclerView.Adapter<HourlyWeatherAdapter.HourlyWeatherViewHolder>() {
    
    private var hourlyData: List<HourlyWeatherData> = emptyList()
    private val settingsManager = SettingsManager.getInstance(context)
    
    class HourlyWeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val weatherIcon: ImageView = itemView.findViewById(R.id.weatherIcon)
        val rainProbabilityText: TextView = itemView.findViewById(R.id.rainProbabilityText)
        val temperatureText: TextView = itemView.findViewById(R.id.temperatureText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyWeatherViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_hourly_weather, parent, false)
        return HourlyWeatherViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: HourlyWeatherViewHolder, position: Int) {
        val data = hourlyData[position]
        
        // 设置时间
        holder.timeText.text = formatHourTime(data.hour, position)
        
        // 设置温度（根据设置显示摄氏度或华氏度）
        val temperature = if (settingsManager.getTemperatureUnit() == "celsius") {
            "${data.temperature.toInt()}°"
        } else {
            // 将摄氏度转换为华氏度
            val fahrenheit = data.temperature * 9 / 5 + 32
            "${fahrenheit.toInt()}°"
        }
        holder.temperatureText.text = temperature
        
        // 设置降雨概率
        holder.rainProbabilityText.text = "${data.chanceOfRain}%"
        
        // 使用WeatherAPI的在线图标
        val iconUrl = if (data.weatherIcon.isNotEmpty()) {
            // WeatherAPI图标URL（添加https协议）
            "https:${data.weatherIcon}"
        } else {
            null
        }
        
        if (iconUrl != null) {
            // 使用Glide加载WeatherAPI的在线图标
            Glide.with(context)
                .load(iconUrl)
                .placeholder(R.drawable.ic_weather_loading) // 加载中显示的图标
                .error(getBackupIcon(data)) // 加载失败时的备用图标
                .override(144, 144) // 设置更高的分辨率
                .into(holder.weatherIcon)
        } else {
            // 没有API图标时使用本地备用图标
            holder.weatherIcon.setImageResource(getBackupIcon(data))
        }
    }
    
    override fun getItemCount(): Int = hourlyData.size
    
    fun updateHourlyData(newHourlyData: List<HourlyWeatherData>) {
        hourlyData = newHourlyData
        notifyDataSetChanged()
    }
    
    /**
     * 获取备用图标资源（当API图标加载失败或不可用时使用）
     */
    private fun getBackupIcon(data: HourlyWeatherData): Int {
        return when {
            // 高降雨概率 - 使用雨天图标
            data.chanceOfRain >= 70 -> R.drawable.ic_weather_rainy_new
            // 中等降雨概率 - 使用多云图标
            data.chanceOfRain >= 30 -> R.drawable.ic_weather_cloudy_new
            // 高云量但低降雨概率 - 使用多云图标
            data.cloudCover > 70 -> R.drawable.ic_weather_cloudy_new
            // 白天晴朗 - 使用太阳图标
            data.isDayTime && data.cloudCover <= 30 -> R.drawable.ic_weather_sunny
            // 白天多云 - 使用多云图标
            data.isDayTime -> R.drawable.ic_weather_cloudy_new
            // 夜晚 - 使用多云图标
            else -> R.drawable.ic_weather_cloudy_new
        }
    }
    
    /**
     * 格式化小时时间显示
     * @param hour 小时数 (0-23)
     * @param position 在列表中的位置
     * @return 格式化后的时间字符串
     */
    private fun formatHourTime(hour: Int, position: Int): String {
        if (position == 0) {
            return "现在"
        }
        
        // 对于其他位置，检查是否需要显示日期
        val data = hourlyData[position]
        val currentTime = System.currentTimeMillis()
        val dataTime = data.timeEpoch * 1000
        
        val currentCalendar = Calendar.getInstance().apply { timeInMillis = currentTime }
        val dataCalendar = Calendar.getInstance().apply { timeInMillis = dataTime }
        
        // 如果是不同天，显示"明天xx时"，否则只显示"xx时"
        return if (currentCalendar.get(Calendar.DAY_OF_YEAR) != dataCalendar.get(Calendar.DAY_OF_YEAR)) {
            "明天${hour}时"
        } else {
            "${hour}时"
        }
    }
} 