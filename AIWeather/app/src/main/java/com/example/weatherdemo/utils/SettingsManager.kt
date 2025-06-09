package com.example.weatherdemo.utils

import android.content.Context
import android.content.SharedPreferences

class SettingsManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "weather_settings"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        
        const val UNIT_CELSIUS = "celsius"
        const val UNIT_FAHRENHEIT = "fahrenheit"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 设置温度单位
     */
    fun setTemperatureUnit(unit: String) {
        prefs.edit().putString(KEY_TEMPERATURE_UNIT, unit).apply()
    }
    
    /**
     * 获取温度单位，默认为摄氏度
     */
    fun getTemperatureUnit(): String {
        return prefs.getString(KEY_TEMPERATURE_UNIT, UNIT_CELSIUS) ?: UNIT_CELSIUS
    }
    
    /**
     * 是否使用摄氏度
     */
    fun isCelsius(): Boolean {
        return getTemperatureUnit() == UNIT_CELSIUS
    }
    
    /**
     * 是否使用华氏度
     */
    fun isFahrenheit(): Boolean {
        return getTemperatureUnit() == UNIT_FAHRENHEIT
    }
    
    /**
     * 温度转换：摄氏度转华氏度
     */
    fun celsiusToFahrenheit(celsius: Double): Double {
        return celsius * 9.0 / 5.0 + 32.0
    }
    
    /**
     * 温度转换：摄氏度转华氏度（整数版本）
     */
    fun celsiusToFahrenheit(celsius: Int): Int {
        val fahrenheit = ((celsius * 9.0 / 5.0) + 32.0).toInt()
        return fahrenheit
    }
    
    /**
     * 格式化温度显示
     */
    fun formatTemperature(celsius: Int): String {
        return if (isCelsius()) {
            "${celsius}°C"
        } else {
            "${celsiusToFahrenheit(celsius)}°F"
        }
    }
    
    /**
     * 格式化温度显示（仅数字）- 增强版本，添加防护措施
     */
    fun formatTemperatureValue(celsius: Int): String {
        // 🔧 添加输入验证和异常值处理，确保温度显示的稳定性
        
        return try {
            // 1. 边界检查 - 确保温度值在合理范围内
            val validatedTemp = when {
                celsius < -99 -> {
                    android.util.Log.w("SettingsManager", "温度值过低($celsius°C)，限制为最小显示值")
                    -99
                }
                celsius > 99 -> {
                    android.util.Log.w("SettingsManager", "温度值过高($celsius°C)，限制为最大显示值")
                    99
                }
                else -> celsius
            }
            
            // 2. 根据设置的单位进行转换
            if (isCelsius()) {
                "${validatedTemp}°"
            } else {
                // 3. 华氏度转换时的额外保护
                val fahrenheitValue = try {
                    celsiusToFahrenheit(validatedTemp)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsManager", "华氏度转换失败: ${e.message}")
                    // 如果转换失败，使用简单公式作为回退
                    (validatedTemp * 9 / 5) + 32
                }
                
                // 4. 确保华氏度值也在合理显示范围内
                val finalFahrenheit = fahrenheitValue.coerceIn(-99, 199)
                "${finalFahrenheit}°"
            }
        } catch (e: Exception) {
            // 5. 最后的异常处理 - 如果所有处理都失败，返回占位符
            android.util.Log.e("SettingsManager", "温度格式化失败: ${e.message}")
            "--°"
        }
    }
    
    /**
     * 获取温度单位符号
     */
    fun getTemperatureUnitSymbol(): String {
        return if (isCelsius()) "°C" else "°F"
    }
    
    /**
     * 获取温度单位显示名称
     */
    fun getTemperatureUnitName(): String {
        return if (isCelsius()) "摄氏度" else "华氏度"
    }
} 