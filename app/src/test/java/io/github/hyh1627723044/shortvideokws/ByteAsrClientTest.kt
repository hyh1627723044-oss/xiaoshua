package io.github.hyh1627723044.shortvideokws

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ByteAsrClientTest {
    private lateinit var server: MockWebServer
    private val wav = Wav.encodePcm16Mono(shortArrayOf(1, 2, 3))
    private val key = "secret-api-key-123"

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun ok(text: String) = MockResponse().setHeader("X-Api-Status-Code", "20000000")
        .setHeader("X-Api-Message", "OK").setBody(JSONObject().put("result", JSONObject().put("text", text)).toString())

    private fun recognize(auth: AsrAuth = AsrAuth.ApiKey(key), client: ByteAsrClient = ByteAsrClient()) =
        client.execute(client.newCall(server.url("/relay/flash?x=1"), auth, CloudDefaults.ASR_RESOURCE_ID, wav))

    @Test fun sendsFlashRequestWithApiKey() {
        server.enqueue(ok("下一条。"))
        assertEquals(AsrResult.Text("下一条。"), recognize())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/relay/flash?x=1", request.path)   // custom URL is used as-is
        assertEquals(key, request.getHeader("X-Api-Key"))
        assertNull(request.getHeader("X-Api-App-Key"))
        assertEquals("volc.bigasr.auc_turbo", request.getHeader("X-Api-Resource-Id"))
        assertEquals("-1", request.getHeader("X-Api-Sequence"))
        assertTrue(request.getHeader("X-Api-Request-Id")!!.matches(Regex("[0-9a-f-]{36}")))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("wav", body.getJSONObject("audio").getString("format"))
        assertArrayEquals(wav, Base64.getDecoder().decode(body.getJSONObject("audio").getString("data")))
        assertEquals("bigmodel", body.getJSONObject("request").getString("model_name"))
    }
    @Test fun legacyAuthSendsAppKeyAndAccessKey() {
        server.enqueue(ok("暂停"))
        recognize(AsrAuth.Legacy("app-1", "token-xyz"))
        val request = server.takeRequest()
        assertEquals("app-1", request.getHeader("X-Api-App-Key"))
        assertEquals("token-xyz", request.getHeader("X-Api-Access-Key"))
        assertNull(request.getHeader("X-Api-Key"))
    }
    @Test fun silenceAndEmptyTextAreNoSpeech() {
        server.enqueue(MockResponse().setHeader("X-Api-Status-Code", "20000003"))
        server.enqueue(MockResponse().setResponseCode(400).setHeader("X-Api-Status-Code", "45000002"))
        server.enqueue(ok("  "))
        repeat(3) { assertEquals(AsrResult.NoSpeech, recognize()) }
    }
    @Test fun serviceErrorsBecomeShortFailuresWithoutSecrets() {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("X-Api-Status-Code", "45000151")
            .setHeader("X-Api-Message", "bad format for key $key").setBody("{\"echo\":\"$key\"}"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid $key"))
        server.enqueue(MockResponse().setHeader("X-Api-Status-Code", "55000031"))
        server.enqueue(MockResponse().setHeader("X-Api-Status-Code", "20000000").setBody("not json"))
        server.enqueue(MockResponse().setBody("{}"))
        val results = (1..5).map { recognize() }
        assertEquals(AsrResult.Failure("ASR 音频格式错误（45000151）"), results[0])
        assertEquals(AsrResult.Failure("ASR 鉴权失败"), results[1])
        assertEquals(AsrResult.Failure("ASR 服务繁忙（55000031）"), results[2])
        assertEquals(AsrResult.Failure("ASR 响应无法解析"), results[3])
        assertEquals(AsrResult.Failure("ASR 响应缺少状态码"), results[4])
        results.forEach { assertFalse(it.toString().contains(key)) }
        assertFalse(AsrAuth.ApiKey(key).toString().contains(key))
        assertFalse(AsrAuth.Legacy("a", key).toString().contains(key))
    }
    @Test fun slowResponsesTimeOutWithoutRetry() {
        server.enqueue(ok("下一条").setHeadersDelay(2, TimeUnit.SECONDS))
        val start = System.nanoTime()
        assertEquals(AsrResult.Failure("请求超时"), recognize(client = ByteAsrClient(callMs = 300)))
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))
        assertEquals(1, server.requestCount)
    }
    @Test fun cancelAbortsInFlightCall() {
        server.enqueue(ok("下一条").setHeadersDelay(3, TimeUnit.SECONDS))
        val client = ByteAsrClient()
        val call = client.newCall(server.url("/"), AsrAuth.ApiKey(key), CloudDefaults.ASR_RESOURCE_ID, wav)
        var result: AsrResult? = null
        val worker = thread { result = client.execute(call) }
        server.takeRequest(2, TimeUnit.SECONDS)
        call.cancel()
        worker.join(2_000)
        assertEquals(AsrResult.Failure("已取消"), result)
    }
}
