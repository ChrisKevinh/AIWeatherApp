package com.example.weatherdemo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/**
 * 天气小部件后台更新服务
 * 定期更新小部件的天气数据
 */
class WeatherWidgetUpdateService : Service() {
    
    companion object {
        private const val UPDATE_INTERVAL = 30 * 60 * 1000L // 30分钟更新一次
        private const val ACTION_AUTO_UPDATE = "com.example.weatherdemo.widget.AUTO_UPDATE"
        
        /**
         * 启动定期更新
         */
        fun startPeriodicUpdate(context: Context) {
            Log.d("WeatherWidgetService", "启动定期更新")
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WeatherWidgetUpdateService::class.java).apply {
                action = ACTION_AUTO_UPDATE
            }
            
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 设置重复的闹钟，每30分钟更新一次
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL,
                UPDATE_INTERVAL,
                pendingIntent
            )
        }
        
        /**
         * 停止定期更新
         */
        fun stopPeriodicUpdate(context: Context) {
            Log.d("WeatherWidgetService", "停止定期更新")
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WeatherWidgetUpdateService::class.java).apply {
                action = ACTION_AUTO_UPDATE
            }
            
            val pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AUTO_UPDATE -> {
                Log.d("WeatherWidgetService", "执行自动更新")
                updateAllWidgets()
            }
        }
        
        // 返回START_NOT_STICKY，服务被杀死后不自动重启
        return START_NOT_STICKY
    }
    
    /**
     * 更新所有小部件
     */
    private fun updateAllWidgets() {
        try {
            WeatherWidgetProvider.updateAllWidgets(this)
            Log.d("WeatherWidgetService", "小部件自动更新完成")
        } catch (e: Exception) {
            Log.e("WeatherWidgetService", "小部件自动更新失败", e)
        } finally {
            stopSelf()
        }
    }
} 