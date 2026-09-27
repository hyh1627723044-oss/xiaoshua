package io.github.hyh1627723044.shortvideokws

enum class Command(val phrase: String) {
    NEXT("下一条"), PREVIOUS("上一条"), PLAY("播放"), PAUSE("暂停"),
    LIKE("点赞点赞"), COMMENTS("查看评论"), CLOSE_COMMENTS("关闭评论"), FAVORITE("收藏一下"), STOP("停止控制");

    companion object {
        fun fromKeyword(keyword: String): Command? = entries.firstOrNull { it.name == keyword }
    }
}

enum class CommandSource { LOCAL_KEYWORD, CLOUD }

// Times are SystemClock.elapsedRealtime() values. Cloud requests are timed from the end of the utterance.
data class CommandRequest(
    val command: Command,
    val source: CommandSource,
    val utteranceId: Long,
    val capturedAt: Long,
    val expiresAt: Long,
) {
    companion object {
        const val LOCAL_FRESH_MS = 700L
        const val CLOUD_FRESH_MS = 5000L
        fun local(command: Command, detectedAt: Long) =
            CommandRequest(command, CommandSource.LOCAL_KEYWORD, 0, detectedAt, detectedAt + LOCAL_FRESH_MS)
        fun cloud(command: Command, utteranceId: Long, endedAt: Long) =
            CommandRequest(command, CommandSource.CLOUD, utteranceId, endedAt, endedAt + CLOUD_FRESH_MS)
    }
}

// Called only on the main thread. Expired callbacks and duplicate detections never queue gestures.
class CommandGate {
    private var lastAt = Long.MIN_VALUE
    private var lastCommand: Command? = null
    fun accept(request: CommandRequest, now: Long, active: Boolean, busy: Boolean): Boolean {
        if (!active || busy || now < request.capturedAt || now > request.expiresAt) return false
        val command = request.command
        if (lastAt != Long.MIN_VALUE && (now - lastAt < 250 || (lastCommand == command && now - lastAt < 900))) return false
        lastAt = now
        lastCommand = command
        return true
    }
}
