package com.example.weatherdemo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weatherdemo.adapter.CityListAdapter
import com.example.weatherdemo.adapter.SearchResultAdapter
import com.example.weatherdemo.databinding.ActivityMainBinding
import com.example.weatherdemo.viewmodel.WeatherViewModel
import com.example.weatherdemo.viewmodel.WeatherViewModelFactory
import androidx.activity.OnBackPressedCallback
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import com.example.weatherdemo.data.WeatherData
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.location.LocationManager
import android.widget.TextView
import com.example.weatherdemo.ui.ModernPopupMenu
import com.example.weatherdemo.ui.ModernDeleteDialog
import android.widget.Button
import com.example.weatherdemo.widget.WeatherWidgetProvider
import androidx.activity.result.contract.ActivityResultContracts
import com.example.weatherdemo.utils.SettingsManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WeatherViewModel
    private lateinit var cityListAdapter: CityListAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var locationManager: com.example.weatherdemo.LocationManager
    private lateinit var settingsManager: SettingsManager
    
    // 语音识别相关
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    // Settings Activity启动器
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置有变化，刷新界面显示
            updateTemperatureDisplay()
        }
    }
    
    // Intent方式语音识别启动器
    private val voiceRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                Log.d("VoiceRecognition", "Intent recognition result: $recognizedText")
                
                // 将识别结果填入搜索框并执行搜索
                binding.searchEditText.setText(recognizedText)
                
                // 自动触发搜索
                if (recognizedText.isNotBlank()) {
                    showSearchResults()
                    viewModel.searchCities(recognizedText)
                    Toast.makeText(this, "Searching for \"$recognizedText\"", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d("VoiceRecognition", "Intent voice recognition cancelled or failed")
        }
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MICROPHONE_PERMISSION_REQUEST_CODE = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化定位管理器
        locationManager = com.example.weatherdemo.LocationManager(this)
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        
        setupSystemUI()
        setupViewModel()
        setupUI()
        setupObservers()
        setupBackPress()
        
        // 请求定位权限并加载数据
        requestLocationPermissionAndLoadData()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume - 自动刷新城市列表")
        
        // 刷新用户城市列表
        viewModel.loadSavedCities()
        
        // 从数据库加载定位城市（如果有的话）
        viewModel.loadLocationCityFromDatabase()
    }
    
    private fun setupSystemUI() {
        // 现代化的状态栏设置方式
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = 
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }
    
    private fun setupViewModel() {
        val application = application as WeatherApplication
        val factory = WeatherViewModelFactory(application.repository)
        viewModel = ViewModelProvider(this, factory)[WeatherViewModel::class.java]
    }
    
    private fun setupUI() {
        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshAllWeatherData()
        }
        
        // 设置下拉刷新的颜色主题（适配深色主题）
        binding.swipeRefreshLayout.setColorSchemeColors(
            resources.getColor(android.R.color.white, theme),
            resources.getColor(android.R.color.holo_blue_light, theme),
            resources.getColor(android.R.color.holo_green_light, theme),
            resources.getColor(android.R.color.holo_orange_light, theme)
        )
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            resources.getColor(android.R.color.darker_gray, theme)
        )
        
        // 设置城市列表RecyclerView
        cityListAdapter = CityListAdapter(
            this, // 传递context
            onCityClick = { weatherData ->
                // 点击城市，跳转到详情页面（从城市列表进入）
                val intent = Intent(this, CityDetailActivity::class.java).apply {
                    putExtra(CityDetailActivity.EXTRA_CITY_NAME, weatherData.cityName)
                    putExtra(CityDetailActivity.EXTRA_FROM_SEARCH, false)  // 从城市列表进入
                }
                startActivity(intent)
                // 添加页面切换动画
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            },
            onCityDelete = { weatherData, position ->
                // 删除城市确认对话框
                showDeleteConfirmDialog(weatherData, position)
            }
        )
        
        binding.cityListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = cityListAdapter
        }
        
        // 设置搜索结果RecyclerView
        searchResultAdapter = SearchResultAdapter { searchResult ->
            // 点击搜索结果，跳转到详情页面（从搜索进入）
            val intent = Intent(this, CityDetailActivity::class.java).apply {
                putExtra(CityDetailActivity.EXTRA_CITY_NAME, searchResult.name)
                putExtra(CityDetailActivity.EXTRA_FROM_SEARCH, true)  // 从搜索进入
            }
            startActivity(intent)
            // 添加页面切换动画
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            
            // 隐藏搜索结果
            hideSearchResults()
            binding.searchEditText.text.clear()
        }
        
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchResultAdapter
        }
        
        // 设置搜索功能
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    showSearchResults()
                    viewModel.searchCities(query)
                } else {
                    hideSearchResults()
                    viewModel.clearSearchResults()
                }
            }
        })
        
        // 菜单按钮点击事件
        binding.menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }
        
        // AI助手按钮点击事件
        binding.aiButton.setOnClickListener {
            val intent = Intent(this, AIWeatherAssistantActivity::class.java)
            startActivity(intent)
            // 添加页面切换动画
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        
        // 麦克风按钮点击事件
        binding.micButton.setOnClickListener {
            startVoiceRecognition()
        }
    }
    
    private fun setupObservers() {
        // 观察搜索结果
        viewModel.searchResults.observe(this) { results ->
            searchResultAdapter.updateResults(results)
        }
        
        // 观察错误消息
        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
        
        // 观察保存的城市列表
        viewModel.savedCities.observe(this) { cities ->
            // 获取当前定位城市，确保更新时包含完整信息
            val locationCity = viewModel.locationCity.value
            updateCityList(cities, locationCity)
        }
        
        // 观察定位城市数据
        viewModel.locationCity.observe(this) { locationCity ->
            // 获取当前用户城市列表，确保更新时包含完整信息
            val savedCities = viewModel.savedCities.value ?: emptyList()
            updateCityList(savedCities, locationCity)
            
            // 更新小部件
            locationCity?.let {
                Log.d("MainActivity", "定位城市数据更新，同步更新小部件")
                WeatherWidgetProvider.updateAllWidgets(this)
            }
        }
        
        // 观察首页刷新状态
        viewModel.isMainRefreshing.observe(this) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }
    }
    
    private fun updateCityList(savedCities: List<WeatherData>, locationCity: WeatherData? = null) {
        val allCities = mutableListOf<WeatherData>()
        
        Log.d("MainActivity", "更新城市列表 - 用户城市: ${savedCities.size}个, 定位城市: ${locationCity?.cityName ?: "无"}")
        
        // 先添加定位城市（如果有）
        locationCity?.let { 
            allCities.add(it)
            Log.d("MainActivity", "添加定位城市: ${it.cityName} (isLocationCity=${it.isLocationCity})")
        }
        
        // 再添加用户保存的城市（确保排除定位城市）
        val userCities = savedCities.filter { !it.isLocationCity }
        allCities.addAll(userCities)
        Log.d("MainActivity", "添加${userCities.size}个用户城市: ${userCities.map { it.cityName }}")
        
        Log.d("MainActivity", "最终城市列表: ${allCities.map { "${it.cityName}(定位:${it.isLocationCity})" }}")
        cityListAdapter.updateCities(allCities)
        
        // 控制空状态显示
        if (allCities.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.cityListRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.cityListRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun loadDefaultCities() {
        // 加载用户已保存的城市列表（而不是模拟数据）
        viewModel.loadSavedCities()
    }
    
    private fun showSearchResults() {
        binding.searchResultsContainer.visibility = View.VISIBLE
        binding.cityListRecyclerView.visibility = View.GONE
    }
    
    private fun hideSearchResults() {
        binding.searchResultsContainer.visibility = View.GONE
        binding.cityListRecyclerView.visibility = View.VISIBLE
        binding.searchEditText.clearFocus()
    }
    
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // 如果在编辑模式，先退出编辑模式
                    cityListAdapter.isInEditMode() -> {
                        toggleEditMode()
                    }
                    // 如果显示搜索结果，隐藏搜索结果
                    binding.searchResultsContainer.visibility == View.VISIBLE -> {
                        hideSearchResults()
                        binding.searchEditText.text.clear()
                    }
                    // 否则退出应用
                    else -> {
                        finish()
                    }
                }
            }
        })
    }
    
    private fun refreshWeatherData() {
        // 刷新所有城市的天气数据
        viewModel.loadSavedCities()
    }
    
    private fun refreshAllWeatherData() {
        Log.d("MainActivity", "用户触发下拉刷新")
        viewModel.refreshAllCitiesData()
    }
    
    /**
     * 显示弹出菜单
     */
    private fun showPopupMenu(anchorView: View) {
        val popup = ModernPopupMenu(this, anchorView)
        
        popup.setOnMenuItemClickListener(object : ModernPopupMenu.OnMenuItemClickListener {
            override fun onEditModeClick() {
                toggleEditMode()
            }
            
            override fun onSettingsClick() {
                // 启动设置页面
                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                settingsLauncher.launch(intent)
            }
            
            override fun onAboutClick() {
                showAboutDialog()
            }
        })
        
        // 传递当前编辑模式状态
        popup.show(cityListAdapter.isInEditMode())
    }
    
    /**
     * 切换编辑模式
     */
    private fun toggleEditMode() {
        val isEditMode = !cityListAdapter.isInEditMode()
        
        if (isEditMode) {
            enterEditMode()
        } else {
            exitEditMode()
        }
    }
    
    /**
     * 进入编辑模式 - 优化动画效果，增加弹性和平滑度
     */
    private fun enterEditMode() {
        // 第一步：标题文字渐变过渡
        binding.titleText.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.titleText.text = "编辑列表"
                binding.titleText.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
        
        // 第二步：优化搜索栏隐藏动画 - 增加弹性和平滑度
        val searchContainer = binding.searchEditText.parent as View
        val searchAnimator = searchContainer.animate()
            .alpha(0f)
            .translationY(-30f)  // 增加移动距离，让过渡更明显
            .scaleY(0.85f)       // 减少缩放程度，避免过于剧烈
            .setDuration(350)    // 延长动画时间，让过渡更平滑
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))  // 使用减速插值器
            .withEndAction {
                searchContainer.visibility = View.GONE
                hideSearchResults()
                binding.searchEditText.text.clear()
            }
        
        // 第三步：背景色过渡动画 - 稍微延长时间配合搜索栏动画
        val backgroundAnimator = android.animation.ValueAnimator.ofArgb(
            resources.getColor(R.color.black, theme),
            resources.getColor(R.color.edit_mode_background, theme)
        ).apply {
            duration = 500  // 延长背景动画时间
            addUpdateListener { animator ->
                binding.root.setBackgroundColor(animator.animatedValue as Int)
            }
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        
        // 第四步：空状态文本更新动画
        binding.emptyStateText.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.emptyStateText.apply {
                    text = "编辑模式\n点击 🗑️ 删除城市"
                    setTextColor(resources.getColor(R.color.edit_mode_accent, theme))
                }
                binding.emptyStateText.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
        
        // 执行动画序列
        searchAnimator.start()
        backgroundAnimator.start()
        
        // 延迟启动RecyclerView编辑模式，等待搜索栏隐藏完成 - 增加延迟时间
        binding.cityListRecyclerView.postDelayed({
            cityListAdapter.setEditMode(true)
        }, 400)  // 从250ms增加到400ms，让列表动画更自然
    }
    
    /**
     * 退出编辑模式 - 优化动画效果，增加弹性和平滑度
     */
    private fun exitEditMode() {
        // 第一步：立即关闭列表编辑模式
        cityListAdapter.setEditMode(false)
        
        // 第二步：标题文字渐变过渡
        binding.titleText.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.titleText.text = "天气"
                binding.titleText.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
        
        // 第三步：背景色过渡动画 - 延长时间配合搜索栏动画
        val backgroundAnimator = android.animation.ValueAnimator.ofArgb(
            resources.getColor(R.color.edit_mode_background, theme),
            resources.getColor(R.color.black, theme)
        ).apply {
            duration = 500  // 延长背景动画时间
            addUpdateListener { animator ->
                binding.root.setBackgroundColor(animator.animatedValue as Int)
            }
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        
        // 第四步：优化搜索栏恢复动画 - 增加弹性效果
        val searchContainer = binding.searchEditText.parent as View
        searchContainer.visibility = View.VISIBLE
        searchContainer.alpha = 0f
        searchContainer.translationY = -30f  // 与进入动画一致的距离
        searchContainer.scaleY = 0.85f      // 与进入动画一致的缩放
        
        val searchAnimator = searchContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleY(1f)
            .setDuration(400)  // 稍微延长恢复动画时间
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))  // 添加轻微的弹性效果
            .setStartDelay(150)  // 延迟让背景动画先开始
        
        // 第五步：空状态文本更新动画
        binding.emptyStateText.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.emptyStateText.apply {
                    text = "搜索并添加城市\n查看天气信息"
                    setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                }
                binding.emptyStateText.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
        
        // 执行动画序列
        backgroundAnimator.start()
        searchAnimator.start()
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(weatherData: WeatherData, position: Int) {
        Log.d("MainActivity", "准备删除城市：${weatherData.cityName}, ID：${weatherData.id}, 位置：$position")
        
        // 使用现代化自定义对话框
        val modernDialog = ModernDeleteDialog(this, weatherData.cityName)
        modernDialog.setOnDeleteConfirmListener(object : ModernDeleteDialog.OnDeleteConfirmListener {
            override fun onConfirmDelete() {
                Log.d("MainActivity", "用户确认删除，开始删除过程...")
                
                // 添加删除动画效果
                val cityIndex = cityListAdapter.getCityPosition(weatherData)
                if (cityIndex != -1) {
                    cityListAdapter.removeCity(cityIndex)
                }
                
                // 从数据库删除
                Log.d("MainActivity", "调用ViewModel删除数据库记录")
                viewModel.deleteWeatherData(weatherData)
                
                // 删除成功提示已移除
                Log.d("MainActivity", "删除操作完成")
            }
            
            override fun onCancel() {
                Log.d("MainActivity", "用户取消删除")
            }
        })
        
        modernDialog.show()
    }
    
    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // 设置对话框背景透明
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 设置确定按钮点击事件
        dialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun requestLocationPermissionAndLoadData() {
        if (locationManager.hasLocationPermission()) {
            // 已有权限，直接获取定位
            loadLocationWeather()
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        
        // 同时加载用户已保存的城市
        viewModel.loadSavedCities()
    }
    
    private fun loadLocationWeather() {
        Log.d("MainActivity", "开始获取定位城市天气")
        
        lifecycleScope.launch {
            try {
                val result = locationManager.getCurrentLocation()
                result.fold(
                    onSuccess = { cityName ->
                        Log.d("MainActivity", "定位成功，城市：$cityName")
                        // 获取定位城市的天气数据
                        viewModel.loadLocationWeatherData(cityName)
                    },
                    onFailure = { exception ->
                        Log.e("MainActivity", "定位失败：${exception.message}")
                        // 定位失败，使用默认城市
                        viewModel.loadLocationWeatherData("Beijing")
                    }
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "定位异常：${e.message}")
                viewModel.loadLocationWeatherData("Beijing")
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限授予，获取定位
                    loadLocationWeather()
                } else {
                    // 权限拒绝，使用默认城市
                    Log.d("MainActivity", "定位权限被拒绝，使用默认城市")
                    viewModel.loadLocationWeatherData("Beijing")
                }
                // 加载用户已保存的城市
                viewModel.loadSavedCities()
            }
            MICROPHONE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted, please tap the voice button again", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission required for voice search", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 更新温度显示
     */
    private fun updateTemperatureDisplay() {
        // 刷新城市列表适配器以更新温度显示格式
        cityListAdapter.updateTemperatureUnit()
        cityListAdapter.notifyDataSetChanged()
    }
    
    override fun finish() {
        super.finish()
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
    
    // =====================================================================================
    // 🎤 语音识别功能实现
    // =====================================================================================
    
    /**
     * 开始语音识别 - 改进版本，支持更多设备
     */
    private fun startVoiceRecognition() {
        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            // 请求麦克风权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST_CODE
            )
            return
        }
        
        // 详细的设备和语音服务检查
        Log.d("VoiceRecognition", "=== 语音识别设备检查 ===")
        Log.d("VoiceRecognition", "设备型号: ${android.os.Build.MODEL}")
        Log.d("VoiceRecognition", "设备厂商: ${android.os.Build.MANUFACTURER}")
        Log.d("VoiceRecognition", "Android版本: ${android.os.Build.VERSION.RELEASE}")
        
        // 检查语音识别可用性
        val isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        Log.d("VoiceRecognition", "SpeechRecognizer.isRecognitionAvailable(): $isRecognitionAvailable")
        
        // 检查可用的语音识别服务
        try {
            val packageManager = packageManager
            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val activities = packageManager.queryIntentActivities(voiceIntent, PackageManager.MATCH_DEFAULT_ONLY)
            Log.d("VoiceRecognition", "可用的语音识别服务数量: ${activities.size}")
            activities.forEach { resolveInfo ->
                Log.d("VoiceRecognition", "语音服务: ${resolveInfo.activityInfo.packageName}")
            }
            
            // 如果有可用的语音识别服务，就尝试使用
            if (activities.isNotEmpty()) {
                Log.d("VoiceRecognition", "检测到语音识别服务，尝试启动...")
                tryStartVoiceRecognition()
                return
            }
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "检查语音服务时出错", e)
        }
        
        // 如果标准检查失败，尝试使用Intent方式作为后备方案
        if (!isRecognitionAvailable) {
            Log.d("VoiceRecognition", "标准检查失败，尝试Intent后备方案...")
            tryIntentBasedVoiceRecognition()
            return
        }
        
        // 标准方式启动
        tryStartVoiceRecognition()
    }
    
    /**
     * 尝试启动标准语音识别
     */
    private fun tryStartVoiceRecognition() {
        // 如果正在监听，停止监听
        if (isListening) {
            stopVoiceRecognition()
            return
        }
        
        // 开始语音识别
        try {
            initializeSpeechRecognizer()
            startListening()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start speech recognition", e)
            // 如果标准方式失败，尝试Intent方式
            Log.d("VoiceRecognition", "标准方式失败，尝试Intent方式...")
            tryIntentBasedVoiceRecognition()
        }
    }
    
    /**
     * Intent方式语音识别（后备方案）
     */
    private fun tryIntentBasedVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Please say the city name in English")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            
            // 检查是否有应用可以处理这个Intent
            if (intent.resolveActivity(packageManager) != null) {
                Log.d("VoiceRecognition", "启动Intent方式语音识别...")
                voiceRecognitionLauncher.launch(intent)
                Toast.makeText(this, "Please speak in English...", Toast.LENGTH_SHORT).show()
            } else {
                // 最终失败，显示友好的错误信息
                showVoiceRecognitionUnavailable()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Intent方式也失败", e)
            showVoiceRecognitionUnavailable()
        }
    }
    
    /**
     * 显示语音识别不可用的友好提示
     */
    private fun showVoiceRecognitionUnavailable() {
        val message = when (android.os.Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> "请在系统设置中启用Google语音服务，或尝试安装Google应用"
            "huawei" -> "请在设置中启用语音输入服务"
            "oppo", "vivo" -> "请检查语音输入设置，确保已启用"
            else -> "语音识别不可用，请检查系统语音服务设置"
        }
        
        AlertDialog.Builder(this)
            .setTitle("语音识别不可用")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                try {
                    // 尝试打开语音设置页面
                    val settingsIntent = Intent().apply {
                        action = "com.android.settings.TTS_SETTINGS"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (settingsIntent.resolveActivity(packageManager) != null) {
                        startActivity(settingsIntent)
                    } else {
                        // 如果具体设置页面不可用，打开通用设置
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 初始化语音识别器
     */
    private fun initializeSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceRecognition", "准备好接收语音")
                    isListening = true
                    updateMicButtonState(true)
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d("VoiceRecognition", "开始说话")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // 可以在这里添加音量指示器
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // 接收到音频数据
                }
                
                override fun onEndOfSpeech() {
                    Log.d("VoiceRecognition", "说话结束")
                    isListening = false
                    updateMicButtonState(false)
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timeout"
                        else -> "Unknown error"
                    }
                    
                    Log.e("VoiceRecognition", "Speech recognition error: $errorMessage (code: $error)")
                    isListening = false
                    updateMicButtonState(false)
                    
                    // 对于"没有匹配结果"的错误，给出更友好的提示
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@MainActivity, "Please speak clearly in English and try again", Toast.LENGTH_SHORT).show()
                    } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                        // 忽略客户端错误（通常是用户主动停止）
                        Toast.makeText(this@MainActivity, "Speech recognition failed: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        Log.d("VoiceRecognition", "Recognition result: $recognizedText")
                        
                        // 将识别结果填入搜索框并执行搜索
                        binding.searchEditText.setText(recognizedText)
                        
                        // 自动触发搜索
                        if (recognizedText.isNotBlank()) {
                            showSearchResults()
                            viewModel.searchCities(recognizedText)
                            Toast.makeText(this@MainActivity, "Searching for \"$recognizedText\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    isListening = false
                    updateMicButtonState(false)
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // 部分结果，可以用于实时显示识别过程
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        Log.d("VoiceRecognition", "部分结果: ${matches[0]}")
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // 其他事件
                }
            })
        }
    }
    
    /**
     * 开始监听
     */
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // 使用英文识别
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US") // 优先使用英文
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true) // 只返回首选语言结果
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Please say the city name in English")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // 增加结果数量，提高识别准确性
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            // 添加英文识别优化参数
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // 使用在线识别获得更好效果
        }
        
        speechRecognizer?.startListening(intent)
        Toast.makeText(this, "Please speak in English...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 停止语音识别
     */
    private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        isListening = false
        updateMicButtonState(false)
    }
    
    /**
     * 更新麦克风按钮状态
     */
    private fun updateMicButtonState(listening: Boolean) {
        if (listening) {
            // 正在监听状态 - 改变按钮外观
            binding.micButton.setColorFilter(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            binding.micButton.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .start()
        } else {
            // 正常状态
            binding.micButton.clearColorFilter()
            binding.micButton.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .start()
        }
    }
    
    /**
     * 页面销毁时清理语音识别器
     */
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}