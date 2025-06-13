package com.example.weatherdemo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.utils.MPPointF

/**
 * 自定义LineChart - 解决X轴标签拖动时位置不准确问题
 * 核心思想：标签作为图表内容的一部分，与数据点完全同步移动
 */
class CustomLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LineChart(context, attrs, defStyle) {

    private var timeLabels: List<String> = emptyList()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 28f // 10f * density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    /**
     * 设置时间标签数据
     * @param labels 与数据点对应的时间标签列表
     */
    fun setTimeLabels(labels: List<String>) {
        timeLabels = labels
        invalidate() // 触发重绘
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        drawCustomTimeLabels(canvas)
    }

    /**
     * 手动绘制时间标签
     * 标签位置通过Transformer精确计算，确保与数据点完全对应
     * 🔧 修复：使用ViewPortHandler获取图表内容区域的实际底部，避免与图例重叠
     */
    private fun drawCustomTimeLabels(canvas: Canvas) {
        if (timeLabels.isEmpty() || data == null) return

        // 🔧 关键修复：标签显示在图表外部下方，不与图表内容重叠
        val contentBottom = viewPortHandler.contentBottom()
        val labelY = contentBottom + 30f // 标签Y位置，在图表内容区域底部下方30像素

        timeLabels.forEachIndexed { index, label ->
            // 🔧 关键修复：只绘制非空标签，避免显示不必要的标签
            if (label.isNotEmpty()) {
                // 使用MPAndroidChart的Transformer计算精确的像素位置
                val points = floatArrayOf(index.toFloat(), 0f)
                getTransformer(data.getDataSetByIndex(0).axisDependency)
                    .pointValuesToPixel(points)

                val labelX = points[0]

                // 确保标签在可见区域内才绘制
                if (labelX >= viewPortHandler.contentLeft() && 
                    labelX <= viewPortHandler.contentRight()) {
                    
                    canvas.drawText(label, labelX, labelY, labelPaint)
                }
            }
        }
    }
} 