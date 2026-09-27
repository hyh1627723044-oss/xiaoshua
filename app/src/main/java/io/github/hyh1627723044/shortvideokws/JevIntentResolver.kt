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
import java.util.Locale
import java.util.concurrent.TimeUnit

// command == null means NO_ACTION. note is a short status for the UI, never containing credentials.
data class JevDecision(val command: Command?, val note: String)

// JEV `choice` decision over a fixed label set. Anything unexpected resolves to NO_ACTION.
class JevIntentResolver(base: OkHttpClient = OkHttpClient(), connectMs: Long = 3_000, callMs: Long = 5_000) {
    companion object {
        const val NO_ACTION = "NO_ACTION"
        const val QUESTION = "intent"
        val CRITERIA: Map<String, String> = linkedMapOf(
            Command.NEXT.name to "切到下一条视频",
            Command.PREVIOUS.name to "回到上一条视频",
            Command.PLAY.name to "继续播放当前视频",
            Command.PAUSE.name to "暂停当前视频",
            Command.LIKE.name to "给当前视频点赞",
            Command.COMMENTS.name to "打开当前视频的评论区",
            Command.CLOSE_COMMENTS.name to "关闭已经打开的评论区",
            Command.FAVORITE.name to "收藏当前视频（取消收藏不属于此项）",
            Command.STOP.name to "停止语音控制",
            NO_ACTION to "不执行任何操作：否定句、多个操作、闲聊、像视频里的对白、与操作无关或意图不明确",
        )
        const val INSTRUCTIONS = "这是用户对着短视频应用说的一句话（语音转写，可能有错字）。" +
            "只在用户明确要求立即执行某一个操作时选择该操作。" +
            "否定句（如“不要点赞”）、同时要求多个操作、闲聊、像是视频里的对白、或意图不明确时，选择 NO_ACTION。"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val http = base.newBuilder()
        .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
        .callTimeout(callMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // Only the final transcript is sent: no audio, screen content or device identifiers.
    fun newCall(endpoint: HttpUrl, apiKey: String, model: String, text: String): CloudCall {
        val question = JSONObject().put("type", "choice").put("instructions", INSTRUCTIONS)
            .put("criteria", JSONObject(CRITERIA as Map<*, *>))
        val body = JSONObject().put("model", model).put("state", text)
            .put("questions", JSONObject().put(QUESTION, question))
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return CloudCall(http.newCall(request))
    }

    // Blocking; run on a worker thread.
    fun execute(call: CloudCall, thresholds: JevThresholds): JevDecision = try {
        call.call.execute().use { parse(it, thresholds) }
    } catch (e: InterruptedIOException) {
        JevDecision(null, if (call.cancelled) "已取消" else "JEV 请求超时")
    } catch (e: IOException) {
        JevDecision(null, if (call.cancelled) "已取消" else "JEV 网络错误")
    }

    fun parse(response: Response, thresholds: JevThresholds): JevDecision {
        if (!response.isSuccessful) return JevDecision(null, when (response.code) {
            401 -> "JEV 鉴权失败"
            402 -> "JEV 余额不足"
            403 -> "JEV 账号不可用"
            400 -> "JEV 请求被拒绝"
            else -> "JEV HTTP ${response.code}"
        })
        val answer = try {
            JSONObject(response.body?.string().orEmpty()).optJSONObject("answers")?.optJSONObject(QUESTION)
        } catch (e: JSONException) {
            return JevDecision(null, "JEV 响应无法解析")
        } catch (e: IOException) {
            return JevDecision(null, "JEV 网络错误")
        } ?: return JevDecision(null, "JEV 响应缺少结果")
        return decide(answer, thresholds)
    }

    private fun decide(answer: JSONObject, thresholds: JevThresholds): JevDecision {
        val label = answer.opt("choice") as? String
        if (label == null || label !in CRITERIA) return JevDecision(null, "JEV 返回未知行为")
        if (label == NO_ACTION) return JevDecision(null, "JEV：不执行")
        val confidence = (answer.opt("confidence") as? Number)?.toDouble()
        if (confidence == null || !confidence.isFinite()) return JevDecision(null, "JEV 缺少置信度")
        val probabilities = answer.optJSONObject("probabilities") ?: return JevDecision(null, "JEV 缺少概率")
        val values = probabilities.keys().asSequence().map { (probabilities.opt(it) as? Number)?.toDouble() }.toList()
        if (values.isEmpty() || values.any { it == null || !it.isFinite() }) return JevDecision(null, "JEV 概率无效")
        val sorted = values.map { it!! }.sortedDescending()
        val top = (probabilities.opt(label) as? Number)?.toDouble()
        if (top == null || top < sorted.first()) return JevDecision(null, "JEV 结果不一致")
        if (sorted.size > 1 && sorted[0] - sorted[1] < JevThresholds.MIN_MARGIN) return JevDecision(null, "JEV：结果不明确")
        val percent = String.format(Locale.ROOT, "%.2f", confidence)
        if (confidence < thresholds.forLabel(label)) return JevDecision(null, "JEV：置信度不足（$label $percent）")
        return JevDecision(Command.valueOf(label), "JEV：$label $percent")
    }
}
