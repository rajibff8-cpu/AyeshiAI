package com.ayeshiai.assistant.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class ErrorKind { NO_KEY, INVALID_KEY, NETWORK, RATE_LIMIT, MODEL_UNAVAILABLE, BLOCKED, BAD_RESPONSE, SERVER, OTHER }

data class GeminiError(val kind: ErrorKind, val message: String)

sealed class GeminiResult<out T> {
    data class Ok<T>(val value: T) : GeminiResult<T>()
    data class Err(val error: GeminiError) : GeminiResult<Nothing>()
}

/**
 * Minimal Gemini REST client (official Generative Language API, v1beta).
 * The API key is sent only in the x-goog-api-key header (never in the URL) and is never logged.
 */
class GeminiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun listModels(apiKey: String): GeminiResult<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext noKey()
        val req = Request.Builder()
            .url("$BASE/models?pageSize=200")
            .header("x-goog-api-key", apiKey.trim())
            .get()
            .build()
        when (val r = call(req)) {
            is GeminiResult.Err -> r
            is GeminiResult.Ok -> parseModels(r.value)
        }
    }

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<String, String>>,
        temperature: Float
    ): GeminiResult<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext noKey()
        if (!MODEL_RE.matches(model)) {
            return@withContext GeminiResult.Err(
                GeminiError(ErrorKind.MODEL_UNAVAILABLE, "Model name sahi nahi hai. Settings se model chuno.")
            )
        }
        val contents = JSONArray()
        history.takeLast(24).dropWhile { it.first != "user" }.forEach { (role, text) ->
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        if (contents.length() == 0) {
            return@withContext GeminiResult.Err(GeminiError(ErrorKind.OTHER, "Bhejne ke liye koi message nahi hai."))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))
        val req = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(body.toString().toRequestBody(jsonType))
            .build()
        when (val r = call(req)) {
            is GeminiResult.Err -> r
            is GeminiResult.Ok -> parseText(r.value)
        }
    }

    private fun parseModels(raw: String): GeminiResult<List<String>> {
        return try {
            val arr = JSONObject(raw).optJSONArray("models") ?: JSONArray()
            val names = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") ok = true
                    }
                }
                if (ok && name.startsWith("gemini")) names.add(name)
            }
            GeminiResult.Ok(names.sorted())
        } catch (e: JSONException) {
            bad()
        }
    }

    private fun parseText(raw: String): GeminiResult<String> {
        try {
            val root = JSONObject(raw)
            val cands = root.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                val msg = if (reason.isNotEmpty()) "Request safety filter ne block kar di (reason: $reason)."
                else "Model ne koi jawab nahi diya."
                return GeminiResult.Err(GeminiError(ErrorKind.BLOCKED, msg))
            }
            val cand = cands.getJSONObject(0)
            val parts = cand.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            }
            val text = sb.toString().trim()
            if (text.isEmpty()) {
                val fr = cand.optString("finishReason")
                return GeminiResult.Err(
                    GeminiError(ErrorKind.BLOCKED, "Khaali jawab mila" + if (fr.isNotEmpty()) " (finishReason: $fr)." else ".")
                )
            }
            return GeminiResult.Ok(text)
        } catch (e: JSONException) {
            return bad()
        }
    }

    private fun call(req: Request): GeminiResult<String> {
        return try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) GeminiResult.Ok(text) else httpError(resp.code, text)
            }
        } catch (e: UnknownHostException) {
            net("Internet ya DNS problem. Network check karo.")
        } catch (e: SocketTimeoutException) {
            net("Request timeout ho gayi. Network slow hai, dobara try karo.")
        } catch (e: IOException) {
            net("Network error. Connection check karo aur dobara try karo.")
        }
    }

    private fun httpError(code: Int, body: String): GeminiResult.Err {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: JSONException) {
            ""
        }
        val err = when {
            code == 429 -> GeminiError(ErrorKind.RATE_LIMIT, "Rate limit ya quota khatam. Thodi der baad try karo.")
            code == 404 -> GeminiError(
                ErrorKind.MODEL_UNAVAILABLE,
                "Ye model available nahi hai. Settings mein 'Fetch models' se doosra model chuno."
            )
            code == 401 || code == 403 || (code == 400 && msg.contains("API key", ignoreCase = true)) ->
                GeminiError(
                    ErrorKind.INVALID_KEY,
                    "API key invalid hai ya permission nahi hai (HTTP $code). Settings mein key check karo."
                )
            code in 500..599 -> GeminiError(ErrorKind.SERVER, "Gemini server mein problem (HTTP $code). Baad mein try karo.")
            else -> GeminiError(ErrorKind.OTHER, "Gemini error (HTTP $code): ${msg.take(200)}")
        }
        return GeminiResult.Err(err)
    }

    private fun noKey() = GeminiResult.Err(GeminiError(ErrorKind.NO_KEY, "Pehle Settings mein Gemini API key add karo."))
    private fun net(m: String) = GeminiResult.Err(GeminiError(ErrorKind.NETWORK, m))
    private fun bad() = GeminiResult.Err(GeminiError(ErrorKind.BAD_RESPONSE, "Gemini ka response samajh nahi aaya."))

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        val MODEL_RE = Regex("[A-Za-z0-9._-]+")
    }
}
