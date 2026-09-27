package io.github.hyh1627723044.shortvideokws

object IntentText {
    const val PREFIX = "小刷"
    private val removable = Regex("[\\s\\p{P}\\p{S}]")

    // Full-width ASCII to half-width, then drop whitespace, punctuation and symbols.
    fun normalize(text: String): String {
        val halfWidth = buildString(text.length) {
            for (ch in text) append(if (ch in '！'..'～') ch - 0xFEE0 else ch)
        }
        return halfWidth.lowercase().replace(removable, "")
    }
}

// Whole-utterance alias match only. Anything with extra words, including negations, returns null.
class ExactIntentResolver(private val requirePrefix: Boolean) {
    companion object {
        val ALIASES: Map<String, Command> = mapOf(
            "下一条" to Command.NEXT, "下一个" to Command.NEXT, "下个视频" to Command.NEXT,
            "上一条" to Command.PREVIOUS, "上一个" to Command.PREVIOUS, "上个视频" to Command.PREVIOUS,
            "播放" to Command.PLAY, "继续播放" to Command.PLAY,
            "暂停" to Command.PAUSE,
            "点赞" to Command.LIKE, "点个赞" to Command.LIKE, "点赞点赞" to Command.LIKE,
            "查看评论" to Command.COMMENTS, "打开评论" to Command.COMMENTS, "看评论" to Command.COMMENTS,
            "关闭评论" to Command.CLOSE_COMMENTS, "关掉评论" to Command.CLOSE_COMMENTS,
            "收藏" to Command.FAVORITE, "收藏一下" to Command.FAVORITE, "加个收藏" to Command.FAVORITE,
            "停止控制" to Command.STOP,
        )
    }

    fun resolve(text: String): Command? {
        val normalized = IntentText.normalize(text)
        val body = normalized.removePrefix(IntentText.PREFIX)
        // Stop never needs the prefix.
        if (ALIASES[body] == Command.STOP) return Command.STOP
        if (requirePrefix && body.length == normalized.length) return null
        return ALIASES[body]
    }
}
