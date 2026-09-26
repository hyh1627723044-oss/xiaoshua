package io.github.hyh1627723044.shortvideokws

import android.content.Context
import com.k2fsa.sherpa.onnx.*

// Native spotter and stream are created, fed and released on the capture worker thread only.
class LocalKeywordRecognizer(context: Context, keywordsFile: String, private val onKeyword: (String) -> Unit) : FrameSink {
    companion object {
        const val KEYWORDS = "keywords.txt"
        const val KEYWORDS_PREFIXED = "keywords-prefixed.txt"
        const val KEYWORDS_STOP_ONLY = "keywords-stop.txt"
    }

    private val spotter = KeywordSpotter(context.assets, KeywordSpotterConfig(
        featConfig = FeatureConfig(sampleRate = VadSettings.SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "kws/encoder.onnx", decoder = "kws/decoder.onnx", joiner = "kws/joiner.onnx"),
            tokens = "kws/tokens.txt", numThreads = 2, modelType = "zipformer2"),
        keywordsFile = keywordsFile,
        keywordsScore = 1.5f, keywordsThreshold = .25f, numTrailingBlanks = 2))
    private val stream = spotter.createStream()
    private val samples = FloatArray(VadSettings.FRAME_SAMPLES)

    init {
        if (stream.ptr == 0L) { close(); error("关键词配置加载失败") }
    }

    override fun accept(frame: ShortArray) {
        for (i in frame.indices) samples[i] = frame[i] / 32768f
        stream.acceptWaveform(samples, VadSettings.SAMPLE_RATE)
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val keyword = spotter.getResult(stream).keyword
            if (keyword.isNotEmpty()) {
                spotter.reset(stream)
                onKeyword(keyword)
            }
        }
    }

    override fun close() {
        stream.release()
        spotter.release()
    }
}
