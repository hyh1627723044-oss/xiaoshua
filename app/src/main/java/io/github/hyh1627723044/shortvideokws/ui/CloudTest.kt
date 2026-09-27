package io.github.hyh1627723044.shortvideokws.ui

import android.os.Handler
import android.os.Looper
import io.github.hyh1627723044.shortvideokws.*
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean

// One-off connection tests for the settings screens. Work runs on a background thread;
// callbacks arrive on the main thread and are suppressed after cancel().
class CloudTest {
    companion object {
        private const val RECORD_MS = 3000
        const val JEV_SAMPLE = "帮我点个赞"
    }
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    @Volatile private var cancelled = false
    @Volatile private var call: CloudCall? = null

    fun cancel() {
        cancelled = true
        recording.set(false)
        call?.cancel()
    }

    // Records up to 3 s, then uploads once. Must not run while listening: the mic is exclusive.
    fun asr(endpoint: HttpUrl, auth: AsrAuth, resourceId: String, onProgress: (String) -> Unit, onDone: (Notice) -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        recording.set(true)
        onProgress("正在录音（3 秒），请说一句话…")
        Thread {
            val notice = try {
                val frames = ArrayList<ShortArray>()
                val needed = VadSettings.SAMPLE_RATE * RECORD_MS / 1000 / VadSettings.FRAME_SAMPLES
                AudioCapture.run(recording, onReady = {}) { frame ->
                    frames.add(frame.copyOf())
                    if (frames.size >= needed) recording.set(false)
                }
                if (cancelled) return@Thread
                val pcm = ShortArray(frames.size * VadSettings.FRAME_SAMPLES)
                frames.forEachIndexed { i, f -> f.copyInto(pcm, i * VadSettings.FRAME_SAMPLES) }
                post { onProgress("正在识别…") }
                val client = ByteAsrClient()
                val c = client.newCall(endpoint, auth, resourceId, Wav.encodePcm16Mono(pcm)).also { call = it }
                when (val r = client.execute(c)) {
                    is AsrResult.Text -> Notice("测试成功，识别结果：${r.text}", Tone.SUCCESS)
                    AsrResult.NoSpeech -> Notice("连接成功，但没有识别到语音", Tone.WARNING)
                    is AsrResult.Failure -> Notice("测试失败：${r.reason}", Tone.DANGER)
                }
            } catch (e: Exception) {
                Notice("测试失败：${e.message ?: e.javaClass.simpleName}", Tone.DANGER)
            } finally {
                recording.set(false)
                call = null
                busy.set(false)
            }
            post { onDone(notice) }
        }.start()
        return true
    }

    // Sends only the built-in sample text, never user speech or screen content.
    fun jev(endpoint: HttpUrl, apiKey: String, model: String, thresholds: JevThresholds, onDone: (Notice) -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        Thread {
            val jev = JevIntentResolver()
            val c = jev.newCall(endpoint, apiKey, model, JEV_SAMPLE).also { call = it }
            val decision = try { jev.execute(c, thresholds) } finally { call = null; busy.set(false) }
            val notice = if (decision.command != null) Notice("测试成功：${decision.note}", Tone.SUCCESS)
                else Notice("返回：${decision.note}", Tone.WARNING)
            post { onDone(notice) }
        }.start()
        return true
    }

    private fun post(block: () -> Unit) = main.post { if (!cancelled) block() }
}
