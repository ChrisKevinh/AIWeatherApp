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

/**
 * 天气图表工具类
 * 用于配置和绘制天气相关的数据可视化图表
 */
class WeatherChartHelper {

    companion object {
        
        // 常量定义
        private const val MIN_PRECIPITATION_THRESHOLD = 10 // 最小显示降水概率阈值
        private const val TEMPERATURE_GRANULARITY = 5f // 温度Y轴刻度间隔
        private const val PRECIPITATION_GRANULARITY = 20f // 降水概率Y轴刻度间隔
        private const val BAR_WIDTH = 0.7f // 柱状图宽度
        private const val VISIBLE_X_RANGE_MAX = 12f // 可见X轴范围
        
        // 重点显示的时间点（每3小时）
        private val KEY_HOURS = setOf(0, 3, 6, 9, 12, 15, 18, 21)
        
        /**
         * 配置24小时温度曲线图
         */
        fun setupTemperatureChart(
            context: Context,
            lineChart: LineChart,
            hourlyData: List<HourlyWeatherData>
        ) {
            if (hourlyData.isEmpty()) {
                lineChart.clear()
                return
            }

            // 获取设置管理器
            val settingsManager = SettingsManager.getInstance(context)

            // 数据验证和准备
            val validatedData = validateHourlyData(hourlyData)
            val temperatureEntries = mutableListOf<Entry>()
            val feelsLikeEntries = mutableListOf<Entry>()
            val timeLabels = generateTimeLabels(validatedData)

            // 根据验证后的数据生成图表点 - 支持温度单位转换
            validatedData.forEachIndexed { index, data ->
                val actualTemp = if (settingsManager.isCelsius()) {
                    data.temperature.toFloat()
                } else {
                    settingsManager.celsiusToFahrenheit(data.temperature).toFloat()
                }
                
                val feelsLikeTemp = if (settingsManager.isCelsius()) {
                    data.feelsLike.toFloat()
                } else {
                    settingsManager.celsiusToFahrenheit(data.feelsLike).toFloat()
                }
                
                temperatureEntries.add(Entry(index.toFloat(), actualTemp))
                feelsLikeEntries.add(Entry(index.toFloat(), feelsLikeTemp))
            }

            // 创建温度线
            val temperatureDataSet = LineDataSet(temperatureEntries, "实际温度").apply {
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

            // 创建体感温度线
            val feelsLikeDataSet = LineDataSet(feelsLikeEntries, "体感温度").apply {
                color = ContextCompat.getColor(context, R.color.white_60)
                setCircleColor(ContextCompat.getColor(context, R.color.white_60))
                lineWidth = 2f
                circleRadius = 3f
                setDrawFilled(false)
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            }

            // 设置数据
            val lineData = LineData(temperatureDataSet, feelsLikeDataSet)
            lineChart.data = lineData

            // 配置图表样式 - 传递settingsManager用于格式化
            setupChartStyle(lineChart, timeLabels, settingsManager, temperatureEntries, feelsLikeEntries)
            
            // 刷新图表
            lineChart.invalidate()
        }

        /**
         * 配置降水概率柱状图
         */
        fun setupPrecipitationChart(
            context: Context,
            barChart: BarChart,
            hourlyData: List<HourlyWeatherData>
        ) {
            if (hourlyData.isEmpty()) {
                barChart.clear()
                return
            }

            // 数据验证和准备
            val validatedData = validateHourlyData(hourlyData)
            val precipitationEntries = mutableListOf<BarEntry>()
            val timeLabels = generateTimeLabels(validatedData)
            val colorList = mutableListOf<Int>()

            // 根据验证后的数据生成图表点
            validatedData.forEachIndexed { index, data ->
                // 使用实际的降水概率数据
                val precipChance = maxOf(data.chanceOfRain, data.chanceOfSnow)
                
                // 只有达到最小阈值的降水概率才显示
                val displayValue = if (precipChance >= MIN_PRECIPITATION_THRESHOLD) precipChance.toFloat() else 0f
                precipitationEntries.add(BarEntry(index.toFloat(), displayValue))
                
                // 设置颜色 - 只有达到阈值的才显示颜色
                val color = if (precipChance >= MIN_PRECIPITATION_THRESHOLD) {
                    when {
                        precipChance >= 70 -> ContextCompat.getColor(context, R.color.white)
                        precipChance >= 40 -> ContextCompat.getColor(context, R.color.white_80)
                        precipChance >= 20 -> ContextCompat.getColor(context, R.color.white_60)
                        else -> ContextCompat.getColor(context, R.color.white_40)
                    }
                } else {
                    android.graphics.Color.TRANSPARENT
                }
                colorList.add(color)
            }

            // 创建降水数据集
            val precipitationDataSet = BarDataSet(precipitationEntries, "").apply { // 移除标签避免显示图例
                colors = colorList
                setDrawValues(true)
                valueTextColor = ContextCompat.getColor(context, R.color.white_80)
                valueTextSize = 9f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= MIN_PRECIPITATION_THRESHOLD) "${value.toInt()}%" else ""
                    }
                }
            }

            // 设置数据
            val barData = BarData(precipitationDataSet)
            barData.barWidth = BAR_WIDTH
            barChart.data = barData

            // 配置图表样式
            setupBarChartStyle(barChart, timeLabels)
            
            // 刷新图表
            barChart.invalidate()
        }

        /**
         * 验证和处理小时级数据
         * 确保数据的完整性和正确性
         */
        private fun validateHourlyData(hourlyData: List<HourlyWeatherData>): List<HourlyWeatherData> {
            // 按小时排序，确保数据顺序正确
            val sortedData = hourlyData.sortedBy { it.hour }
            
            // 如果数据不足或过多，保留前24小时
            return if (sortedData.size > 24) {
                sortedData.take(24)
            } else {
                sortedData
            }
        }
        
        /**
         * 生成时间标签
         * 优化标签生成逻辑，确保即使数据不完整也能正确显示
         */
        private fun generateTimeLabels(hourlyData: List<HourlyWeatherData>): List<String> {
            return hourlyData.map { data ->
                // 为重点时间显示完整标签，其他时间显示空字符串
                if (data.hour in KEY_HOURS) {
                    String.format("%d:00", data.hour)
                } else {
                    ""
                }
            }
        }

        /**
         * 配置折线图的通用样式
         */
        private fun setupChartStyle(
            chart: LineChart, 
            timeLabels: List<String>,
            settingsManager: SettingsManager,
            temperatureEntries: List<Entry>,
            feelsLikeEntries: List<Entry>
        ) {
            chart.apply {
                // 基本设置
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                
                // 🔧 新增：启用高亮功能
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = false
                
                // 🔧 新增：设置标记视图（可选）
                // marker = CustomMarkerView(context, R.layout.marker_view)
                
                // 图例设置
                legend.apply {
                    isEnabled = true
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE
                    formSize = 8f
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    xEntrySpace = 16f
                    yEntrySpace = 0f
                    formToTextSpace = 8f
                    yOffset = 10f
                }

                // X轴设置
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    granularity = 1f
                    labelCount = if (timeLabels.isNotEmpty()) timeLabels.size else 8
                    axisMinimum = 0f
                    axisMaximum = if (timeLabels.isNotEmpty()) (timeLabels.size - 1).toFloat() else 23f
                    valueFormatter = IndexAxisValueFormatter(timeLabels)
                    // 确保标签正确显示
                    setAvoidFirstLastClipping(false)
                    setLabelRotationAngle(0f)
                    setCenterAxisLabels(false)
                }

                // 左Y轴设置 - 根据温度单位设置格式化
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    granularity = TEMPERATURE_GRANULARITY
                    
                    // 🔧 优化：自适应Y轴范围
                    if (temperatureEntries.isNotEmpty() || feelsLikeEntries.isNotEmpty()) {
                        val allTemps = temperatureEntries.map { it.y } + feelsLikeEntries.map { it.y }
                        val minTemp = allTemps.minOrNull() ?: 0f
                        val maxTemp = allTemps.maxOrNull() ?: 30f
                        val padding = (maxTemp - minTemp) * 0.1f // 10%的边距
                        axisMinimum = (minTemp - padding).coerceAtLeast(minTemp - 5f)
                        axisMaximum = maxTemp + padding
                    }
                    
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}${settingsManager.getTemperatureUnitSymbol()}"
                        }
                    }
                }

                // 右Y轴禁用
                axisRight.isEnabled = false

                // 设置视口，优化性能
                setVisibleXRangeMaximum(VISIBLE_X_RANGE_MAX)
                moveViewToX(0f)
                
                // 禁用动画，减少闪烁
                animateX(0)
                animateY(0)
            }
        }

        /**
         * 配置柱状图的通用样式
         */
        private fun setupBarChartStyle(chart: BarChart, timeLabels: List<String>) {
            chart.apply {
                // 基本设置
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                
                // 🔧 新增：启用高亮功能
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = false
                
                // 🔧 新增：设置标记视图（可选）
                // marker = CustomMarkerView(context, R.layout.marker_view)
                
                // 禁用图例，避免显示"降水概率"文字
                legend.isEnabled = false

                // X轴设置
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    granularity = 1f
                    labelCount = if (timeLabels.isNotEmpty()) timeLabels.size else 8
                    axisMinimum = 0f
                    axisMaximum = if (timeLabels.isNotEmpty()) (timeLabels.size - 1).toFloat() else 23f
                    valueFormatter = IndexAxisValueFormatter(timeLabels)
                    // 确保标签正确显示
                    setAvoidFirstLastClipping(false)
                    setLabelRotationAngle(0f)
                    setCenterAxisLabels(false)
                }

                // 左Y轴设置
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.parseColor("#CCFFFFFF")
                    textSize = 10f
                    axisMinimum = 0f
                    axisMaximum = 100f
                    granularity = PRECIPITATION_GRANULARITY
                    labelCount = 6 // 显示0%, 20%, 40%, 60%, 80%, 100%
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}%"
                        }
                    }
                }

                // 右Y轴禁用
                axisRight.isEnabled = false

                // 设置视口，优化性能
                setVisibleXRangeMaximum(VISIBLE_X_RANGE_MAX)
                moveViewToX(0f)
                
                // 禁用动画，减少闪烁
                animateX(0)
                animateY(0)
            }
        }
    }
}