package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test

class UtteranceSegmenterTest {
    private fun frame(value: Int = 0) = ShortArray(VadSettings.FRAME_SAMPLES) { value.toShort() }
    private fun UtteranceSegmenter.feed(p: Float, count: Int, value: Int = 0): List<SegmentEvent> =
        (1..count).mapNotNull { accept(p, frame(value)) }

    @Test fun settingsDeriveEndThresholdAndAlignToWindows() {
        val s = VadSettings()
        assertEquals(0.45f, s.endThreshold, 1e-4f)
        assertEquals(2, s.startFrames)   // 60 ms -> 64 ms
        assertEquals(10, s.endFrames)    // 300 ms -> 320 ms
        assertEquals(10, s.preRollFrames)
        assertEquals(0.15f, VadSettings(startThreshold = 0.30f).endThreshold, 1e-4f)
        assertEquals(0, VadSettings(preRollMs = 0).preRollFrames)
    }
    @Test fun invalidStoredSettingsFallBackToDefaults() {
        assertEquals(VadSettings(), VadSettings.sanitized(Float.NaN, 5, 5000, -1))
        assertEquals(VadSettings(), VadSettings.sanitized(0.95f, 501, 95, 1001))
        val custom = VadSettings.sanitized(0.7f, 100, 500, 0)
        assertEquals(VadSettings(0.7f, 100, 500, 0), custom)
    }
    @Test fun speechStartsAfter64msAboveThreshold() {
        val seg = UtteranceSegmenter(VadSettings())
        assertNull(seg.accept(0.61f, frame()))
        assertEquals(SegmentEvent.Started, seg.accept(0.61f, frame()))
    }
    @Test fun singleSpikeAndMidProbabilityDoNotStart() {
        val seg = UtteranceSegmenter(VadSettings())
        assertTrue(seg.feed(0.9f, 1).isEmpty())
        assertTrue(seg.feed(0.1f, 1).isEmpty())
        assertTrue(seg.feed(0.9f, 1).isEmpty())
        assertTrue(seg.feed(0.5f, 20).isEmpty()) // between end and start threshold
    }
    @Test fun briefDipDoesNotEndButSustainedSilenceDoes() {
        val seg = UtteranceSegmenter(VadSettings())
        assertEquals(listOf(SegmentEvent.Started), seg.feed(0.8f, 10))
        assertTrue(seg.feed(0.2f, 9).isEmpty())  // 288 ms < 320 ms
        assertTrue(seg.feed(0.5f, 1).isEmpty())  // back above 0.45 resets the end timer
        assertTrue(seg.feed(0.2f, 9).isEmpty())
        val done = seg.feed(0.2f, 1).single() as SegmentEvent.Completed
        assertFalse(done.forced)
        assertEquals(11 * VadSettings.FRAME_MS, done.voicedMs)
    }
    @Test fun utterancesShorterThan150msAreDropped() {
        val seg = UtteranceSegmenter(VadSettings())
        assertEquals(listOf(SegmentEvent.Started), seg.feed(0.8f, 4)) // 128 ms voiced
        assertEquals(listOf(SegmentEvent.TooShort), seg.feed(0.1f, 10))
        assertEquals(listOf(SegmentEvent.Started), seg.feed(0.8f, 5)) // state was reset
    }
    @Test fun eightSecondsForcesCompletion() {
        val seg = UtteranceSegmenter(VadSettings(preRollMs = 0))
        val events = seg.feed(0.9f, 250)
        assertEquals(SegmentEvent.Started, events.first())
        val done = events.last() as SegmentEvent.Completed
        assertTrue(done.forced)
        assertEquals(250 * VadSettings.FRAME_SAMPLES, done.pcm.size)
    }
    @Test fun preRollKeepsOnlyConfiguredLeadingAudio() {
        val seg = UtteranceSegmenter(VadSettings())
        for (i in 1..20) seg.accept(0.0f, frame(i))           // frames 11..20 survive
        seg.feed(0.9f, 5, value = 100)
        val done = seg.feed(0.0f, 10).single() as SegmentEvent.Completed
        assertEquals((10 + 5 + 10) * VadSettings.FRAME_SAMPLES, done.pcm.size)
        assertEquals(11.toShort(), done.pcm.first())
        assertEquals(100.toShort(), done.pcm[10 * VadSettings.FRAME_SAMPLES])
    }
    @Test fun failedStartCandidateReturnsToPreRoll() {
        val seg = UtteranceSegmenter(VadSettings(startMs = 96))  // 3 frames
        seg.feed(0.0f, 1, value = 1)
        seg.feed(0.9f, 2, value = 2)                              // candidate fails
        seg.feed(0.0f, 1, value = 3)
        seg.feed(0.9f, 5, value = 4)
        val done = seg.feed(0.0f, 10).single() as SegmentEvent.Completed
        assertEquals(1.toShort(), done.pcm.first())
        assertEquals(2.toShort(), done.pcm[VadSettings.FRAME_SAMPLES])
    }
    @Test fun nanProbabilityCountsAsSilence() {
        val seg = UtteranceSegmenter(VadSettings())
        assertTrue(seg.feed(Float.NaN, 10).isEmpty())
    }
}
