package com.example.weatherdemo.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherdemo.R
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.utils.SettingsManager
import java.text.SimpleDateFormat
import java.util.*

class ForecastAdapter(private val context: Context) : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {
    
    private var forecastList = listOf<WeatherData>()
    private val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    private val settingsManager = SettingsManager.getInstance(context)
    
    fun updateForecast(newForecastList: List<WeatherData>) {
        forecastList = newForecastList
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast, parent, false)
        return ForecastViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val forecast = forecastList[position]
        holder.bind(forecast, position == 0)
    }
    
    override fun getItemCount(): Int = forecastList.size
    
    inner class ForecastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayText: TextView = itemView.findViewById(R.id.dayText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val weatherText: TextView = itemView.findViewById(R.id.weatherText)
        private val tempHighText: TextView = itemView.findViewById(R.id.tempHighText)
        private val tempLowText: TextView = itemView.findViewById(R.id.tempLowText)
        
        fun bind(forecast: WeatherData, isToday: Boolean) {
            val currentSettingsManager = SettingsManager.getInstance(context)
            
            if (isToday) {
                dayText.text = "今天"
            } else {
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.date)
                    dayText.text = if (date != null) dayFormat.format(date) else forecast.date
                } catch (e: Exception) {
                    dayText.text = forecast.date
                }
            }
            
            dateText.text = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecast.date)
                if (date != null) dateFormat.format(date) else forecast.date
            } catch (e: Exception) {
                forecast.date
            }
            
            weatherText.text = forecast.weather
            tempHighText.text = currentSettingsManager.formatTemperatureValue(forecast.temperatureMax)
            tempLowText.text = currentSettingsManager.formatTemperatureValue(forecast.temperatureMin)
        }
    }
} 