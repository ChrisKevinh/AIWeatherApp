package com.example.weatherdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.weatherdemo.databinding.ActivitySettingsBinding
import com.example.weatherdemo.utils.SettingsManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        
        // 设置系统窗口
        setupSystemWindows()
        
        // 设置界面
        setupUI()
    }
    
    private fun setupSystemWindows() {
        // 设置系统窗口
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 设置状态栏样式
        window.statusBarColor = getColor(R.color.black)
        window.navigationBarColor = getColor(R.color.black)
        
        // 设置状态栏图标颜色为白色
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
    }
    
    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        // 设置当前选中的温度单位
        updateTemperatureUnitUI()
        
        // 摄氏度选项点击事件
        binding.celsiusOption.setOnClickListener {
            selectTemperatureUnit(SettingsManager.UNIT_CELSIUS)
        }
        
        // 华氏度选项点击事件
        binding.fahrenheitOption.setOnClickListener {
            selectTemperatureUnit(SettingsManager.UNIT_FAHRENHEIT)
        }
        
        // 设置选项的视觉状态
        updateOptionStates()
    }
    
    private fun updateTemperatureUnitUI() {
        val currentUnit = settingsManager.getTemperatureUnit()
        
        binding.celsiusRadio.isChecked = (currentUnit == SettingsManager.UNIT_CELSIUS)
        binding.fahrenheitRadio.isChecked = (currentUnit == SettingsManager.UNIT_FAHRENHEIT)
        
        // 更新选项的视觉状态
        updateOptionStates()
    }
    
    private fun updateOptionStates() {
        val currentUnit = settingsManager.getTemperatureUnit()
        
        // 更新选项的选中状态
        binding.celsiusOption.isSelected = (currentUnit == SettingsManager.UNIT_CELSIUS)
        binding.fahrenheitOption.isSelected = (currentUnit == SettingsManager.UNIT_FAHRENHEIT)
    }
    
    private fun selectTemperatureUnit(unit: String) {
        // 保存设置
        settingsManager.setTemperatureUnit(unit)
        
        // 更新UI
        updateTemperatureUnitUI()
        
        // 设置结果，通知主页面更新显示
        setResult(RESULT_OK)
    }
    
    override fun finish() {
        super.finish()
        // 添加返回动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
} 