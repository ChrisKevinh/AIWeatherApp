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

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WeatherViewModel
    private lateinit var cityListAdapter: CityListAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var locationManager: com.example.weatherdemo.LocationManager
    private lateinit var settingsManager: SettingsManager
    
    // Settings Activity启动器
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 设置有变化，刷新界面显示
            updateTemperatureDisplay()
        }
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
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
            // TODO: 实现语音搜索功能
            Toast.makeText(this, "语音搜索功能开发中", Toast.LENGTH_SHORT).show()
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
}