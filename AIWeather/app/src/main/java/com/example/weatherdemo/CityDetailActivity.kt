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
        
        // 观察小时级天气数据并更新图表
        viewModel.hourlyWeatherData.observe(this) { hourlyDataList ->
            if (hourlyDataList.isNotEmpty()) {
                WeatherChartHelper.setupTemperatureChart(this, temperatureChart, hourlyDataList)
                WeatherChartHelper.setupPrecipitationChart(this, precipitationChart, hourlyDataList)
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
        
        // 3. 重新绘制图表
        viewModel.hourlyWeatherData.value?.let { hourlyDataList ->
            if (hourlyDataList.isNotEmpty()) {
                WeatherChartHelper.setupTemperatureChart(this, temperatureChart, hourlyDataList)
                WeatherChartHelper.setupPrecipitationChart(this, precipitationChart, hourlyDataList)
            }
        }
    }
}