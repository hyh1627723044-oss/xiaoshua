package io.github.hyh1627723044.shortvideokws.ui

import android.content.Context
import io.github.hyh1627723044.shortvideokws.*

// Polled copy of the service state for Compose; AppState itself is plain @Volatile fields.
data class ServiceSnapshot(
    val listening: Boolean,
    val starting: Boolean,
    val status: String,
    val lastAction: String,
    val lastHeard: String,
    val accessibility: Boolean,
) {
    val busy get() = listening || starting
    val failed get() = !busy && FAILURES.any { status.startsWith(it) }

    companion object {
        private val FAILURES = listOf("无法", "监听失败", "本机无法", "需要")
        fun read() = ServiceSnapshot(AppState.listening, AppState.starting, AppState.status,
            AppState.lastAction, AppState.lastHeard, GestureService.instance != null)
    }
}

// Settings summary shown on the two tabs. Reading secrets touches Keystore, so this is refreshed on demand only.
data class SettingsSnapshot(
    val recognition: RecognitionMode,
    val intent: IntentMode,
    val prefix: Boolean,
    val commentY: Int,
    val asrConfigured: Boolean,
    val jevConfigured: Boolean,
) {
    val cloud get() = recognition == RecognitionMode.CLOUD

    companion object {
        fun read(c: Context): SettingsSnapshot {
            val secrets = SecretStore(c)
            return SettingsSnapshot(
                CloudSettings.recognitionMode(c), CloudSettings.intentMode(c), AppState.requirePrefix(c),
                AppState.prefs(c).getInt("comment_y", 65), CloudSettings.asrAuth(c, secrets) != null,
                secrets.has(SecretName.JEV_API_KEY),
            )
        }
    }
}

// What the hero card shows for the current service state.
data class HeroState(val badge: String, val tone: Tone, val title: String, val subtitle: String)

fun heroState(s: ServiceSnapshot): HeroState = when {
    s.starting -> HeroState("正在准备", Tone.INFO, "正在加载模型…", "马上就好")
    s.listening -> {
        val title = when {
            s.status.startsWith("检测到语音") -> "听到你在说话…"
            s.status.startsWith("正在识别") -> "正在识别…"
            s.status.startsWith("正在判断") -> "正在理解意图…"
            else -> "正在听你说"
        }
        val detail = listOf(s.lastHeard, s.lastAction).filter { it.isNotBlank() && it != "还没有收到口令" }
        HeroState("正在监听", Tone.SUCCESS, title, detail.joinToString("\n").ifEmpty { "切到抖音竖屏视频页，直接说口令" })
    }
    s.failed -> HeroState("出错了", Tone.DANGER, "未能开始监听", s.status)
    else -> HeroState("准备就绪", Tone.SUCCESS, "准备好听你说", "开始后，可切换到抖音")
}

fun examplePhrases(s: SettingsSnapshot): List<String> = when {
    !s.cloud -> listOf("下一条", "点赞点赞", "查看评论")
    s.intent == IntentMode.JEV -> listOf("换一个吧", "帮我点个赞", "看看评论")
    else -> listOf("下一个", "点个赞", "查看评论")
}.map { if (s.prefix) "${IntentText.PREFIX}$it" else it }
