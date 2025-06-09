package com.example.weatherdemo.adapter

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.databinding.ItemCityWeatherBinding
import com.example.weatherdemo.utils.SettingsManager
import java.text.SimpleDateFormat
import java.util.*

class CityListAdapter(
    private val context: Context,
    private val onCityClick: (WeatherData) -> Unit,
    private val onCityDelete: (WeatherData, Int) -> Unit = { _, _ -> }  // 删除回调
) : RecyclerView.Adapter<CityListAdapter.CityViewHolder>() {
    
    private var cities = mutableListOf<WeatherData>()
    private var isEditMode = false  // 编辑模式标识
    private lateinit var settingsManager: SettingsManager
    
    init {
        settingsManager = SettingsManager.getInstance(context)
    }
    
    fun updateCities(newCities: List<WeatherData>) {
        cities.clear()
        cities.addAll(newCities)
        notifyDataSetChanged()
    }
    
    fun addCity(weatherData: WeatherData) {
        cities.add(weatherData)
        notifyItemInserted(cities.size - 1)
    }
    
    fun removeCity(position: Int) {
        if (position in 0 until cities.size) {
            cities.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    
    /**
     * 更新温度单位，刷新显示
     */
    fun updateTemperatureUnit() {
        // 重新初始化SettingsManager，确保获取最新设置
        settingsManager = SettingsManager.getInstance(context)
    }
    
    // 设置编辑模式 - 添加动画效果
    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            // 使用自定义方法逐个更新ViewHolder，而不是notifyDataSetChanged
            for (i in 0 until itemCount) {
                notifyItemChanged(i, "edit_mode_change")
            }
        }
    }
    
    // 获取编辑模式状态
    fun isInEditMode(): Boolean = isEditMode
    
    // 获取城市在列表中的位置
    fun getCityPosition(weatherData: WeatherData): Int {
        return cities.indexOf(weatherData)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder {
        val binding = ItemCityWeatherBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CityViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        holder.bind(cities[position])
    }
    
    override fun onBindViewHolder(holder: CityViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // 处理局部更新
            if (payloads.contains("edit_mode_change")) {
                holder.updateEditMode(cities[position])
            }
        }
    }
    
    override fun getItemCount(): Int = cities.size
    
    inner class CityViewHolder(
        private val binding: ItemCityWeatherBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(weatherData: WeatherData) {
            binding.apply {
                // =============================================================================
                // 🔧 【关键修复】状态重置 - 根据编辑模式正确设置初始状态
                // =============================================================================
                
                // 1. 立即取消所有进行中的动画，避免状态冲突
                temperatureContainer.animate().cancel()
                deleteButtonContainer.animate().cancel()
                
                // 2. 重置基础显示属性（不包含visibility）
                temperatureContainer.apply {
                    alpha = 1f                              // 完全不透明
                    scaleX = 1f                             // 正常缩放
                    scaleY = 1f                             // 正常缩放
                    translationX = 0f                       // 重置位移
                    translationY = 0f                       // 重置位移
                    
                    // 恢复正常布局参数
                    val layoutParams = layoutParams
                    layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    this.layoutParams = layoutParams
                }
                
                // 3. 重置删除按钮容器基础属性（不包含visibility）
                deleteButtonContainer.apply {
                    alpha = 1f                              // 完全不透明
                    scaleX = 1f                             // 正常缩放
                    scaleY = 1f                             // 正常缩放
                    translationX = 0f                       // 重置位移
                    translationY = 0f                       // 重置位移
                }
                

                if (isEditMode) {
                    // 编辑模式：隐藏温度，显示删除按钮（如果允许删除）
                    temperatureContainer.visibility = android.view.View.GONE
                    
                    val shouldShowDelete = !weatherData.isLocationCity
                    if (shouldShowDelete) {
                        deleteButtonContainer.visibility = android.view.View.VISIBLE
                    } else {
                        deleteButtonContainer.visibility = android.view.View.GONE
                    }
                } else {
                    // 普通模式：显示温度，隐藏删除按钮
                    temperatureContainer.visibility = android.view.View.VISIBLE
                    deleteButtonContainer.visibility = android.view.View.GONE
                }
                
                // =============================================================================
                
                cityNameText.text = weatherData.cityName
                
                // 🔧 关键修复：每次bind时重新获取SettingsManager，确保温度单位实时同步
                val currentSettingsManager = SettingsManager.getInstance(context)
                
                // 使用最新的SettingsManager格式化温度 - 修复华氏度切换问题
                currentTemperatureText.text = currentSettingsManager.formatTemperatureValue(weatherData.temperature)
                
                // 天气描述 - 温度范围也要转换单位
                val description = weatherData.weatherDescription.ifEmpty { "天气数据更新中" }
                val tempRange = if (weatherData.temperatureMax != weatherData.temperatureMin) {
                    "，最高气温 ${currentSettingsManager.formatTemperatureValue(weatherData.temperatureMax)}"
                } else {
                    ""
                }
                weatherDescriptionText.text = "${description}${tempRange}"
                
                // 温度范围 - 使用最新的SettingsManager格式化
                temperatureRangeText.text = "最高 ${currentSettingsManager.formatTemperatureValue(weatherData.temperatureMax)} 最低 ${currentSettingsManager.formatTemperatureValue(weatherData.temperatureMin)}"
                
                // 显示当前时间
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeText.text = timeFormat.format(Date())
                
                // 根据是否为定位城市显示位置图标
                if (weatherData.isLocationCity) {
                    locationIcon.visibility = android.view.View.VISIBLE
                } else {
                    locationIcon.visibility = android.view.View.GONE
                }
                
                // 设置点击事件
                root.setOnClickListener {
                    if (!isEditMode) {
                        onCityClick(weatherData)
                    }
                }
                
                // 删除按钮点击事件
                deleteButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        // 添加点击反馈动画
                        animateButtonPress(deleteButton) {
                            onCityDelete(weatherData, position)
                        }
                    }
                }
            }
        }
        
        /**
         * 平滑显示/隐藏删除按钮
         */
        private fun animateDeleteButtonVisibility(view: android.view.View, show: Boolean) {
            // 取消所有正在进行的动画，避免冲突
            view.animate().cancel()
            
            if (show && view.visibility != android.view.View.VISIBLE) {
                view.visibility = android.view.View.VISIBLE
                view.alpha = 0f
                view.scaleX = 0.3f
                view.scaleY = 0.3f
                
                // 弹性进入动画
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .setStartDelay(0)
                    .start()
                    
            } else if (!show && view.visibility == android.view.View.VISIBLE) {
                // 快速退出动画
                view.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .setStartDelay(0)
                    .withEndAction {
                        view.visibility = android.view.View.GONE
                    }
                    .start()
            }
        }
        
        /**
         * 按钮点击动画效果 - 优化版本
         */
        private fun animateButtonPress(view: android.view.View, onAnimationEnd: () -> Unit) {
            // 取消正在进行的动画
            view.animate().cancel()
            
            view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(80)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                        .withEndAction {
                            onAnimationEnd()
                        }
                        .start()
                }
                .start()
        }
        
        /**
         * 专门处理编辑模式切换的方法 - 恢复平滑过渡动画
         */
        fun updateEditMode(weatherData: WeatherData) {
            binding.apply {
                // 取消所有进行中的动画
                temperatureContainer.animate().cancel()
                deleteButtonContainer.animate().cancel()
                
                val shouldShowDelete = !weatherData.isLocationCity && isEditMode
                
                if (isEditMode) {
                    // 进入编辑模式：温度淡出，删除按钮弹入
                    animateTemperatureOut()
                    if (shouldShowDelete) {
                        animateDeleteButtonIn()
                    }
                } else {
                    // 退出编辑模式：删除按钮淡出，温度淡入
                    animateDeleteButtonOut()
                    animateTemperatureIn()
                }
            }
        }
        
        /**
         * 温度容器淡出动画 - 简化版本，移除复杂的高度动画
         */
        private fun animateTemperatureOut() {
            val container = binding.temperatureContainer
            
            // 检查动画状态，避免重复执行
            if (container.visibility == android.view.View.GONE) {
                return
            }
            
            // 取消所有进行中的动画，避免冲突
            container.animate().cancel()
            
            // 🔧 简化动画：仅使用透明度和轻微缩放，移除复杂的高度动画
            container.animate()
                .alpha(0f)
                .scaleX(0.95f)  // 轻微缩放，避免过度效果
                .scaleY(0.95f)
                .setDuration(200)  // 缩短动画时间，提高响应性
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withStartAction {
                    // 动画开始前的状态确认
                    android.util.Log.d("CityListAdapter", "温度容器淡出动画开始")
                }
                .withEndAction {
                    // 动画完成后安全设置为GONE
                    container.visibility = android.view.View.GONE
                    android.util.Log.d("CityListAdapter", "温度容器淡出动画完成")
                }
                .start()
        }
        
        /**
         * 温度容器淡入动画 - 简化版本，移除复杂的高度动画
         */
        private fun animateTemperatureIn() {
            val container = binding.temperatureContainer
            
            // 检查动画状态，避免重复执行
            if (container.visibility == android.view.View.VISIBLE && container.alpha == 1f) {
                return
            }
            
            // 取消所有进行中的动画，避免冲突
            container.animate().cancel()
            
            // 🔧 简化动画：仅使用透明度和轻微缩放，移除复杂的高度动画
            // 1. 立即设置初始状态
            container.visibility = android.view.View.VISIBLE
            container.alpha = 0f
            container.scaleX = 0.95f
            container.scaleY = 0.95f
            
            // 2. 确保布局参数正确
            val layoutParams = container.layoutParams
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            container.layoutParams = layoutParams
            
            // 3. 执行简化的淡入动画
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)  // 适中的动画时间
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withStartAction {
                    // 动画开始前的状态确认
                    android.util.Log.d("CityListAdapter", "温度容器淡入动画开始")
                }
                .withEndAction {
                    // 动画完成后的状态验证
                    if (container.visibility != android.view.View.VISIBLE) {
                        container.visibility = android.view.View.VISIBLE
                        container.alpha = 1f
                    }
                    android.util.Log.d("CityListAdapter", "温度容器淡入动画完成")
                }
                .start()
        }
        
        /**
         * 删除按钮弹入动画 - 优化用户体验，快速响应
         */
        private fun animateDeleteButtonIn() {
            binding.apply {
                deleteButtonContainer.visibility = android.view.View.VISIBLE
                deleteButtonContainer.alpha = 0f
                deleteButtonContainer.scaleX = 0.5f  // 更自然的起始缩放
                deleteButtonContainer.scaleY = 0.5f
                
                deleteButtonContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)  // 缩短时间，提高响应性
                    .setStartDelay(50)   // 大幅减少延迟，几乎同时开始
                    .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))  // 适中的弹性效果
                    .start()
            }
        }
        
        /**
         * 删除按钮淡出动画 - 优化用户体验，快速响应
         */
        private fun animateDeleteButtonOut() {
            binding.deleteButtonContainer.animate()
                .alpha(0f)
                .scaleX(0.5f)  // 与弹入动画对称
                .scaleY(0.5f)
                .setDuration(200)  // 快速退出，提高响应性
                .setInterpolator(android.view.animation.AccelerateInterpolator())  // 快速退出
                .withEndAction {
                    binding.deleteButtonContainer.visibility = android.view.View.GONE
                }
                .start()
        }
    }
} 