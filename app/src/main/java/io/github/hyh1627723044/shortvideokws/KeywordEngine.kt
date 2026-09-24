package io.github.hyh1627723044.shortvideokws

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.atomic.AtomicBoolean

class KeywordEngine(private val context: Context) {
    private val running = AtomicBoolean(true)
    fun stop() { running.set(false) }

    // Native decoder, stream and recorder belong to this worker, including their release.
    @SuppressLint("MissingPermission")
    fun run(onReady: () -> Unit, onKeyword: (String) -> Unit) {
        val prefix = AppState.prefs(context).getBoolean("prefix", false)
        val spotter = KeywordSpotter(context.assets, KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "kws/encoder.onnx", decoder = "kws/decoder.onnx", joiner = "kws/joiner.onnx"),
                tokens = "kws/tokens.txt", numThreads = 2, modelType = "zipformer2"),
            keywordsFile = if (prefix) "keywords-prefixed.txt" else "keywords.txt",
            keywordsScore = 1.5f, keywordsThreshold = .25f, numTrailingBlanks = 2))
        try {
            val stream = spotter.createStream()
            try {
                check(stream.ptr != 0L) { "关键词配置加载失败" }
                val minBytes = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minBytes > 0) { "设备不支持 16kHz 录音" }
                val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes * 2, 6400))
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED) { "无法初始化麦克风" }
                    if (!running.get()) return
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "无法开始录音" }
                    onReady()
                    val pcm = ShortArray(640) // 40 ms; decoder consumes frames as soon as ready.
                    while (running.get()) {
                        val n = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                        check(n > 0) { "麦克风读取失败：$n" }
                        if (!running.get()) break
                        stream.acceptWaveform(FloatArray(n) { pcm[it] / 32768f }, 16000)
                        while (running.get() && spotter.isReady(stream)) {
                            spotter.decode(stream)
                            val keyword = spotter.getResult(stream).keyword
                            if (keyword.isNotEmpty()) {
                                spotter.reset(stream)
                                if (running.get()) onKeyword(keyword)
                            }
                        }
                    }
                } finally {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                    recorder.release()
                }
            } finally { stream.release() }
        } finally { spotter.release() }
    }
}
