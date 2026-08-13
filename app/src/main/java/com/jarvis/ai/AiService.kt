package com.jarvis.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class AIProvider {
    OPENAI, CLAUDE, GEMINI, GROQ
}

class AIService {

    private val client = OkHttpClient()

    fun sendMessage(
        provider: AIProvider,
        apiKey: String,
        prompt: String,
        callback: (Result<String>) -> Unit
    ) {
        if (apiKey.isBlank()) {
            callback(Result.failure(Exception("Kein API Key hinterlegt! Bitte in den Einstellungen eingeben.")))
            return
        }

        when (provider) {
            AIProvider.GROQ -> sendGroqRequest(apiKey, prompt, callback)
            AIProvider.OPENAI -> sendOpenAIRequest(apiKey, prompt, callback)
            AIProvider.CLAUDE -> sendClaudeRequest(apiKey, prompt, callback)
            AIProvider.GEMINI -> sendGeminiRequest(apiKey, prompt, callback)
        }
    }

    private fun sendGroqRequest(apiKey: String, prompt: String, callback: (Result<String>) -> Unit) {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile") // Kostenloses High-End Modell
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, callback) { responseJson ->
            responseJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun sendOpenAIRequest(apiKey: String, prompt: String, callback: (Result<String>) -> Unit) {
        val url = "https://api.openai.com/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, callback) { responseJson ->
            responseJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun sendClaudeRequest(apiKey: String, prompt: String, callback: (Result<String>) -> Unit) {
        val url = "https://api.anthropic.com/v1/messages"
        val json = JSONObject().apply {
            put("model", "claude-3-haiku-20240307")
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, callback) { responseJson ->
            responseJson.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun sendGeminiRequest(apiKey: String, prompt: String, callback: (Result<String>) -> Unit) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        executeRequest(request, callback) { responseJson ->
            responseJson.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun executeRequest(
        request: Request,
        callback: (Result<String>) -> Unit,
        parseResponse: (JSONObject) -> String
    ) {
        Thread {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    callback(Result.success(parseResponse(json)))
                } else {
                    val errorDetail = body ?: response.message
                    callback(Result.failure(Exception("(${response.code}): $errorDetail")))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }
}
