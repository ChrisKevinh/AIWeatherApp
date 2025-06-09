package com.example.weatherdemo.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class OpenRouterApiService {
    
    // OpenRouter API配置
    private val apiKey = "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"
    private val baseUrl = "https://openrouter.ai/api/v1/chat/completions"
    
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    private val gson = Gson()
    
    /**
     * 向OpenRouter AI发送天气分析请求
     */
    suspend fun getWeatherAnalysis(weatherData: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = createWeatherAnalysisPrompt(weatherData)
            Log.d("OpenRouterAPI", "发送AI分析请求: $prompt")
            
            val requestBody = ChatCompletionRequest(
                model = "deepseek/deepseek-r1-0528-qwen3-8b:free",
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "你是一个专业的天气分析助手。请根据用户提供的天气信息，用中文为用户提供实用的出行建议和生活贴士。回答要专业、实用、温馨，并且结构清晰。"
                    ),
                    ChatMessage(
                        role = "user", 
                        content = prompt
                    )
                ),
                temperature = 0.7,
                max_tokens = 2000
            )
            
            val json = gson.toJson(requestBody)
            Log.d("OpenRouterAPI", "请求JSON: $json")
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://weatherdemo.app")
                .addHeader("X-Title", "Weather Demo AI Assistant")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OpenRouterAPI", "API响应: $responseBody")
                
                if (responseBody != null) {
                    val chatResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                    val aiContent = chatResponse.choices.firstOrNull()?.message?.content
                    val finishReason = chatResponse.choices.firstOrNull()?.finish_reason
                    
                    Log.d("OpenRouterAPI", "AI回复完成原因: $finishReason")
                    Log.d("OpenRouterAPI", "Token使用情况: ${chatResponse.usage}")
                    
                    if (aiContent != null) {
                        // 检查回复是否被截断
                        if (finishReason == "length") {
                            Log.w("OpenRouterAPI", "AI回复可能因为长度限制被截断")
                        }
                        Result.success(aiContent)
                    } else {
                        Result.failure(Exception("AI响应内容为空"))
                    }
                } else {
                    Result.failure(Exception("响应体为空"))
                }
            } else {
                val errorBody = response.body?.string()
                Log.e("OpenRouterAPI", "请求失败: ${response.code} ${response.message}, 错误内容: $errorBody")
                Result.failure(Exception("AI请求失败: ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e("OpenRouterAPI", "API调用异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建天气分析提示词
     */
    private fun createWeatherAnalysisPrompt(weatherData: String): String {
        return """
以下是用户所处区域的详细天气信息，包含今日天气和未来几天的预报：
$weatherData

请你根据这些完整的天气信息，为用户提供以下方面的专业建议：

🌤️ **天气总结**
- 简洁概括今日天气状况的特点
- 总结未来几天的天气趋势和变化
- 突出需要特别关注的天气变化

🌈 **出行规划建议**
- 根据今日天气推荐最适合的出行方式和时间
- 分析未来几天适合出行的日期和注意事项
- 考虑温度变化、降水概率、风力等因素
- 推荐适合的户外或室内活动安排

🏠 **生活贴心提醒** 
- 根据今日天气给出穿衣搭配建议
- 基于未来几天天气变化提供衣物准备建议
- 晾晒衣物的最佳时机和注意事项
- 开窗通风的建议和时间安排
- 其他日常生活小贴士

🌡️ **健康关怀提示**
- 根据今日和未来天气提供健康建议
- 防晒、保暖、补水等提醒
- 特殊天气下的健康注意事项
- 适合的运动建议和时间安排
- 敏感人群的特别提醒

请用温馨、专业的语调回答，内容要实用且具体。每个部分用适当的emoji图标标识，让回答更生动易读。回答要完整，不要中途截断。请重点关注天气变化趋势，给出前瞻性的建议。
        """.trimIndent()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: OpenRouterApiService? = null
        
        fun getInstance(): OpenRouterApiService {
            return INSTANCE ?: synchronized(this) {
                val instance = OpenRouterApiService()
                INSTANCE = instance
                instance
            }
        }
    }
}

// OpenRouter API数据类
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2000
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
) 