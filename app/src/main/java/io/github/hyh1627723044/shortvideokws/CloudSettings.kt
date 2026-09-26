package io.github.hyh1627723044.shortvideokws

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl

enum class RecognitionMode { LOCAL, CLOUD }
enum class IntentMode { STRICT, JEV }
enum class AsrAuthMode { API_KEY, LEGACY }

// Everything the cloud pipeline needs, resolved once when listening starts.
data class CloudProfile(
    val asrUrl: HttpUrl,
    val asrAuth: AsrAuth,
    val resourceId: String,
    val intentMode: IntentMode,
    val jevUrl: HttpUrl?,
    val jevKey: String?,
    val jevModel: String,
    val vad: VadSettings,
    val thresholds: JevThresholds,
    val requirePrefix: Boolean,
) {
    override fun toString() = "CloudProfile(${asrUrl.host}, $intentMode)"
}

// Non-secret settings in plain private prefs; keys and tokens go through SecretStore.
object CloudSettings {
    private const val ASR_URL = "asr_url"
    private const val ASR_AUTH = "asr_auth"
    private const val ASR_APP_ID = "asr_app_id"
    private const val ASR_RESOURCE = "asr_resource_id"
    private const val JEV_URL = "jev_url"
    private const val JEV_MODEL = "jev_model"
    private const val JEV_ACCEPT = "jev_accept"
    private const val JEV_LIKE = "jev_like"
    private const val VAD_START = "vad_start"
    private const val VAD_START_MS = "vad_start_ms"
    private const val VAD_END_MS = "vad_end_ms"
    private const val VAD_PRE_ROLL_MS = "vad_pre_roll_ms"
    private const val RECOGNITION = "recognition_mode"
    private const val INTENT = "intent_mode"
    private const val NOTICE = "cloud_notice_accepted"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
        getString(key, null)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    fun recognitionMode(c: Context) = prefs(c).enum(RECOGNITION, RecognitionMode.LOCAL)
    fun setRecognitionMode(c: Context, mode: RecognitionMode) = prefs(c).edit().putString(RECOGNITION, mode.name).apply()
    fun intentMode(c: Context) = prefs(c).enum(INTENT, IntentMode.STRICT)
    fun setIntentMode(c: Context, mode: IntentMode) = prefs(c).edit().putString(INTENT, mode.name).apply()
    fun noticeAccepted(c: Context) = prefs(c).getBoolean(NOTICE, false)
    fun acceptNotice(c: Context) = prefs(c).edit().putBoolean(NOTICE, true).apply()

    // Invalid stored URLs silently fall back to the official endpoint.
    fun asrUrl(c: Context): String = prefs(c).getString(ASR_URL, null)?.takeIf { Endpoints.parse(it) != null } ?: CloudDefaults.ASR_URL
    fun jevUrl(c: Context): String = prefs(c).getString(JEV_URL, null)?.takeIf { Endpoints.parse(it) != null } ?: CloudDefaults.JEV_URL
    fun asrAuthMode(c: Context) = prefs(c).enum(ASR_AUTH, AsrAuthMode.API_KEY)
    fun asrAppId(c: Context) = prefs(c).getString(ASR_APP_ID, "").orEmpty()
    fun resourceId(c: Context) = prefs(c).getString(ASR_RESOURCE, null)?.takeIf { it.isNotBlank() } ?: CloudDefaults.ASR_RESOURCE_ID
    fun jevModel(c: Context) = prefs(c).getString(JEV_MODEL, null)?.takeIf { it.isNotBlank() } ?: CloudDefaults.JEV_MODEL

    fun saveAsr(c: Context, url: String, mode: AsrAuthMode, appId: String, resourceId: String) = prefs(c).edit()
        .putString(ASR_URL, url.trim()).putString(ASR_AUTH, mode.name)
        .putString(ASR_APP_ID, appId.trim()).putString(ASR_RESOURCE, resourceId.trim()).apply()
    fun saveJev(c: Context, url: String, model: String) =
        prefs(c).edit().putString(JEV_URL, url.trim()).putString(JEV_MODEL, model.trim()).apply()
    fun resetAsrUrl(c: Context) = prefs(c).edit().remove(ASR_URL).apply()
    fun resetJevUrl(c: Context) = prefs(c).edit().remove(JEV_URL).apply()

    fun vad(c: Context): VadSettings = prefs(c).run {
        VadSettings.sanitized(getFloat(VAD_START, VadSettings.DEFAULT_START_THRESHOLD), getInt(VAD_START_MS, VadSettings.DEFAULT_START_MS),
            getInt(VAD_END_MS, VadSettings.DEFAULT_END_MS), getInt(VAD_PRE_ROLL_MS, VadSettings.DEFAULT_PRE_ROLL_MS))
    }
    fun saveVad(c: Context, s: VadSettings) = prefs(c).edit().putFloat(VAD_START, s.startThreshold)
        .putInt(VAD_START_MS, s.startMs).putInt(VAD_END_MS, s.endMs).putInt(VAD_PRE_ROLL_MS, s.preRollMs).apply()
    // Stored as Float; round back to two decimals so 0.8f compares as exactly 0.80.
    private fun SharedPreferences.threshold(key: String, default: Double) =
        Math.round(getFloat(key, default.toFloat()) * 100.0) / 100.0
    fun thresholds(c: Context): JevThresholds = prefs(c).run {
        JevThresholds.sanitized(threshold(JEV_ACCEPT, JevThresholds.DEFAULT_ACCEPT), threshold(JEV_LIKE, JevThresholds.DEFAULT_LIKE))
    }
    fun saveThresholds(c: Context, t: JevThresholds) =
        prefs(c).edit().putFloat(JEV_ACCEPT, t.accept.toFloat()).putFloat(JEV_LIKE, t.like.toFloat()).apply()
    fun resetAdvanced(c: Context) = prefs(c).edit().remove(VAD_START).remove(VAD_START_MS).remove(VAD_END_MS)
        .remove(VAD_PRE_ROLL_MS).remove(JEV_ACCEPT).remove(JEV_LIKE).remove(JEV_MODEL).apply()

    fun asrAuth(c: Context, secrets: SecretStore = SecretStore(c)): AsrAuth? = when (asrAuthMode(c)) {
        AsrAuthMode.API_KEY -> secrets.get(SecretName.ASR_API_KEY)?.let { AsrAuth.ApiKey(it) }
        AsrAuthMode.LEGACY -> {
            val appId = asrAppId(c)
            val token = secrets.get(SecretName.ASR_ACCESS_TOKEN)
            if (appId.isNotEmpty() && token != null) AsrAuth.Legacy(appId, token) else null
        }
    }

    sealed interface ProfileResult {
        data class Ready(val profile: CloudProfile) : ProfileResult
        data class Missing(val message: String) : ProfileResult
    }

    fun profile(c: Context, requirePrefix: Boolean): ProfileResult {
        val secrets = SecretStore(c)
        val asrUrl = Endpoints.parse(asrUrl(c)) ?: return ProfileResult.Missing("字节 ASR 服务地址无效")
        val auth = asrAuth(c, secrets) ?: return ProfileResult.Missing(
            if (asrAuthMode(c) == AsrAuthMode.API_KEY) "请先在字节 ASR 设置中填写 API Key" else "请先填写字节 AppID 和 Access Token")
        val intent = intentMode(c)
        var jevUrl: HttpUrl? = null
        var jevKey: String? = null
        if (intent == IntentMode.JEV) {
            jevUrl = Endpoints.parse(jevUrl(c)) ?: return ProfileResult.Missing("JEV 服务地址无效")
            jevKey = secrets.get(SecretName.JEV_API_KEY) ?: return ProfileResult.Missing("请先在 JEV 设置中填写 API Key")
        }
        return ProfileResult.Ready(CloudProfile(asrUrl, auth, resourceId(c), intent, jevUrl, jevKey, jevModel(c),
            vad(c), thresholds(c), requirePrefix))
    }
}
