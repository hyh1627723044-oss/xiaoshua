package io.github.hyh1627723044.shortvideokws

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface AsrResult {
    data class Text(val text: String) : AsrResult
    data object NoSpeech : AsrResult
    // Reasons are short, fixed strings: never credentials, headers, audio or transcript.
    data class Failure(val reason: String) : AsrResult
}

// Volcengine "极速版" recording recognition: one POST with base64 WAV returns the final text.
class ByteAsrClient(base: OkHttpClient = OkHttpClient(), connectMs: Long = 3_000, callMs: Long = 8_000) {
    companion object {
        const val SUCCESS = 20000000L
        val NO_SPEECH = setOf(20000003L, 45000002L)
        private const val UID = "xiaoshua-android"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http = base.newBuilder()
        .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
        .callTimeout(callMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun newCall(endpoint: HttpUrl, auth: AsrAuth, resourceId: String, wav: ByteArray): CloudCall {
        val body = JSONObject()
            .put("user", JSONObject().put("uid", UID))
            .put("audio", JSONObject().put("data", Base64.getEncoder().encodeToString(wav)).put("format", "wav"))
            .put("request", JSONObject().put("model_name", "bigmodel").put("enable_punc", true).put("enable_itn", true))
        val request = Request.Builder().url(endpoint)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .header("X-Api-Sequence", "-1")
            .apply {
                when (auth) {
                    is AsrAuth.ApiKey -> header("X-Api-Key", auth.key)
                    is AsrAuth.Legacy -> header("X-Api-App-Key", auth.appId).header("X-Api-Access-Key", auth.accessToken)
                }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()
        return CloudCall(http.newCall(request))
    }

    // Blocking; run on a worker thread. CloudCall.cancel() from any thread aborts it.
    fun execute(call: CloudCall): AsrResult = try {
        call.call.execute().use(::parse)
    } catch (e: InterruptedIOException) {
        AsrResult.Failure(if (call.cancelled) "已取消" else "请求超时")
    } catch (e: IOException) {
        AsrResult.Failure(if (call.cancelled) "已取消" else "网络错误")
    }

    fun parse(response: Response): AsrResult {
        val status = response.header("X-Api-Status-Code")?.trim()?.toLongOrNull()
        if (status != null && status in NO_SPEECH) return AsrResult.NoSpeech
        if (status != null && status != SUCCESS) return AsrResult.Failure(describe(status))
        if (!response.isSuccessful) return AsrResult.Failure(
            if (response.code == 401 || response.code == 403) "ASR 鉴权失败" else "ASR HTTP ${response.code}")
        if (status == null) return AsrResult.Failure("ASR 响应缺少状态码")
        return try {
            val text = JSONObject(response.body?.string().orEmpty()).optJSONObject("result")?.optString("text").orEmpty().trim()
            if (text.isEmpty()) AsrResult.NoSpeech else AsrResult.Text(text)
        } catch (e: JSONException) {
            AsrResult.Failure("ASR 响应无法解析")
        } catch (e: IOException) {
            AsrResult.Failure("网络错误")
        }
    }

    private fun describe(status: Long) = when {
        status == 45000001L -> "ASR 参数错误（$status）"
        status == 45000151L -> "ASR 音频格式错误（$status）"
        status == 55000031L -> "ASR 服务繁忙（$status）"
        status in 45000000L..45999999L -> "ASR 请求被拒绝（$status）"
        else -> "ASR 服务错误（$status）"
    }
}
