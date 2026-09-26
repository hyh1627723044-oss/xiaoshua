package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test

class IntentTextTest {
    @Test fun normalizesPunctuationWhitespaceAndFullWidth() {
        assertEquals("小刷下一条", IntentText.normalize(" 小刷，下一条。"))
        assertEquals("点个赞", IntentText.normalize("点 个\t赞！！"))
        assertEquals("next1", IntentText.normalize("ＮＥＸＴ１"))
    }
    @Test fun exactAliasesResolve() {
        val resolver = ExactIntentResolver(requirePrefix = false)
        assertEquals(Command.NEXT, resolver.resolve("下一条。"))
        assertEquals(Command.NEXT, resolver.resolve("下个视频"))
        assertEquals(Command.LIKE, resolver.resolve("点个赞！"))
        assertEquals(Command.CLOSE_COMMENTS, resolver.resolve("关掉评论"))
        assertEquals(Command.STOP, resolver.resolve("停止控制"))
    }
    @Test fun negationsExtraWordsAndPartialsNeverMatch() {
        val resolver = ExactIntentResolver(requirePrefix = false)
        listOf("不要点赞", "别暂停", "点赞然后下一条", "我不想看下一条", "下一条吧我看看", "停止", "", "。。。")
            .forEach { assertNull(it, resolver.resolve(it)) }
    }
    @Test fun prefixModeRequiresWakeWordExceptForStop() {
        val resolver = ExactIntentResolver(requirePrefix = true)
        assertNull(resolver.resolve("下一条"))
        assertEquals(Command.NEXT, resolver.resolve("小刷，下一条"))
        assertNull(resolver.resolve("小刷不要点赞"))
        assertEquals(Command.STOP, resolver.resolve("停止控制"))
        assertEquals(Command.STOP, resolver.resolve("小刷停止控制"))
    }
}
