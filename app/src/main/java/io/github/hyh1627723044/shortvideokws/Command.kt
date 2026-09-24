package io.github.hyh1627723044.shortvideokws

enum class Command(val phrase: String) {
    NEXT("下一条"), PREVIOUS("上一条"), PLAY("播放"), PAUSE("暂停"),
    LIKE("点赞点赞"), COMMENTS("查看评论"), CLOSE_COMMENTS("关闭评论"), STOP("停止控制");

    companion object {
        fun fromKeyword(keyword: String): Command? = entries.firstOrNull { it.name == keyword }
    }
}

// Called only on the main thread. Expired callbacks and duplicate detections never queue gestures.
class CommandGate {
    private var lastAt = Long.MIN_VALUE
    private var lastCommand: Command? = null
    fun accept(command: Command, detectedAt: Long, now: Long, active: Boolean, busy: Boolean): Boolean {
        if (!active || busy || now < detectedAt || now - detectedAt > 700) return false
        if (lastAt != Long.MIN_VALUE && (now - lastAt < 250 || (lastCommand == command && now - lastAt < 900))) return false
        lastAt = now
        lastCommand = command
        return true
    }
}
