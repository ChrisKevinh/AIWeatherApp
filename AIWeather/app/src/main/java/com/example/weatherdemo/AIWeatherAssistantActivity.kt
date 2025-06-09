package com.example.weatherdemo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weatherdemo.adapter.SearchResultAdapter
import com.example.weatherdemo.databinding.ActivityAiWeatherAssistantBinding
import com.example.weatherdemo.databinding.DialogAiCitySearchBinding
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.WeatherResponse
import com.example.weatherdemo.network.OpenRouterApiService
import com.example.weatherdemo.network.SearchResult
import com.example.weatherdemo.repository.WeatherRepository
import com.example.weatherdemo.viewmodel.WeatherViewModel
import com.example.weatherdemo.viewmodel.WeatherViewModelFactory
import kotlinx.coroutines.launch

class AIWeatherAssistantActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAiWeatherAssistantBinding
    private lateinit var viewModel: WeatherViewModel
    private lateinit var openRouterApiService: OpenRouterApiService
    private lateinit var repository: WeatherRepository
    
    // 当前选择的城市
    private var selectedCityData: WeatherData? = null
    private var selectedCityForecastData: List<WeatherData>? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiWeatherAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupSystemUI()
        setupToolbar()
        setupServices()
        setupViewModel()
        setupUI()
        setupObservers()
        
        Log.d("AIWeatherAssistant", "AI天气助手页面启动")
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
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupServices() {
        val application = application as WeatherApplication
        openRouterApiService = application.openRouterApiService
        repository = application.repository
    }
    
    private fun setupViewModel() {
        val application = application as WeatherApplication
        val factory = WeatherViewModelFactory(application.repository)
        viewModel = ViewModelProvider(this, factory)[WeatherViewModel::class.java]
    }
    
    private fun setupUI() {
        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshAIAnalysis()
        }
        
        // 设置刷新颜色主题
        binding.swipeRefreshLayout.setColorSchemeColors(
            resources.getColor(android.R.color.white, theme),
            resources.getColor(android.R.color.holo_blue_light, theme),
            resources.getColor(android.R.color.holo_green_light, theme)
        )
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            resources.getColor(android.R.color.darker_gray, theme)
        )
        
        // 设置重试按钮点击事件
        binding.retryButton.setOnClickListener {
            requestAIAnalysis()
        }
        
        // 设置城市选择按钮点击事件
        binding.changeCityButton.setOnClickListener {
            showCitySearchDialog()
        }
        
        // 初始状态
        showLoadingState()
    }
    
    private fun setupObservers() {
        // 观察定位城市数据
        viewModel.locationCity.observe(this) { locationCity ->
            locationCity?.let { 
                Log.d("AIWeatherAssistant", "获取到定位城市数据：${it.cityName}")
                if (selectedCityData == null) {
                    // 如果没有选择城市，使用定位城市
                    selectedCityData = it
                    updateSelectedCityUI(it)
                    
                    // 获取定位城市的7天预报数据
                    lifecycleScope.launch {
                        try {
                            val result = repository.getWeatherForecast(it.cityName, 7, false)
                            result.onSuccess { forecastList ->
                                selectedCityForecastData = forecastList
                                requestAIAnalysis()
                            }.onFailure { exception ->
                                Log.e("AIWeatherAssistant", "获取定位城市预报失败", exception)
                                requestAIAnalysis() // 即使预报失败也尝试分析当前天气
                            }
                        } catch (e: Exception) {
                            Log.e("AIWeatherAssistant", "获取预报异常", e)
                            requestAIAnalysis()
                        }
                    }
                }
            }
        }
        
        // 观察刷新状态
        viewModel.isMainRefreshing.observe(this) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }
        
        // 观察城市搜索结果
        viewModel.searchResults.observe(this) { results ->
            // 这里不处理，搜索结果在对话框中处理
        }
        
        // 加载定位城市数据
        viewModel.loadLocationCityFromDatabase()
    }
    
    private fun showCitySearchDialog() {
        Log.d("AIWeatherAssistant", "显示城市搜索对话框")
        
        val dialogBinding = DialogAiCitySearchBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            window?.let { window ->
                window.setBackgroundDrawableResource(android.R.color.transparent)
                // 设置对话框大小
                val params = window.attributes
                params.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
                params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                window.attributes = params
            }
            setCancelable(true)
        }
        
        // 设置搜索结果适配器
        val searchAdapter = SearchResultAdapter { searchResult ->
            Log.d("AIWeatherAssistant", "用户选择城市：${searchResult.name}")
            selectCity(searchResult)
            dialog.dismiss()
        }
        
        dialogBinding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AIWeatherAssistantActivity)
            adapter = searchAdapter
            Log.d("AIWeatherAssistant", "RecyclerView adapter已设置")
        }
        
        // 加载并显示已保存的城市
        loadSavedCitiesForDialog(searchAdapter, dialogBinding)
        
        // 搜索输入监听
        dialogBinding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                Log.d("AIWeatherAssistant", "搜索查询变更：'$query'")
                
                if (query.isEmpty()) {
                    // 搜索框为空时，显示已保存的城市
                    Log.d("AIWeatherAssistant", "搜索框为空，显示已保存城市")
                    loadSavedCitiesForDialog(searchAdapter, dialogBinding)
                } else if (query.length >= 2) {
                    // 开始搜索
                    searchCities(query, searchAdapter, dialogBinding)
                } else {
                    // 输入长度不足，显示提示
                    searchAdapter.updateResults(emptyList())
                    dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                    dialogBinding.emptyStateText.visibility = View.VISIBLE
                    dialogBinding.listTitleText.visibility = View.GONE
                    dialogBinding.emptyStateText.text = "继续输入以搜索城市"
                    Log.d("AIWeatherAssistant", "查询长度不足，显示提示")
                }
            }
        })
        
        // 取消按钮
        dialogBinding.cancelButton.setOnClickListener {
            Log.d("AIWeatherAssistant", "用户取消搜索")
            dialog.dismiss()
        }
        
        Log.d("AIWeatherAssistant", "显示对话框")
        dialog.show()
        
        // 自动聚焦到搜索框并弹出键盘
        dialogBinding.searchEditText.requestFocus()
        dialog.window?.decorView?.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(dialogBinding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    /**
     * 为对话框加载已保存的城市
     */
    private fun loadSavedCitiesForDialog(adapter: SearchResultAdapter, dialogBinding: DialogAiCitySearchBinding) {
        Log.d("AIWeatherAssistant", "加载已保存的城市用于对话框显示")
        
        lifecycleScope.launch {
            try {
                // 获取已保存的城市列表
                val savedCities = repository.getAllSavedWeatherData()
                
                // 获取定位城市
                val locationCity = repository.getLocationCityData()
                
                // 创建城市选项列表
                val cityOptions = mutableListOf<SearchResult>()
                
                // 添加定位城市（如果存在）
                locationCity?.let { locCity ->
                    cityOptions.add(SearchResult(
                        id = locCity.id.hashCode().toLong(),
                        name = "${locCity.cityName} (当前位置)",
                        region = "定位城市",
                        country = "",
                        lat = 0.0,
                        lon = 0.0,
                        url = ""
                    ))
                    Log.d("AIWeatherAssistant", "添加定位城市：${locCity.cityName}")
                }
                
                // 添加用户保存的城市
                savedCities.forEach { cityData ->
                    // 避免重复添加定位城市
                    if (locationCity == null || cityData.cityName != locationCity.cityName) {
                        cityOptions.add(SearchResult(
                            id = cityData.id.hashCode().toLong(),
                            name = cityData.cityName,
                            region = "已保存的城市",
                            country = "",
                            lat = 0.0,
                            lon = 0.0,
                            url = ""
                        ))
                        Log.d("AIWeatherAssistant", "添加用户城市：${cityData.cityName}")
                    }
                }
                
                if (cityOptions.isNotEmpty()) {
                    Log.d("AIWeatherAssistant", "显示${cityOptions.size}个已保存的城市")
                    adapter.updateResults(cityOptions)
                    dialogBinding.searchResultsRecyclerView.visibility = View.VISIBLE
                    dialogBinding.emptyStateText.visibility = View.GONE
                    dialogBinding.listTitleText.visibility = View.VISIBLE
                    dialogBinding.listTitleText.text = "已保存的城市"
                } else {
                    Log.d("AIWeatherAssistant", "没有已保存的城市")
                    adapter.updateResults(emptyList())
                    dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                    dialogBinding.emptyStateText.visibility = View.VISIBLE
                    dialogBinding.listTitleText.visibility = View.GONE
                    dialogBinding.emptyStateText.text = "暂无已保存的城市\n输入城市名称进行搜索"
                }
                
            } catch (e: Exception) {
                Log.e("AIWeatherAssistant", "加载已保存城市失败", e)
                adapter.updateResults(emptyList())
                dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                dialogBinding.emptyStateText.visibility = View.VISIBLE
                dialogBinding.listTitleText.visibility = View.GONE
                dialogBinding.emptyStateText.text = "加载已保存城市失败\n输入城市名称进行搜索"
            }
        }
    }
    
    private fun searchCities(query: String, adapter: SearchResultAdapter, dialogBinding: DialogAiCitySearchBinding) {
        Log.d("AIWeatherAssistant", "开始搜索城市：$query")
        lifecycleScope.launch {
            try {
                val result = repository.searchCities(query)
                result.onSuccess { searchResults ->
                    Log.d("AIWeatherAssistant", "搜索成功，找到${searchResults.size}个城市")
                    if (searchResults.isNotEmpty()) {
                        adapter.updateResults(searchResults)
                        dialogBinding.searchResultsRecyclerView.visibility = View.VISIBLE
                        dialogBinding.emptyStateText.visibility = View.GONE
                        dialogBinding.listTitleText.visibility = View.VISIBLE
                        dialogBinding.listTitleText.text = "搜索结果 (${searchResults.size})"
                        Log.d("AIWeatherAssistant", "显示搜索结果")
                    } else {
                        adapter.updateResults(emptyList())
                        dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                        dialogBinding.emptyStateText.visibility = View.VISIBLE
                        dialogBinding.listTitleText.visibility = View.GONE
                        dialogBinding.emptyStateText.text = "未找到相关城市"
                        Log.d("AIWeatherAssistant", "搜索结果为空")
                    }
                }.onFailure { exception ->
                    Log.e("AIWeatherAssistant", "搜索城市失败", exception)
                    adapter.updateResults(emptyList())
                    dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                    dialogBinding.emptyStateText.visibility = View.VISIBLE
                    dialogBinding.listTitleText.visibility = View.GONE
                    dialogBinding.emptyStateText.text = "搜索失败：${exception.message}"
                }
            } catch (e: Exception) {
                Log.e("AIWeatherAssistant", "搜索异常", e)
                dialogBinding.searchResultsRecyclerView.visibility = View.GONE
                dialogBinding.emptyStateText.visibility = View.VISIBLE
                dialogBinding.listTitleText.visibility = View.GONE
                dialogBinding.emptyStateText.text = "搜索出错：${e.message}"
            }
        }
    }
    
    private fun selectCity(searchResult: SearchResult) {
        Log.d("AIWeatherAssistant", "选择城市：${searchResult.name}")
        
        // 显示加载状态
        showLoadingState()
        binding.selectedCityText.text = searchResult.name
        
        lifecycleScope.launch {
            try {
                // 获取选中城市的天气数据（7天预报）
                val result = repository.getWeatherForecast(searchResult.name, 7, false)
                result.onSuccess { weatherDataList: List<WeatherData> ->
                    // 取第一个作为当前天气数据
                    if (weatherDataList.isNotEmpty()) {
                        val weatherData = weatherDataList[0].copy(
                            id = "${searchResult.name}_${System.currentTimeMillis()}",
                            cityName = searchResult.name,
                            isLocationCity = false
                        )
                        
                        selectedCityData = weatherData
                        selectedCityForecastData = weatherDataList // 保存完整的预报数据
                        updateSelectedCityUI(weatherData)
                        requestAIAnalysis()
                    } else {
                        showErrorState("获取${searchResult.name}天气数据为空")
                    }
                    
                }.onFailure { exception: Throwable ->
                    Log.e("AIWeatherAssistant", "获取城市天气失败", exception)
                    showErrorState("获取${searchResult.name}天气数据失败：${exception.message}")
                }
            } catch (e: Exception) {
                Log.e("AIWeatherAssistant", "选择城市异常", e)
                showErrorState("获取天气数据时出错：${e.message}")
            }
        }
    }
    
    private fun updateSelectedCityUI(weatherData: WeatherData) {
        binding.selectedCityText.text = weatherData.cityName
    }
    
    private fun requestAIAnalysis() {
        val cityData = selectedCityData
        if (cityData == null) {
            showErrorState("请先选择城市")
            return
        }
        
        showLoadingState()
        
        lifecycleScope.launch {
            try {
                val weatherDataText = formatWeatherDataForAI(cityData)
                Log.d("AIWeatherAssistant", "准备发送给AI的天气数据：$weatherDataText")
                
                val result = openRouterApiService.getWeatherAnalysis(weatherDataText)
                
                result.onSuccess { aiResponse ->
                    Log.d("AIWeatherAssistant", "AI分析成功：$aiResponse")
                    val cleanedResponse = cleanMarkdownText(aiResponse)
                    showAIResponse(cleanedResponse)
                }.onFailure { exception ->
                    Log.e("AIWeatherAssistant", "AI分析失败", exception)
                    showErrorState("AI分析失败：${exception.message}")
                }
                
            } catch (e: Exception) {
                Log.e("AIWeatherAssistant", "请求AI分析时出错", e)
                showErrorState("请求失败：${e.message}")
            }
        }
    }
    
    private fun formatWeatherDataForAI(weatherData: WeatherData): String {
        val forecastData = selectedCityForecastData ?: listOf(weatherData)
        
        val weatherText = StringBuilder()
        
        // 基本城市信息
        weatherText.append("城市：${weatherData.cityName}\n")
        weatherText.append("更新时间：${weatherData.date}\n\n")
        
        // 今日详细天气
        weatherText.append("=== 今日天气详情 ===\n")
        weatherText.append("当前温度：${weatherData.temperature}°C\n")
        weatherText.append("最高温度：${weatherData.temperatureMax}°C\n")
        weatherText.append("最低温度：${weatherData.temperatureMin}°C\n")
        weatherText.append("体感温度：${weatherData.feelsLike}°C\n")
        weatherText.append("天气状况：${weatherData.weatherDescription}\n")
        weatherText.append("湿度：${weatherData.humidity}%\n")
        weatherText.append("气压：${weatherData.pressure} hPa\n")
        weatherText.append("风速：${weatherData.windSpeed} km/h\n")
        weatherText.append("风向：${weatherData.windDegree}°\n")
        weatherText.append("能见度：${weatherData.visibility} km\n")
        weatherText.append("紫外线指数：${weatherData.uvIndex}\n\n")
        
        // 未来几天预报
        if (forecastData.size > 1) {
            weatherText.append("=== 未来几天天气预报 ===\n")
            forecastData.drop(1).forEachIndexed { index, forecast ->
                val dayName = when (index) {
                    0 -> "明天"
                    1 -> "后天"
                    else -> "第${index + 2}天"
                }
                
                weatherText.append("$dayName (${forecast.date})：\n")
                weatherText.append("  温度：${forecast.temperatureMin}°C - ${forecast.temperatureMax}°C\n")
                weatherText.append("  天气：${forecast.weatherDescription}\n")
                weatherText.append("  湿度：${forecast.humidity}%\n")
                weatherText.append("  紫外线：${forecast.uvIndex}\n\n")
            }
        }
        
        return weatherText.toString().trimEnd()
    }
    
    /**
     * 清理文本中的markdown格式符号
     */
    private fun cleanMarkdownText(text: String): String {
        var cleanedText = text
        
        // 处理粗体标记 **文本** -> 文本
        cleanedText = cleanedText.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        
        // 处理斜体标记 *文本* -> 文本（但要注意不要误删正常的*符号）
        // 只处理被空格或标点符号包围的*文本*格式
        cleanedText = cleanedText.replace(Regex("(?<=\\s|^|[\\p{Punct}&&[^*]])\\*([^*\\s][^*]*[^*\\s]|[^*\\s])\\*(?=\\s|$|[\\p{Punct}&&[^*]])"), "$1")
        
        // 处理标题标记 ### 文本 -> 文本
        cleanedText = cleanedText.replace(Regex("^#{1,6}\\s*(.+)$", RegexOption.MULTILINE), "$1")
        
        // 处理列表标记 - 文本 -> • 文本
        cleanedText = cleanedText.replace(Regex("^-\\s+", RegexOption.MULTILINE), "• ")
        
        // 处理序号列表 1. 文本 -> 1. 文本（保持不变，这个比较清晰）
        
        // 清理多余的空行（连续的换行符）
        cleanedText = cleanedText.replace(Regex("\n{3,}"), "\n\n")
        
        // 去除首尾空白
        cleanedText = cleanedText.trim()
        
        return cleanedText
    }
    
    private fun showLoadingState() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
        binding.aiResponseCard.visibility = View.GONE
    }
    
    private fun showErrorState(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.aiResponseCard.visibility = View.GONE
        binding.errorMessageText.text = message
    }
    
    private fun showAIResponse(response: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.aiResponseCard.visibility = View.VISIBLE
        binding.aiResponseText.text = response
    }
    
    private fun refreshAIAnalysis() {
        Log.d("AIWeatherAssistant", "刷新AI分析")
        selectedCityData?.let { cityData ->
            // 重新获取该城市的天气数据（7天预报）
            lifecycleScope.launch {
                try {
                    val result = repository.getWeatherForecast(cityData.cityName, 7, false)
                    result.onSuccess { weatherDataList: List<WeatherData> ->
                        // 更新城市数据
                        if (weatherDataList.isNotEmpty()) {
                            val updatedWeatherData = weatherDataList[0].copy(
                                id = cityData.id,
                                cityName = cityData.cityName,
                                isLocationCity = cityData.isLocationCity
                            )
                            
                            selectedCityData = updatedWeatherData
                            selectedCityForecastData = weatherDataList // 保存完整的预报数据
                            updateSelectedCityUI(updatedWeatherData)
                            requestAIAnalysis()
                        } else {
                            showErrorState("刷新天气数据为空")
                        }
                    }.onFailure { exception: Throwable ->
                        Log.e("AIWeatherAssistant", "刷新天气数据失败", exception)
                        showErrorState("刷新失败：${exception.message}")
                    }
                } catch (e: Exception) {
                    Log.e("AIWeatherAssistant", "刷新异常", e)
                    showErrorState("刷新出错：${e.message}")
                }
            }
        } ?: run {
            // 如果没有选择城市，重新加载定位城市
            viewModel.loadLocationCityFromDatabase()
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
} 