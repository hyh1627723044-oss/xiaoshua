package io.github.hyh1627723044.shortvideokws

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class JevIntentResolverTest {
    private lateinit var server: MockWebServer
    private val key = "jv_live_secret_456"
    private val jev = JevIntentResolver()

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun answer(choice: Any?, confidence: Any?, probabilities: Map<String, Any?>?): MockResponse {
        val a = JSONObject().put("type", "choice")
        if (choice != null) a.put("choice", choice)
        if (confidence != null) a.put("confidence", confidence)
        if (probabilities != null) a.put("probabilities", JSONObject(probabilities))
        return MockResponse().setBody(JSONObject().put("model", "jev-1.13.0")
            .put("answers", JSONObject().put("intent", a)).toString())
    }
    private fun decide(text: String = "帮我点个赞", thresholds: JevThresholds = JevThresholds(), resolver: JevIntentResolver = jev) =
        resolver.execute(resolver.newCall(server.url("/api/v1/decide"), key, "jev-1.13.0", text), thresholds)

    @Test fun sendsChoiceQuestionWithFixedLabelsOnly() {
        server.enqueue(answer("LIKE", 0.95, mapOf("LIKE" to 0.95, "NO_ACTION" to 0.05)))
        assertEquals(Command.LIKE, decide().command)
        val request = server.takeRequest()
        assertEquals("Bearer $key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("jev-1.13.0", body.getString("model"))
        assertEquals("帮我点个赞", body.getString("state"))
        val question = body.getJSONObject("questions").getJSONObject("intent")
        assertEquals("choice", question.getString("type"))
        assertTrue(question.getString("instructions").contains("NO_ACTION"))
        val labels = question.getJSONObject("criteria").keys().asSequence().toSet()
        assertEquals(Command.entries.map { it.name }.toSet() + "NO_ACTION", labels)
        assertEquals(setOf("model", "state", "questions"), body.keys().asSequence().toSet())
    }
    @Test fun acceptsConfidentChoice() {
        server.enqueue(answer("NEXT", 0.86, mapOf("NEXT" to 0.86, "PREVIOUS" to 0.10, "NO_ACTION" to 0.04)))
        val d = decide("换一个吧")
        assertEquals(Command.NEXT, d.command)
        assertEquals("JEV：NEXT 0.86", d.note)
    }
    @Test fun noActionUnknownAndMalformedAnswersNeverAct() {
        server.enqueue(answer("NO_ACTION", 0.99, mapOf("NO_ACTION" to 0.99)))
        server.enqueue(answer("DELETE_ACCOUNT", 0.99, mapOf("DELETE_ACCOUNT" to 0.99)))
        server.enqueue(answer("NEXT", null, mapOf("NEXT" to 0.9)))
        server.enqueue(answer("NEXT", "0.95", mapOf("NEXT" to 0.95)))
        server.enqueue(answer("NEXT", 0.95, null))
        server.enqueue(answer("NEXT", 0.95, mapOf("NEXT" to "high")))
        server.enqueue(answer(3, 0.95, mapOf("NEXT" to 0.95)))
        server.enqueue(MockResponse().setBody("{\"answers\":{}}"))
        server.enqueue(MockResponse().setBody("<html>"))
        repeat(9) { assertNull("case $it", decide().command) }
    }
    @Test fun lowConfidenceAndLikeThresholdRejected() {
        server.enqueue(answer("PAUSE", 0.79, mapOf("PAUSE" to 0.79, "NO_ACTION" to 0.21)))
        server.enqueue(answer("LIKE", 0.89, mapOf("LIKE" to 0.89, "NO_ACTION" to 0.11)))
        server.enqueue(answer("LIKE", 0.91, mapOf("LIKE" to 0.91, "NO_ACTION" to 0.09)))
        assertNull(decide().command)
        assertEquals("JEV：置信度不足（LIKE 0.89）", decide().note)
        assertEquals(Command.LIKE, decide().command)
    }
    @Test fun tiesAndInconsistentProbabilitiesRejected() {
        server.enqueue(answer("NEXT", 0.95, mapOf("NEXT" to 0.50, "PREVIOUS" to 0.45)))
        server.enqueue(answer("NEXT", 0.95, mapOf("NEXT" to 0.20, "PAUSE" to 0.80)))
        assertEquals("JEV：结果不明确", decide().note)
        assertEquals("JEV 结果不一致", decide().note)
    }
    @Test fun httpErrorsTimeoutsAndNotesNeverLeakKey() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key $key\"}"))
        server.enqueue(MockResponse().setResponseCode(402).setBody("{\"code\":\"insufficient_credits\"}"))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(answer("NEXT", 0.9, mapOf("NEXT" to 0.9)).setHeadersDelay(2, TimeUnit.SECONDS))
        val notes = listOf(decide(), decide(), decide(), decide(resolver = JevIntentResolver(callMs = 300)))
        assertEquals(listOf("JEV 鉴权失败", "JEV 余额不足", "JEV HTTP 502", "JEV 请求超时"), notes.map { it.note })
        notes.forEach { assertNull(it.command); assertFalse(it.note.contains(key)) }
    }
    @Test fun thresholdsSanitize() {
        assertEquals(JevThresholds(), JevThresholds.sanitized(Double.NaN, 1.5))
        assertEquals(JevThresholds(0.7, 0.95), JevThresholds.sanitized(0.7, 0.95))
    }
}
