package com.example.weatherdemo.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.weatherdemo.R
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.utils.SettingsManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 天气图表工具类 - 全新简化版本
 * 🔄 完全重写：移除所有复杂逻辑，实现简洁稳定的图表显示
 */
class WeatherChartHelper {

    companion object {
        
        // 简化的常量定义
        private const val MIN_PRECIPITATION_DISPLAY = 10 // 最小显示降水概率
        private const val LABEL_INTERVAL_HOURS = 3 // 每3小时显示一个标签
        
        /**
         * 设置24小时温度折线图
         * 🔄 全新实现：使用自定义图表解决标签位置问题
         */
        fun setupTemperatureChart(
            context: Context,
            lineChart: CustomLineChart,
            hourlyData: List<HourlyWeatherData>
        ) {
            if (hourlyData.isEmpty()) {
                lineChart.clear()
                return
            }

            val settingsManager = SettingsManager.getInstance(context)
            
            // 简单数据处理：按时间排序，最多取24个数据点
            val sortedData = hourlyData
                .sortedBy { it.timeEpoch }
                .take(24)
            
            // 生成简洁的时间标签
            val timeLabels = generateSimpleTimeLabels(sortedData)
            
            // 创建温度数据点 - 只保留实际气温
            val temperatureEntries = mutableListOf<Entry>()
            
            sortedData.forEachIndexed { index, data ->
                val actualTemp = if (settingsManager.isCelsius()) {
                    data.temperature.toFloat()
                } else {
                    settingsManager.celsiusToFahrenheit(data.temperature).toFloat()
                }
                
                temperatureEntries.add(Entry(index.toFloat(), actualTemp))
            }

            // 创建实际温度线
            val temperatureDataSet = LineDataSet(temperatureEntries, "气温").apply {
                color = ContextCompat.getColor(context, R.color.white)
                setCircleColor(ContextCompat.getColor(context, R.color.white))
                lineWidth = 3f
                circleRadius = 4f
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(context, R.color.white_30)
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(context, R.color.white_80)
                valueTextSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}°"
                    }
                }
            }

            // 设置数据 - 只包含实际气温曲线
            val lineData = LineData(temperatureDataSet)
            lineChart.data = lineData

            // 🔧 关键修复：将时间标签传递给自定义图表
            lineChart.setTimeLabels(timeLabels)

            // 配置图表样式
            setupLineChartStyle(lineChart, timeLabels, settingsManager)
            
            lineChart.invalidate()
        }

        /**
         * 设置24小时降水概率柱状图
         * 🔄 全新实现：使用自定义图表解决标签位置问题
         */
        fun setupPrecipitationChart(
            context: Context,
            barChart: CustomBarChart,
            hourlyData: List<HourlyWeatherData>
        ) {
            if (hourlyData.isEmpty()) {
                barChart.clear()
                return
            }

            // 简单数据处理：按时间排序，最多取24个数据点
            val sortedData = hourlyData
                .sortedBy { it.timeEpoch }
                .take(24)
            
            // 生成与温度图相同的时间标签
            val timeLabels = generateSimpleTimeLabels(sortedData)
            
            // 创建降水数据点
            val precipitationEntries = mutableListOf<BarEntry>()
            val colorList = mutableListOf<Int>()

            sortedData.forEachIndexed { index, data ->
                val precipChance = maxOf(data.chanceOfRain, data.chanceOfSnow)
                val displayValue = if (precipChance >= MIN_PRECIPITATION_DISPLAY) precipChance.toFloat() else 0f
                
                precipitationEntries.add(BarEntry(index.toFloat(), displayValue))
                
                // 简单的颜色映射
                val color = when {
                    precipChance >= 70 -> ContextCompat.getColor(context, R.color.white)
                    precipChance >= 40 -> ContextCompat.getColor(context, R.color.white_80)
                    precipChance >= 20 -> ContextCompat.getColor(context, R.color.white_60)
                    precipChance >= MIN_PRECIPITATION_DISPLAY -> ContextCompat.getColor(context, R.color.white_40)
                    else -> Color.TRANSPARENT
                }
                colorList.add(color)
            }

            // 创建降水数据集
            val precipitationDataSet = BarDataSet(precipitationEntries, "").apply {
                colors = colorList
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(context, R.color.white_80)
                valueTextSize = 9f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= MIN_PRECIPITATION_DISPLAY) "${value.toInt()}%" else ""
                    }
                }
            }

            // 设置数据
            val barData = BarData(precipitationDataSet)
            barData.barWidth = 0.7f
            barChart.data = barData

            // 🔧 关键修复：将时间标签传递给自定义图表
            barChart.setTimeLabels(timeLabels)

            // 配置图表样式
            setupBarChartStyle(barChart, timeLabels)
            
            barChart.invalidate()
        }

        /**
         * 生成简洁的时间标签
         * 🔧 关键修复：使用索引间隔而不是小时间隔，确保从起始时间开始每3小时显示
         */
        private fun generateSimpleTimeLabels(hourlyData: List<HourlyWeatherData>): List<String> {
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            return hourlyData.mapIndexed { index, data ->
                // 🔧 关键修复：使用索引间隔，从index 0开始，每3个索引显示一个标签
                // 这样确保从起始时间开始，真正的每3小时间隔显示（如：20:00, 23:00, 02:00...）
                val shouldShowLabel = index % LABEL_INTERVAL_HOURS == 0
                
                if (shouldShowLabel) {
                    timeFormatter.format(Date(data.timeEpoch * 1000))
                } else {
                    ""
                }
            }
        }

        /**
         * 配置折线图样式
         * 🔧 关键修复：禁用原生X轴标签，使用自定义Canvas绘制
         */
        private fun setupLineChartStyle(
            chart: CustomLineChart, 
            timeLabels: List<String>,
            settingsManager: SettingsManager
        ) {
            chart.apply {
                // 基本设置
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = false
                
                // 禁用图例
                legend.isEnabled = false

                // X轴设置 - 🔧 关键修复：完全禁用原生标签，使用Canvas手动绘制
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    
                    // 🔧 核心修复：禁用所有原生X轴标签显示
                    setDrawLabels(false)  // 完全禁用X轴标签
                    setDrawAxisLine(false) // 禁用X轴线
                    
                    setAxisMinimum(0f)
                    setAxisMaximum((timeLabels.size - 1).toFloat())
                }

                // Y轴设置 - 固定范围，简单可靠
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    
                    // 简单的固定范围策略
                    if (settingsManager.isCelsius()) {
                        setAxisMinimum(0f)
                        setAxisMaximum(50f)
                        granularity = 5f
                    } else {
                        setAxisMinimum(32f)
                        setAxisMaximum(122f)
                        granularity = 10f
                    }
                    
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}${settingsManager.getTemperatureUnitSymbol()}"
                        }
                    }
                }

                // 禁用右Y轴
                axisRight.isEnabled = false
                
                // 设置可见范围
                setVisibleXRangeMaximum(12f)
                moveViewToX(0f)
            }
        }

        /**
         * 配置柱状图样式
         * 🔧 关键修复：禁用原生X轴标签，与折线图保持一致
         */
        private fun setupBarChartStyle(chart: CustomBarChart, timeLabels: List<String>) {
            chart.apply {
                // 基本设置
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = false
                
                // 禁用图例
                legend.isEnabled = false

                // X轴设置 - 🔧 关键修复：与折线图完全一致，禁用原生标签
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    
                    // 🔧 核心修复：禁用所有原生X轴标签显示
                    setDrawLabels(false)  // 完全禁用X轴标签
                    setDrawAxisLine(false) // 禁用X轴线
                    
                    setAxisMinimum(0f)
                    setAxisMaximum((timeLabels.size - 1).toFloat())
                }

                // Y轴设置 - 简单的0-100%固定范围
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    setAxisMinimum(0f)
                    setAxisMaximum(100f)
                    granularity = 20f
                    labelCount = 6
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}%"
                        }
                    }
                }

                // 禁用右Y轴
                axisRight.isEnabled = false
                
                // 设置可见范围 - 与折线图一致
                setVisibleXRangeMaximum(12f)
                moveViewToX(0f)
            }
        }
    }
}