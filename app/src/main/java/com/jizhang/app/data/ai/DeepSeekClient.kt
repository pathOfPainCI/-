package com.jizhang.app.data.ai

import com.jizhang.app.domain.classify.AiClassifier
import com.jizhang.app.domain.model.Categories
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ResponseFormat(val type: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat("json_object"),
    val temperature: Double = 0.1,
)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: ChatMessage)

/**
 * DeepSeek 分类客户端（OpenAI 兼容 /chat/completions）。
 * - json_object 结构化输出，分类名在客户端校验（枚举见 Categories.DEFAULT）
 * - 偶发空 content：重试一次；仍失败返回 null（降级为未分类，绝不阻塞记账）
 */
class DeepSeekClient(
    baseUrl: String,
    apiKey: String,
    model: String,
) : AiClassifier {

    private val url = baseUrl.trimEnd('/') + "/chat/completions"
    private val auth = "Bearer " + apiKey
    private val requestModel = model

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun classify(merchant: String?, note: String?): String? {
        val prompt = buildString {
            append("你是个人记账应用的分类助手。根据商户名和备注，从以下固定分类枚举中选择一个：")
            append(Categories.DEFAULT.joinToString("、"))
            append("。只输出 JSON，格式：{\"category\":\"分类名\"}。")
            append("商户名：").append(merchant ?: "未知")
            append("；备注：").append(note ?: "")
        }
        return withContext(Dispatchers.IO) {
            callOnce(prompt) ?: callOnce(prompt) // 重试一次
        }
    }

    private fun callOnce(prompt: String): String? {
        return try {
            val body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(requestModel, listOf(ChatMessage("user", prompt))),
            ).toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Authorization", auth)
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                val parsed = json.decodeFromString(ChatResponse.serializer(), text)
                val content = parsed.choices.firstOrNull()?.message?.content ?: return null
                parseCategory(content)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 解析 {"category":"餐饮"}，校验枚举；非法返回 null */
    private fun parseCategory(content: String): String? {
        return try {
            val obj = json.parseToJsonElement(content).jsonObject
            val name = obj["category"]?.jsonPrimitive?.contentOrNull ?: return null
            if (Categories.isValid(name)) name else null
        } catch (e: Exception) {
            null
        }
    }
}
