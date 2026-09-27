package io.github.hyh1627723044.shortvideokws

import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CloudDefaults {
    const val ASR_URL = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"
    // 豆包录音文件识别模型 2.0; verified to work on the flash endpoint with inline audio.
    const val ASR_RESOURCE_ID = "volc.seedasr.auc"
    // Default before 0.3.1. Accounts with only the 2.0 model get 45000030 for it.
    const val OLD_ASR_RESOURCE_ID = "volc.bigasr.auc_turbo"
    const val JEV_URL = "https://jevtypesafeai.com/api/v1/decide"
    const val JEV_MODEL = "jev-1.13.0"
}

object Endpoints {
    // Full endpoint URLs only: https, no userinfo, no fragment. Query strings are kept for relays.
    fun parse(raw: String): HttpUrl? {
        val trimmed = raw.trim()
        if (trimmed.contains('#')) return null
        val url = trimmed.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url
    }
}

// OkHttp's call timeout also marks a call canceled, so user cancellation is tracked separately.
// secrets are redacted from any server-provided text shown to the user.
class CloudCall(val call: Call, val secrets: List<String> = emptyList()) {
    @Volatile var cancelled = false
        private set
    fun cancel() { cancelled = true; call.cancel() }
}

sealed interface AsrAuth {
    // New console: a single API key.
    data class ApiKey(val key: String) : AsrAuth { override fun toString() = "ApiKey(***)" }
    // Old console: AppID plus Access Token.
    data class Legacy(val appId: String, val accessToken: String) : AsrAuth {
        override fun toString() = "Legacy($appId, ***)"
    }
}

data class JevThresholds(
    val accept: Double = DEFAULT_ACCEPT,
    val like: Double = DEFAULT_LIKE,
) {
    // Like and favorite are visible, harder-to-undo actions, so they share the stricter threshold.
    fun forLabel(label: String) = if (label == Command.LIKE.name || label == Command.FAVORITE.name) like else accept

    companion object {
        const val DEFAULT_ACCEPT = 0.80
        const val DEFAULT_LIKE = 0.90
        // The top two options must be at least this far apart, otherwise the result is treated as a tie.
        const val MIN_MARGIN = 0.10
        val RANGE = 0.50..0.99
        fun sanitized(accept: Double, like: Double) = JevThresholds(
            if (accept in RANGE) accept else DEFAULT_ACCEPT,
            if (like in RANGE) like else DEFAULT_LIKE,
        )
    }
}
