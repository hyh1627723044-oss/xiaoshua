package io.github.hyh1627723044.shortvideokws

import android.content.Context
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

// Called from capture and network threads; implementations must hop to the main thread.
interface CloudEvents {
    fun phase(text: String?)          // null means back to plain listening
    fun result(text: String)          // may contain the transcript: UI memory only, never logs
    fun command(request: CommandRequest)
}

// Local Silero VAD cuts one utterance; only that utterance is uploaded. At most one is in flight,
// and utterances finished meanwhile are dropped rather than queued.
class CloudRecognizer(
    context: Context,
    private val profile: CloudProfile,
    private val events: CloudEvents,
    private val asr: ByteAsrClient = ByteAsrClient(),
    private val jev: JevIntentResolver = JevIntentResolver(),
) : FrameSink {
    // Probabilities come from compute(); sherpa's own segmentation is unused.
    private val vad = Vad(context.assets, VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(model = "vad/silero_vad.onnx", windowSize = VadSettings.FRAME_SAMPLES),
        sampleRate = VadSettings.SAMPLE_RATE, numThreads = 1))
    private val segmenter = UtteranceSegmenter(profile.vad)
    private val exact = ExactIntentResolver(profile.requirePrefix)
    private val network = Executors.newSingleThreadExecutor()
    private val samples = FloatArray(VadSettings.FRAME_SAMPLES)
    private val busy = AtomicBoolean(false)
    @Volatile private var cancelled = false
    @Volatile private var inFlight: CloudCall? = null
    private var nextId = 0L

    // Capture thread.
    override fun accept(frame: ShortArray) {
        for (i in frame.indices) samples[i] = frame[i] / 32768f
        when (val event = segmenter.accept(vad.compute(samples), frame)) {
            SegmentEvent.Started -> if (!busy.get()) events.phase("检测到语音")
            SegmentEvent.TooShort -> if (!busy.get()) events.phase(null)
            is SegmentEvent.Completed -> submit(event.pcm, SystemClock.elapsedRealtime())
            null -> Unit
        }
    }

    // Any thread. Aborts network work; native resources are released later by close().
    fun cancel() {
        cancelled = true
        inFlight?.cancel()
        network.shutdownNow()
    }

    // Capture thread, after the last accept().
    override fun close() {
        cancel()
        vad.release()
    }

    private fun submit(pcm: ShortArray, endedAt: Long) {
        if (cancelled || !busy.compareAndSet(false, true)) return
        val id = ++nextId
        events.phase("正在识别")
        try {
            network.execute {
                try { process(id, pcm, endedAt) } finally {
                    inFlight = null
                    busy.set(false)
                    if (!cancelled) events.phase(null)
                }
            }
        } catch (e: RejectedExecutionException) { busy.set(false) }
    }

    private fun process(id: Long, pcm: ShortArray, endedAt: Long) {
        val asrCall = asr.newCall(profile.asrUrl, profile.asrAuth, profile.resourceId, Wav.encodePcm16Mono(pcm))
        if (!track(asrCall)) return
        val text = when (val r = asr.execute(asrCall)) {
            is AsrResult.Text -> r.text
            AsrResult.NoSpeech -> { events.result("没有识别到内容"); return }
            is AsrResult.Failure -> { if (!cancelled) events.result("识别失败：${r.reason}"); return }
        }
        if (cancelled) return
        exact.resolve(text)?.let {
            events.result("听到“$text” → ${it.phrase}")
            events.command(CommandRequest.cloud(it, id, endedAt))
            return
        }
        if (profile.intentMode != IntentMode.JEV) { events.result("听到“$text” → 未匹配口令"); return }
        if (profile.requirePrefix && !IntentText.normalize(text).startsWith(IntentText.PREFIX)) {
            events.result("听到“$text” → 缺少“小刷”前缀")
            return
        }
        val url = profile.jevUrl ?: return
        val key = profile.jevKey ?: return
        events.phase("正在判断意图")
        val jevCall = jev.newCall(url, key, profile.jevModel, text)
        if (!track(jevCall)) return
        val decision = jev.execute(jevCall, profile.thresholds)
        if (cancelled) return
        events.result("听到“$text” → ${decision.note}")
        decision.command?.let { events.command(CommandRequest.cloud(it, id, endedAt)) }
    }

    // Publishes the call before re-checking cancellation, so cancel() can never miss it.
    private fun track(call: CloudCall): Boolean {
        inFlight = call
        if (cancelled) { call.cancel(); return false }
        return true
    }
}
