package com.example.weatherdemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as SystemLocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationManager(private val context: Context) {
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    /**
     * 检查定位权限
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取当前位置
     */
    suspend fun getCurrentLocation(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                return@withContext Result.failure(Exception("没有定位权限"))
            }
            
            val location = getLastKnownLocation() ?: getCurrentLocationUpdates()
            
            if (location != null) {
                val cityName = getCityNameFromLocation(location)
                if (cityName != null) {
                    Log.d("LocationManager", "获取到定位城市：$cityName")
                    Result.success(cityName)
                } else {
                    Result.failure(Exception("无法获取城市名称"))
                }
            } else {
                Result.failure(Exception("无法获取位置信息"))
            }
        } catch (e: Exception) {
            Log.e("LocationManager", "获取位置失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取最后已知位置
     */
    @Suppress("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val providers = listOf(
            SystemLocationManager.GPS_PROVIDER,
            SystemLocationManager.NETWORK_PROVIDER
        )
        
        for (provider in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    Log.d("LocationManager", "从 $provider 获取到位置：${location.latitude}, ${location.longitude}")
                    return location
                }
            }
        }
        return null
    }
    
    /**
     * 主动请求位置更新
     */
    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationUpdates(): Location? = suspendCancellableCoroutine { continuation ->
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d("LocationManager", "获取到新位置：${location.latitude}, ${location.longitude}")
                locationManager.removeUpdates(this)
                continuation.resume(location)
            }
            
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        
        val provider = when {
            locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) -> 
                SystemLocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER) -> 
                SystemLocationManager.NETWORK_PROVIDER
            else -> {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
        }
        
        try {
            locationManager.requestLocationUpdates(
                provider,
                1000L, // 1秒
                10f,   // 10米
                locationListener
            )
            
            // 设置超时
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(locationListener)
            }
            
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
    
    /**
     * 根据位置获取城市名称
     */
    private fun getCityNameFromLocation(location: Location): String? {
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // 优先使用 locality（城市），如果没有则使用 subAdminArea（区县）
                address.locality ?: address.subAdminArea ?: address.adminArea
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("LocationManager", "地理编码失败", e)
            null
        }
    }
} 