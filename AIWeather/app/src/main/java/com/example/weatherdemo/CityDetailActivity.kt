package com.example.weatherdemo

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weatherdemo.adapter.ForecastAdapter
import com.example.weatherdemo.adapter.HourlyWeatherAdapter
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.databinding.ActivityCityDetailBinding
import com.example.weatherdemo.ui.WeatherChartHelper
import com.example.weatherdemo.utils.SettingsManager
import com.example.weatherdemo.viewmodel.WeatherViewModel
import com.example.weatherdemo.viewmodel.WeatherViewModelFactory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import java.text.SimpleDateFormat
import java.util.*

class CityDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCityDetailBinding
    private lateinit var viewModel: WeatherViewModel
    private lateinit var forecastAdapter: ForecastAdapter
    private lateinit var hourlyWeatherAdapter: HourlyWeatherAdapter
    
    // 图表组件
    private lateinit var temperatureChart: LineChart
    private lateinit var precipitationChart: BarChart
    
    // 加载状态相关
    private var loadingView: View? = null
    private var isDataLoaded = false
    
    private lateinit var settingsManager: SettingsManager
    private var lastTemperatureUnit: String = ""
    
    companion object {
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_FROM_SEARCH = "extra_from_search"  // 标识是否从搜索进入
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        lastTemperatureUnit = settingsManager.getTemperatureUnit()
        
        setupSystemUI()
        setupToolbar()
        setupViewModel()
        setupUI()
        setupObservers()
        
        // 获取城市名称并显示加载状态
        val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "北京"
        
        // 立即显示加载状态
        showLoadingState(cityName)
        
        // 加载数据
        viewModel.loadWeatherData(cityName)
        viewModel.loadHourlyWeatherData(cityName)
        
        // 加载已保存的城市列表和定位城市（用于检查重复）
        viewModel.loadSavedCities()
        viewModel.loadLocationCityFromDatabase()
    }
    
    override fun onResume() {
        super.onResume()
        
        // 检查温度单位是否发生变化
        val currentUnit = settingsManager.getTemperatureUnit()
        if (currentUnit != lastTemperatureUnit) {
            lastTemperatureUnit = currentUnit
            
            // 温度单位发生变化，刷新界面
            refreshTemperatureDisplay()
        }
    }
    
    private fun setupSystemUI() {
        // 设置状态栏
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = 
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = ""
        }
        
        // 确保返回按钮点击事件
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupViewModel() {
        val application = application as WeatherApplication
        val factory = WeatherViewModelFactory(application.repository)
        viewModel = ViewModelProvider(this, factory)[WeatherViewModel::class.java]
    }
    
    private fun setupUI() {
        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshWeatherData()
        }
        
        // 设置预报列表 - 传递context给ForecastAdapter
        forecastAdapter = ForecastAdapter(this)
        binding.forecastRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CityDetailActivity)
            adapter = forecastAdapter
        }
        
        // 设置24小时天气预报列表
        hourlyWeatherAdapter = HourlyWeatherAdapter(this)
        binding.hourlyWeatherRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CityDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = hourlyWeatherAdapter
        }
        
        // 初始化图表组件
        temperatureChart = binding.temperatureChart
        precipitationChart = binding.precipitationChart
        
        // 检查是否从搜索进入，决定是否显示添加按钮
        val fromSearch = intent.getBooleanExtra(EXTRA_FROM_SEARCH, false)
        val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "北京"
        
        if (fromSearch) {
            // 从搜索进入，检查城市是否已存在
            checkCityExistsAndSetupAddButton(cityName)
        } else {
            // 从城市列表进入，隐藏添加按钮
            binding.addCityButton.visibility = android.view.View.GONE
        }
        
        Log.d("CityDetail", "来源: ${if (fromSearch) "搜索" else "城市列表"}")
    }
    
    private fun checkCityExistsAndSetupAddButton(cityName: String) {
        // 检查城市是否已经存在于用户列表或定位城市中
        viewModel.savedCities.observe(this) { savedCities ->
            val cityExists = savedCities.any { it.cityName.equals(cityName, ignoreCase = true) }
            
            // 也检查定位城市
            viewModel.locationCity.observe(this) { locationCity ->
                val isLocationCity = locationCity?.cityName?.equals(cityName, ignoreCase = true) == true
                
                if (cityExists || isLocationCity) {
                    // 城市已存在，隐藏添加按钮
                    binding.addCityButton.visibility = android.view.View.GONE
                    Log.d("CityDetail", "城市 $cityName 已存在，隐藏添加按钮")
                } else {
                    // 城市不存在，显示添加按钮
                    binding.addCityButton.visibility = android.view.View.VISIBLE
                    binding.addCityButton.setOnClickListener {
                        Log.d("CityDetail", "添加按钮被点击")
                        addCurrentCityToList()
                    }
                    Log.d("CityDetail", "城市 $cityName 不存在，显示添加按钮")
                }
            }
        }
    }
    
    private fun setupObservers() {
        // 观察当前天气数据
        viewModel.currentWeatherData.observe(this) { weatherData ->
            if (weatherData != null) {
                updateWeatherUI(weatherData)
                hideLoadingState()
                isDataLoaded = true
            }
        }
        
        // 观察预报数据
        viewModel.forecastWeatherData.observe(this) { forecastList ->
            forecastAdapter.updateForecast(forecastList)
        }
        
        // 观察加载状态
        viewModel.isLoading.observe(this) { isLoading ->
            if (!isLoading && isDataLoaded) {
                // 数据加载完成且有数据时，确保隐藏加载状态
                hideLoadingState()
            }
        }
        
        // 观察刷新状态
        viewModel.isRefreshing.observe(this) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }
        
        // 观察选中的城市
        viewModel.selectedCity.observe(this) { cityName ->
            binding.cityNameText.text = cityName
            supportActionBar?.title = cityName
        }
        
        // 观察错误消息
        viewModel.errorMessage.observe(this) { errorMessage ->
            if (errorMessage != null && !isDataLoaded) {
                // 只有在没有数据加载成功的情况下才显示错误
                hideLoadingState()
                showErrorState(errorMessage)
            }
        }
        
        // 观察小时级天气数据并更新图表和24小时预报
        viewModel.hourlyWeatherData.observe(this) { hourlyDataList ->
            Log.d("HourlyWeather", "观察到小时数据变化：${hourlyDataList?.size ?: 0}条")
            
            if (hourlyDataList != null && hourlyDataList.isNotEmpty()) {
                WeatherChartHelper.setupTemperatureChart(this, temperatureChart, hourlyDataList)
                WeatherChartHelper.setupPrecipitationChart(this, precipitationChart, hourlyDataList)
                
                // 更新24小时天气预报
                updateHourlyWeatherForecast(hourlyDataList)
            } else {
                // 如果没有数据，生成一些测试数据用于调试
                Log.d("HourlyWeather", "小时数据为空，生成测试数据")
                val testData = generateTestHourlyData()
                updateHourlyWeatherForecast(testData)
            }
        }
        
        // 主动检查是否已有数据（防止观察者错过已存在的数据）
        viewModel.hourlyWeatherData.value?.let { existingData ->
            Log.d("HourlyWeather", "发现已存在的小时数据：${existingData.size}条")
            if (existingData.isNotEmpty()) {
                updateHourlyWeatherForecast(existingData)
            }
        }
    }
    
    private fun updateWeatherUI(weatherData: com.example.weatherdemo.data.WeatherData) {
        binding.apply {
            // 🔧 关键修复：重新获取SettingsManager，确保温度单位实时同步
            val currentSettingsManager = SettingsManager.getInstance(this@CityDetailActivity)
            
            cityNameText.text = weatherData.cityName
            
            // 使用最新的SettingsManager格式化温度显示
            currentTemperatureText.text = currentSettingsManager.formatTemperatureValue(weatherData.temperature)
            weatherDescriptionText.text = weatherData.weatherDescription
            temperatureRangeText.text = "最高 ${currentSettingsManager.formatTemperatureValue(weatherData.temperatureMax)} 最低 ${currentSettingsManager.formatTemperatureValue(weatherData.temperatureMin)}"
            
            // 详细信息 - 使用最新的SettingsManager格式化体感温度
            feelsLikeText.text = currentSettingsManager.formatTemperatureValue(weatherData.feelsLike)
            humidityText.text = "${weatherData.humidity}%"
            windSpeedText.text = "${weatherData.windSpeed.toInt()} km/h"
            uvIndexText.text = weatherData.uvIndex.toInt().toString()
            
            // 更新时间
            val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(weatherData.timestamp))
            lastUpdateText.text = "最后更新: $updateTime"
        }
    }
    
    private fun refreshWeatherData() {
        val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "北京"
        viewModel.refreshWeatherData()
        
        // 同时刷新小时级数据用于图表
        viewModel.loadHourlyWeatherData(cityName)
    }
    
    private fun addCurrentCityToList() {
        viewModel.currentWeatherData.value?.let { weatherData ->
            viewModel.saveWeatherData(weatherData)
            
            // 添加成功后返回主页面
            finish()
        } ?: run {
            Toast.makeText(this, "请等待天气数据加载完成", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun finish() {
        super.finish()
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
    
    /**
     * 显示加载状态
     */
    private fun showLoadingState(cityName: String) {
        isDataLoaded = false
        
        // 隐藏主要内容
        binding.mainContentScrollView.visibility = View.GONE
        
        // 显示加载布局
        if (loadingView == null) {
            loadingView = binding.loadingViewStub.inflate()
        }
        loadingView?.visibility = View.VISIBLE
        
        // 设置加载中的城市名称
        loadingView?.findViewById<TextView>(R.id.loadingCityText)?.text = cityName
        
        // 启动加载动画
        val loadingIcon = loadingView?.findViewById<ImageView>(R.id.loadingIcon)
        loadingIcon?.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_loading))
    }
    
    /**
     * 隐藏加载状态
     */
    private fun hideLoadingState() {
        loadingView?.visibility = View.GONE
        binding.mainContentScrollView.visibility = View.VISIBLE
        
        // 停止加载动画
        val loadingIcon = loadingView?.findViewById<ImageView>(R.id.loadingIcon)
        loadingIcon?.clearAnimation()
    }
    
    /**
     * 显示错误状态
     */
    private fun showErrorState(errorMessage: String) {
        // 可以在这里添加错误状态的UI
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }
    
    /**
     * 更新24小时天气预报
     */
    private fun updateHourlyWeatherForecast(hourlyDataList: List<HourlyWeatherData>) {
        Log.d("HourlyWeather", "收到小时数据：${hourlyDataList.size}条")
        
        // 获取接下来24小时的数据
        val next24Hours = getNext24Hours(hourlyDataList)
        Log.d("HourlyWeather", "筛选后数据：${next24Hours.size}条")
        
        hourlyWeatherAdapter.updateHourlyData(next24Hours)
    }
    
    /**
     * 从小时数据中获取接下来24小时的数据
     */
    private fun getNext24Hours(hourlyDataList: List<HourlyWeatherData>): List<HourlyWeatherData> {
        if (hourlyDataList.isEmpty()) {
            Log.d("HourlyWeather", "输入数据为空")
            return emptyList()
        }
        
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentTimeMillis = System.currentTimeMillis()
        Log.d("HourlyWeather", "当前时间：${currentHour}时，时间戳：$currentTimeMillis")
        
        // 按时间戳排序，确保数据按时间顺序排列
        val sortedList = hourlyDataList.sortedBy { it.timeEpoch }
        
        // 找到当前小时或最接近当前小时的数据点
        var startIndex = sortedList.indexOfFirst { it.hour == currentHour }
        
        if (startIndex == -1) {
            // 如果找不到当前小时的数据，找最接近当前时间的数据
            var closestIndex = 0
            var minDiff = Math.abs(sortedList[0].timeEpoch * 1000 - currentTimeMillis)
            sortedList.forEachIndexed { index, data ->
                val diff = Math.abs(data.timeEpoch * 1000 - currentTimeMillis)
                if (diff < minDiff) {
                    minDiff = diff
                    closestIndex = index
                }
            }
            Log.d("HourlyWeather", "未找到当前小时(${currentHour})的数据，使用最接近的数据点，索引：$closestIndex")
            startIndex = closestIndex
        } else {
            Log.d("HourlyWeather", "找到当前小时(${currentHour})的数据点，索引：$startIndex")
        }
        
        // 从找到的位置开始取24小时的数据
        val result = if (startIndex + 24 <= sortedList.size) {
            // 如果后面还有足够的数据
            sortedList.subList(startIndex, startIndex + 24)
        } else {
            // 如果后面的数据不够24小时，就取到最后
            sortedList.subList(startIndex, sortedList.size)
        }
        
        Log.d("HourlyWeather", "最终返回数据：${result.size}条")
        result.forEach { 
            val timeStr = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(it.timeEpoch * 1000))
            Log.d("HourlyWeather", "时间：$timeStr (${it.hour}时)，温度：${it.temperature}°，降雨：${it.chanceOfRain}%")
        }
        
        return result
    }
    
    /**
     * 刷新温度显示 - 当温度单位变化时调用
     */
    private fun refreshTemperatureDisplay() {
        // 🔧 关键修复：重新获取SettingsManager实例，确保获取最新设置
        settingsManager = SettingsManager.getInstance(this)
        
        // 1. 刷新当前天气数据显示
        viewModel.currentWeatherData.value?.let { weatherData ->
            updateWeatherUI(weatherData)
        }
        
        // 2. 刷新预报数据显示
        forecastAdapter.notifyDataSetChanged()
        
        // 3. 刷新24小时天气预报显示
        hourlyWeatherAdapter.notifyDataSetChanged()
        
        // 4. 重新绘制图表
        viewModel.hourlyWeatherData.value?.let { hourlyDataList ->
            if (hourlyDataList.isNotEmpty()) {
                WeatherChartHelper.setupTemperatureChart(this, temperatureChart, hourlyDataList)
                WeatherChartHelper.setupPrecipitationChart(this, precipitationChart, hourlyDataList)
            }
        }
    }
    
    /**
     * 生成测试的24小时天气数据（用于调试）
     */
    private fun generateTestHourlyData(): List<HourlyWeatherData> {
        val testData = mutableListOf<HourlyWeatherData>()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentTime = System.currentTimeMillis()
        
        for (i in 0 until 24) {
            val hour = (currentHour + i) % 24
            val temperature = 25.0 + (Math.random() * 10 - 5) // 20-30度随机
            val chanceOfRain = (Math.random() * 100).toInt() // 0-100%随机
            
            val hourlyData = HourlyWeatherData(
                id = "test_${hour}",
                cityName = "测试城市",
                date = "2023-12-08",
                hour = hour,
                timeEpoch = currentTime + (i * 3600000L), // 每小时递增
                temperature = temperature,
                feelsLike = temperature + 2,
                humidity = 60,
                pressure = 1013.0,
                windSpeed = 10.0,
                windDegree = 180,
                precipitationMm = 0.0,
                chanceOfRain = chanceOfRain,
                chanceOfSnow = 0,
                cloudCover = 50,
                visibility = 10.0,
                uvIndex = 5.0,
                isDayTime = hour in 6..18,
                weatherDescription = if (chanceOfRain > 50) "多云有雨" else "晴朗",
                weatherIcon = "sunny"
            )
            
            testData.add(hourlyData)
        }
        
        Log.d("HourlyWeather", "生成了${testData.size}条测试数据")
        return testData
    }
}