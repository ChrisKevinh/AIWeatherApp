package com.example.weatherdemo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "weather_data",
    indices = [Index(value = ["cityName"], unique = true)]
)
data class WeatherData(
    @PrimaryKey
    val id: String,
    val cityName: String,
    val date: String,
    val temperature: Int,
    val temperatureMin: Int,
    val temperatureMax: Int,
    val weather: String,
    val weatherDescription: String,
    val icon: String,
    val humidity: Int,
    val pressure: Int,
    val windSpeed: Double,
    val windDegree: Int,
    val visibility: Int,
    val uvIndex: Double,
    val feelsLike: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isLocationCity: Boolean = false  // 是否为定位城市
)

// 网络请求相关的数据类
data class WeatherResponse(
    @SerializedName("location")
    val location: Location,
    @SerializedName("current")
    val current: Current,
    @SerializedName("forecast")
    val forecast: Forecast
)

data class Location(
    @SerializedName("name")
    val name: String,
    @SerializedName("region")
    val region: String,
    @SerializedName("country")
    val country: String,
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lon")
    val lon: Double,
    @SerializedName("tz_id")
    val tzId: String,
    @SerializedName("localtime")
    val localtime: String
)

data class Current(
    @SerializedName("last_updated")
    val lastUpdated: String,
    @SerializedName("temp_c")
    val tempC: Double,
    @SerializedName("temp_f")
    val tempF: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("condition")
    val condition: Condition,
    @SerializedName("wind_mph")
    val windMph: Double,
    @SerializedName("wind_kph")
    val windKph: Double,
    @SerializedName("wind_degree")
    val windDegree: Int,
    @SerializedName("wind_dir")
    val windDir: String,
    @SerializedName("pressure_mb")
    val pressureMb: Double,
    @SerializedName("pressure_in")
    val pressureIn: Double,
    @SerializedName("precip_mm")
    val precipMm: Double,
    @SerializedName("precip_in")
    val precipIn: Double,
    @SerializedName("humidity")
    val humidity: Int,
    @SerializedName("cloud")
    val cloud: Int,
    @SerializedName("feelslike_c")
    val feelslikeC: Double,
    @SerializedName("feelslike_f")
    val feelslikeF: Double,
    @SerializedName("vis_km")
    val visKm: Double,
    @SerializedName("vis_miles")
    val visMiles: Double,
    @SerializedName("uv")
    val uv: Double,
    @SerializedName("gust_mph")
    val gustMph: Double,
    @SerializedName("gust_kph")
    val gustKph: Double
)

data class Condition(
    @SerializedName("text")
    val text: String,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("code")
    val code: Int
)

data class Forecast(
    @SerializedName("forecastday")
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    @SerializedName("date")
    val date: String,
    @SerializedName("date_epoch")
    val dateEpoch: Long,
    @SerializedName("day")
    val day: Day,
    @SerializedName("astro")
    val astro: Astro,
    @SerializedName("hour")
    val hour: List<Hour>
)

data class Day(
    @SerializedName("maxtemp_c")
    val maxtempC: Double,
    @SerializedName("maxtemp_f")
    val maxtempF: Double,
    @SerializedName("mintemp_c")
    val mintempC: Double,
    @SerializedName("mintemp_f")
    val mintempF: Double,
    @SerializedName("avgtemp_c")
    val avgtempC: Double,
    @SerializedName("avgtemp_f")
    val avgtempF: Double,
    @SerializedName("maxwind_mph")
    val maxwindMph: Double,
    @SerializedName("maxwind_kph")
    val maxwindKph: Double,
    @SerializedName("totalprecip_mm")
    val totalprecipMm: Double,
    @SerializedName("totalprecip_in")
    val totalprecipIn: Double,
    @SerializedName("totalsnow_cm")
    val totalsnowCm: Double,
    @SerializedName("avgvis_km")
    val avgvisKm: Double,
    @SerializedName("avgvis_miles")
    val avgvisMiles: Double,
    @SerializedName("avghumidity")
    val avghumidity: Double,
    @SerializedName("daily_will_it_rain")
    val dailyWillItRain: Int,
    @SerializedName("daily_chance_of_rain")
    val dailyChanceOfRain: Int,
    @SerializedName("daily_will_it_snow")
    val dailyWillItSnow: Int,
    @SerializedName("daily_chance_of_snow")
    val dailyChanceOfSnow: Int,
    @SerializedName("condition")
    val condition: Condition,
    @SerializedName("uv")
    val uv: Double
)

data class Astro(
    @SerializedName("sunrise")
    val sunrise: String,
    @SerializedName("sunset")
    val sunset: String,
    @SerializedName("moonrise")
    val moonrise: String,
    @SerializedName("moonset")
    val moonset: String,
    @SerializedName("moon_phase")
    val moonPhase: String,
    @SerializedName("moon_illumination")
    val moonIllumination: String
)

data class Hour(
    @SerializedName("time_epoch")
    val timeEpoch: Long,
    @SerializedName("time")
    val time: String,
    @SerializedName("temp_c")
    val tempC: Double,
    @SerializedName("temp_f")
    val tempF: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("condition")
    val condition: Condition,
    @SerializedName("wind_mph")
    val windMph: Double,
    @SerializedName("wind_kph")
    val windKph: Double,
    @SerializedName("wind_degree")
    val windDegree: Int,
    @SerializedName("wind_dir")
    val windDir: String,
    @SerializedName("pressure_mb")
    val pressureMb: Double,
    @SerializedName("pressure_in")
    val pressureIn: Double,
    @SerializedName("precip_mm")
    val precipMm: Double,
    @SerializedName("precip_in")
    val precipIn: Double,
    @SerializedName("humidity")
    val humidity: Int,
    @SerializedName("cloud")
    val cloud: Int,
    @SerializedName("feelslike_c")
    val feelslikeC: Double,
    @SerializedName("feelslike_f")
    val feelslikeF: Double,
    @SerializedName("windchill_c")
    val windchillC: Double,
    @SerializedName("windchill_f")
    val windchillF: Double,
    @SerializedName("heatindex_c")
    val heatindexC: Double,
    @SerializedName("heatindex_f")
    val heatindexF: Double,
    @SerializedName("dewpoint_c")
    val dewpointC: Double,
    @SerializedName("dewpoint_f")
    val dewpointF: Double,
    @SerializedName("will_it_rain")
    val willItRain: Int,
    @SerializedName("chance_of_rain")
    val chanceOfRain: Int,
    @SerializedName("will_it_snow")
    val willItSnow: Int,
    @SerializedName("chance_of_snow")
    val chanceOfSnow: Int,
    @SerializedName("vis_km")
    val visKm: Double,
    @SerializedName("vis_miles")
    val visMiles: Double,
    @SerializedName("gust_mph")
    val gustMph: Double,
    @SerializedName("gust_kph")
    val gustKph: Double,
    @SerializedName("uv")
    val uv: Double
)

// 24小时天气数据类（用于数据可视化）
@Entity(tableName = "hourly_weather_data")
data class HourlyWeatherData(
    @PrimaryKey
    val id: String, // cityName_date_hour格式
    val cityName: String,
    val date: String, // yyyy-MM-dd
    val hour: Int, // 0-23
    val timeEpoch: Long,
    val temperature: Double, // 当前温度
    val feelsLike: Double, // 体感温度
    val humidity: Int, // 湿度 %
    val pressure: Double, // 气压 mb
    val windSpeed: Double, // 风速 km/h
    val windDegree: Int, // 风向度数
    val precipitationMm: Double, // 降水量 mm
    val chanceOfRain: Int, // 降雨概率 %
    val chanceOfSnow: Int, // 降雪概率 %
    val cloudCover: Int, // 云量 %
    val visibility: Double, // 能见度 km
    val uvIndex: Double, // 紫外线指数
    val isDayTime: Boolean, // 是否白天
    val weatherDescription: String, // 天气描述
    val weatherIcon: String, // 天气图标
    val timestamp: Long = System.currentTimeMillis()
)

// 天文数据类
@Entity(tableName = "astro_data")
data class AstroData(
    @PrimaryKey
    val id: String, // cityName_date格式
    val cityName: String,
    val date: String,
    val sunrise: String, // 日出时间
    val sunset: String, // 日落时间
    val moonrise: String, // 月升时间
    val moonset: String, // 月落时间
    val moonPhase: String, // 月相
    val moonIllumination: String, // 月亮照明度
    val timestamp: Long = System.currentTimeMillis()
) 