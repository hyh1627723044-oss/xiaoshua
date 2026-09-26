package io.github.hyh1627723044.shortvideokws

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

// A 16 kHz mono PCM16 frame consumer. Frames are a reused buffer; copy anything kept.
interface FrameSink : AutoCloseable {
    fun accept(frame: ShortArray)
}

// The only AudioRecord in the app. Blocks the calling worker thread until running is cleared.
object AudioCapture {
    @SuppressLint("MissingPermission")
    fun run(running: AtomicBoolean, onReady: () -> Unit, onFrame: (ShortArray) -> Unit) {
        val rate = VadSettings.SAMPLE_RATE
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "设备不支持 16kHz 录音" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes * 2, 6400))
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "无法初始化麦克风" }
            if (!running.get()) return
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "无法开始录音" }
            onReady()
            val pcm = ShortArray(VadSettings.FRAME_SAMPLES) // 32 ms, one Silero window.
            while (running.get()) {
                var filled = 0
                while (filled < pcm.size && running.get()) {
                    val n = recorder.read(pcm, filled, pcm.size - filled, AudioRecord.READ_BLOCKING)
                    check(n > 0) { "麦克风读取失败：$n" }
                    filled += n
                }
                if (running.get()) onFrame(pcm)
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }
}
