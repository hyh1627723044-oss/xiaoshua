package io.github.hyh1627723044.shortvideokws

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

// Wires one AudioCapture to the recognizers for the selected mode. Local mode never touches the network.
// Cloud mode also runs a stop-only local spotter so "停止控制" works without the network.
class ListeningEngine(
    private val context: Context,
    private val profile: CloudProfile?,
    private val requirePrefix: Boolean,
    private val cloudEvents: CloudEvents,
) {
    private val running = AtomicBoolean(true)
    @Volatile private var cloud: CloudRecognizer? = null

    // Main thread. Native resources are released by the worker when run() unwinds.
    fun stop() {
        running.set(false)
        cloud?.cancel()
    }

    // Worker thread; blocks until stop().
    fun run(onReady: () -> Unit, onKeyword: (String) -> Unit) {
        val sinks = ArrayList<FrameSink>()
        try {
            if (profile == null) {
                val file = if (requirePrefix) LocalKeywordRecognizer.KEYWORDS_PREFIXED else LocalKeywordRecognizer.KEYWORDS
                sinks += LocalKeywordRecognizer(context, file, onKeyword)
            } else {
                sinks += LocalKeywordRecognizer(context, LocalKeywordRecognizer.KEYWORDS_STOP_ONLY, onKeyword)
                sinks += CloudRecognizer(context, profile, cloudEvents).also {
                    cloud = it
                    if (!running.get()) it.cancel()
                }
            }
            AudioCapture.run(running, onReady) { frame -> for (sink in sinks) sink.accept(frame) }
        } finally {
            sinks.asReversed().forEach { it.close() }
        }
    }
}
