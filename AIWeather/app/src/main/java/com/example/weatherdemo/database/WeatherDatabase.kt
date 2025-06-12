package com.example.weatherdemo.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.weatherdemo.data.WeatherData
import com.example.weatherdemo.data.HourlyWeatherData
import com.example.weatherdemo.data.AstroData
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WeatherData::class, HourlyWeatherData::class, AstroData::class],
    version = 4,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    
    abstract fun weatherDao(): WeatherDao
    
    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null
        
        fun getDatabase(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "weather_database"
                )
                .fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 创建临时表
                database.execSQL("""
                    CREATE TABLE weather_data_temp (
                        id TEXT NOT NULL PRIMARY KEY,
                        cityName TEXT NOT NULL,
                        date TEXT NOT NULL,
                        temperature INTEGER NOT NULL,
                        temperatureMin INTEGER NOT NULL,
                        temperatureMax INTEGER NOT NULL,
                        weather TEXT NOT NULL,
                        weatherDescription TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        humidity INTEGER NOT NULL,
                        pressure INTEGER NOT NULL,
                        windSpeed REAL NOT NULL,
                        windDegree INTEGER NOT NULL,
                        visibility INTEGER NOT NULL,
                        uvIndex REAL NOT NULL,
                        feelsLike INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isLocationCity INTEGER NOT NULL
                    )
                """)

                // 2. 为临时表创建唯一索引
                database.execSQL("CREATE UNIQUE INDEX index_weather_data_temp_cityName ON weather_data_temp(cityName)")

                // 3. 复制数据到临时表，只保留每个城市最新的数据
                database.execSQL("""
                    INSERT OR REPLACE INTO weather_data_temp 
                    SELECT * FROM weather_data w1
                    WHERE timestamp = (
                        SELECT MAX(timestamp)
                        FROM weather_data w2
                        WHERE w2.cityName = w1.cityName
                    )
                """)

                // 4. 删除原表
                database.execSQL("DROP TABLE weather_data")

                // 5. 重命名临时表为正式表
                database.execSQL("ALTER TABLE weather_data_temp RENAME TO weather_data")
            }
        }
    }
} 