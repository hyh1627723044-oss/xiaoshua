package io.github.hyh1627723044.shortvideokws

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    // Canonical 44-byte RIFF header, PCM16 little-endian, mono.
    fun encodePcm16Mono(pcm: ShortArray, sampleRate: Int = VadSettings.SAMPLE_RATE): ByteArray {
        val dataBytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        buffer.putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (sample in pcm) buffer.putShort(sample)
        return buffer.array()
    }
}
