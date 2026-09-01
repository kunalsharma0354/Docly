package com.nexora.docly.data.ai

import com.nexora.docly.security.Protect
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val role: String, val content: String)

/**
 * Mistral AI chat completions (OpenAI-compatible endpoint).
 * Free tier: 1 req/sec (~60 RPM), 500k tokens/min, 1B tokens/month.
 * A global mutex keeps us under the rate limit (1050ms between calls).
 */
object MistralApi {

    private const val ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
    private const val MODEL = "open-mistral-nemo"

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    fun hasKey(): Boolean = Protect.isReady() && Protect.apiKey().isNotBlank()

    suspend fun chat(messages: List<ChatMessage>, maxTokens: Int = 1500): String {
        mutex.withLock { throttle() }
        var attempt = 0
        while (true) {
            try {
                return request(messages, maxTokens)
            } catch (e: RateLimitedException) {
                attempt++
                if (attempt >= 4) throw e
                delay(3000L * attempt)
            }
        }
    }

    private suspend fun throttle() {
        val waitMs = 1050L - (System.currentTimeMillis() - lastRequestAt)
        if (waitMs > 0) delay(waitMs)
        lastRequestAt = System.currentTimeMillis()
    }

    private fun request(messages: List<ChatMessage>, maxTokens: Int): String {
        val key = Protect.apiKey()
        if (key.isBlank()) throw ApiKeyMissingException()

        val payload = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.3)
            .put("max_tokens", maxTokens)
            .put(
                "messages", JSONArray().apply {
                    messages.forEach { m ->
                        put(JSONObject().put("role", m.role).put("content", m.content))
                    }
                }
            )

        var connection: HttpURLConnection? = null
        try {
            connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (code == 429 || code == 408 || code == 500 || code == 502 || code == 503) {
                throw RateLimitedException()
            }
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).optString("message").ifBlank { body.take(300) }
                }.getOrDefault(body.take(300))
                throw RuntimeException("API error ($code): $detail")
            }

            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            if (content.isEmpty()) throw RuntimeException("API ne khali response diya.")
            return content
        } finally {
            connection?.disconnect()
        }
    }

    private class RateLimitedException : Exception()
    class ApiKeyMissingException : Exception()
}