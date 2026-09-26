package io.github.hyh1627723044.shortvideokws

import kotlin.math.max

data class VadSettings(
    val startThreshold: Float = DEFAULT_START_THRESHOLD,
    val startMs: Int = DEFAULT_START_MS,
    val endMs: Int = DEFAULT_END_MS,
    val preRollMs: Int = DEFAULT_PRE_ROLL_MS,
) {
    val endThreshold: Float get() = max(0.05f, startThreshold - 0.15f)
    val startFrames: Int get() = framesFor(startMs)
    val endFrames: Int get() = framesFor(endMs)
    val preRollFrames: Int get() = framesFor(preRollMs)

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 512 // Silero window, 32 ms at 16 kHz.
        const val FRAME_MS = 32
        const val MIN_SPEECH_MS = 150
        const val MAX_UTTERANCE_MS = 8000
        const val DEFAULT_START_THRESHOLD = 0.60f
        const val DEFAULT_START_MS = 60
        const val DEFAULT_END_MS = 300
        const val DEFAULT_PRE_ROLL_MS = 300
        val START_THRESHOLD_RANGE = 0.30f..0.90f
        val START_MS_RANGE = 32..500
        val END_MS_RANGE = 96..2000
        val PRE_ROLL_MS_RANGE = 0..1000

        // Durations are "at least", so they round up to whole model windows.
        fun framesFor(ms: Int) = (ms + FRAME_MS - 1) / FRAME_MS

        // Out-of-range or corrupted stored values fall back to defaults field by field.
        fun sanitized(startThreshold: Float, startMs: Int, endMs: Int, preRollMs: Int) = VadSettings(
            if (startThreshold in START_THRESHOLD_RANGE) startThreshold else DEFAULT_START_THRESHOLD,
            if (startMs in START_MS_RANGE) startMs else DEFAULT_START_MS,
            if (endMs in END_MS_RANGE) endMs else DEFAULT_END_MS,
            if (preRollMs in PRE_ROLL_MS_RANGE) preRollMs else DEFAULT_PRE_ROLL_MS,
        )
    }
}

sealed interface SegmentEvent {
    data object Started : SegmentEvent
    class Completed(val pcm: ShortArray, val voicedMs: Int, val forced: Boolean) : SegmentEvent
    data object TooShort : SegmentEvent
}

// Hysteresis state machine over per-window speech probabilities. Audio stays in memory only.
class UtteranceSegmenter(private val settings: VadSettings) {
    private enum class State { IDLE, START_CANDIDATE, SPEAKING, END_CANDIDATE }

    private val maxFrames = VadSettings.MAX_UTTERANCE_MS / VadSettings.FRAME_MS
    private val minVoicedFrames = VadSettings.framesFor(VadSettings.MIN_SPEECH_MS)
    private var state = State.IDLE
    private val preRoll = ArrayDeque<ShortArray>()
    private val candidate = ArrayList<ShortArray>()
    private val utterance = ArrayList<ShortArray>()
    private var voicedFrames = 0
    private var silentFrames = 0

    fun accept(probability: Float, frame: ShortArray): SegmentEvent? {
        val p = if (probability.isNaN()) 0f else probability
        val copy = frame.copyOf()
        return when (state) {
            State.IDLE -> if (p >= settings.startThreshold) {
                candidate.add(copy)
                state = State.START_CANDIDATE
                maybeStart()
            } else { remember(copy); null }
            State.START_CANDIDATE -> if (p >= settings.startThreshold) {
                candidate.add(copy)
                maybeStart()
            } else {
                candidate.forEach(::remember)
                candidate.clear()
                remember(copy)
                state = State.IDLE
                null
            }
            State.SPEAKING, State.END_CANDIDATE -> {
                utterance.add(copy)
                if (p >= settings.endThreshold) {
                    voicedFrames++
                    silentFrames = 0
                    state = State.SPEAKING
                } else {
                    silentFrames++
                    state = State.END_CANDIDATE
                }
                when {
                    silentFrames >= settings.endFrames -> finish(forced = false)
                    utterance.size >= maxFrames -> finish(forced = true)
                    else -> null
                }
            }
        }
    }

    fun reset() {
        state = State.IDLE
        preRoll.clear()
        candidate.clear()
        utterance.clear()
        voicedFrames = 0
        silentFrames = 0
    }

    private fun maybeStart(): SegmentEvent? {
        if (candidate.size < settings.startFrames) return null
        utterance.addAll(preRoll)
        utterance.addAll(candidate)
        voicedFrames = candidate.size
        silentFrames = 0
        preRoll.clear()
        candidate.clear()
        state = State.SPEAKING
        return SegmentEvent.Started
    }

    private fun remember(frame: ShortArray) {
        if (settings.preRollFrames == 0) return
        preRoll.addLast(frame)
        while (preRoll.size > settings.preRollFrames) preRoll.removeFirst()
    }

    private fun finish(forced: Boolean): SegmentEvent {
        val voicedMs = voicedFrames * VadSettings.FRAME_MS
        val pcm = if (voicedFrames >= minVoicedFrames) {
            ShortArray(utterance.sumOf { it.size }).also { out ->
                var offset = 0
                for (frame in utterance) { frame.copyInto(out, offset); offset += frame.size }
            }
        } else null
        reset()
        return if (pcm == null) SegmentEvent.TooShort else SegmentEvent.Completed(pcm, voicedMs, forced)
    }
}
