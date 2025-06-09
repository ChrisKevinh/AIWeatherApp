# 🌤️ WeatherDemo - Android天气应用

> 一个使用Kotlin开发的Android天气应用，作为移动应用开发学习项目

[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-24%2B-orange.svg)](https://android-arsenal.com/api?level=24)

## 📖 项目简介

这是我在大学学习Android开发过程中完成的一个天气应用项目。通过这个项目，我学习和实践了：
- Android开发基础知识
- Kotlin编程语言
- MVVM架构模式
- 网络请求和数据处理
- 数据库操作
- UI设计和用户体验

## ✨ 主要功能

### 🌍 基础天气功能
- **实时天气查询** - 显示当前温度、湿度、风速等信息
- **10天天气预报** - 查看未来10天的天气趋势
- **多城市管理** - 可以添加多个城市，方便切换查看
- **城市搜索** - 支持搜索全球城市天气
- **自动定位** - 获取当前位置的天气信息
- **温度单位切换** - 可以切换摄氏度、华氏度显示

### 🎨 界面设计
- **现代化UI** - 简洁清爽的界面设计
- **流畅动画** - 加入了一些过渡动画效果
- **响应式布局** - 适配不同尺寸的Android设备

### 📊 数据可视化
- **温度趋势图** - 24小时温度变化曲线
- **降水量图表** - 24小时降水概率图

### 🤖 AI功能
- **智能天气分析** - AI助手根据当地天气状况提供天气总结、出行建议、生活贴心提醒、健康关怀
- **切换城市** - 可以选择不同城市进行AI分析，提供智能建议

## 🛠️ 技术栈

### 开发环境
- **Android Studio** - 主要开发工具
- **Kotlin** - 编程语言
- **Gradle** - 构建工具
- **Git** - 版本控制

### 核心技术
- **MVVM架构** - 使用ViewModel和LiveData
- **SQLite数据库（Room框架）** - 本地数据存储
- **OkHttp + Gson** - 网络请求和JSON解析
- **Kotlin协程** - 异步操作处理
- **ViewBinding** - 视图绑定
- **Material Design** - UI设计规范

### 第三方库
```gradle
// UI组件
implementation 'com.google.android.material:material:1.11.0'

// 数据库
implementation 'androidx.room:room-runtime:2.6.1'
implementation 'androidx.room:room-ktx:2.6.1'

// 网络请求
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.google.code.gson:gson:2.10.1'

// 图表
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

// 协程
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

## 🚀 快速开始

### 环境要求
- Android Studio 2022.3.1 或更高版本
- Android SDK API 24 (Android 7.0) 及以上
- JDK 11 或更高版本

### 安装步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/your-username/WeatherDemo.git
   cd WeatherDemo
   ```

2. **配置API密钥** (必须)
   
   获取免费API密钥：
   - 访问 [WeatherAPI.com](https://www.weatherapi.com/) 注册账户
   - 获取免费的API Key
   
   在代码中配置：
   ```kotlin
   // 编辑 app/src/main/java/com/example/weatherdemo/network/WeatherApiService.kt
   private val apiKey = "your_weather_api_key_here" // 替换成你的API密钥
   ```

3. **可选：配置AI功能**
   
   如果要使用AI助手功能：
   - 访问 [OpenRouter.ai](https://openrouter.ai/) 获取API密钥
   - 在 `OpenRouterApiService.kt` 中配置密钥


## 📱 使用说明

### 基本操作
- **查看天气** - 打开应用自动显示当前位置天气
- **搜索城市** - 点击搜索按钮输入城市名称
- **添加城市** - 搜索后点击城市详情页右上角添加按钮添加到列表
- **编辑城市** - 点击右上角菜单按钮，再点击编辑列表进入编辑模式
- **下拉刷新** - 在主界面下拉刷新数据

### 高级功能
- **城市详情** - 点击城市卡片查看详细信息和图表
- **AI助手** - 点击AI按钮体验智能天气分析
- **趋势分析** - 在详情页面查看24小时温度变化

## 📁 项目结构

```
app/src/main/java/com/example/weatherdemo/
├── MainActivity.kt                      # 主界面活动
├── CityDetailActivity.kt               # 城市详情页面
├── AIWeatherAssistantActivity.kt       # AI助手页面
├── SettingsActivity.kt                 # 设置页面
├── WeatherApplication.kt               # 应用程序类
├── LocationManager.kt                  # 位置管理器
├── adapter/                            # 适配器层
│   ├── CityListAdapter.kt              # 城市列表适配器
│   ├── ForecastAdapter.kt              # 天气预报适配器
│   └── SearchResultAdapter.kt          # 搜索结果适配器
├── data/                               # 数据模型
│   └── WeatherData.kt                  # 天气数据模型
├── database/                           # 数据库层
│   ├── WeatherDao.kt                   # 数据访问对象
│   └── WeatherDatabase.kt              # 数据库配置
├── network/                            # 网络请求层
│   ├── WeatherApiService.kt            # 天气API服务
│   └── OpenRouterApiService.kt         # AI API服务
├── repository/                         # 数据仓库层
│   └── WeatherRepository.kt            # 数据管理
├── viewmodel/                          # 视图模型层
│   └── WeatherViewModel.kt             # MVVM架构核心
├── ui/                                 # UI组件
│   └── [UI相关组件]
├── utils/                              # 工具类
│   └── SettingsManager.kt              # 设置管理器
└── widget/                             # 桌面小部件
    └── [小部件相关文件]
```

## 🔧 常见问题

**Q: 应用显示"获取天气数据失败"**
A: 检查API密钥是否正确配置，确保网络连接正常

**Q: 搜索不到城市**
A: 尝试使用英文城市名或检查拼写

**Q: 数据不更新**
A: 下拉刷新或检查网络连接，数据每小时自动更新

**Q: 编译报错**
A: 确保Android Studio版本符合要求，执行 `./gradlew clean` 后重新构建

**Q: 获取不到AI回复**
A: 确保OpenRouter Api Key是否正确配置，前往官网检查ApiKey是否过期

## 📝 学习心得

通过开发这个项目，我学到了：

1. **Android开发基础** - 从零开始学习Activity、Fragment、Adapter等组件
2. **架构设计** - 理解MVVM模式的优势和实现方法
3. **网络编程** - 学会使用OkHttp进行API调用和错误处理
4. **数据库操作** - 掌握Room数据库（SQLite）的使用方法
5. **UI设计** - 学习Material Design设计原则和实现
6. **异步编程** - 使用Kotlin协程处理后台任务
7. **项目管理** - 使用Git进行版本控制和代码管理

## 📊 数据来源

- **天气数据**: [WeatherAPI.com](https://www.weatherapi.com/) - 免费的天气API服务
- **AI服务**: [OpenRouter.ai](https://openrouter.ai/) - AI模型API服务（可选）

## 🔒 隐私说明

- 位置权限仅用于获取当前位置天气
- 不收集任何个人信息
- 所有数据来源于公开的天气API
- AI回复内容来源于OpenRouter提供的DeepSeek R1大模型

## 🤝 贡献

欢迎提出建议和改进：
- 提交Issue报告问题
- 提交Pull Request贡献代码
- 分享使用体验和建议

## 📄 许可证

本项目仅供学习交流使用，请勿用于商业用途。

---

**备注**: 这是一个学习项目，代码可能还有需要改进的地方，欢迎指正和交流！ 
