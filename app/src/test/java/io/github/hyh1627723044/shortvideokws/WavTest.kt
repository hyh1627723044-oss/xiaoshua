package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    @Test fun writesCanonicalPcm16MonoHeader() {
        val wav = Wav.encodePcm16Mono(shortArrayOf(1, -2, 0x1234))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44 + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 6, b.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt())      // PCM
        assertEquals(1, b.getShort(22).toInt())      // mono
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))            // byte rate
        assertEquals(2, b.getShort(32).toInt())      // block align
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(6, b.getInt(40))
        assertEquals(-2, b.getShort(46).toInt())
        assertEquals(0x34.toByte(), wav[48])         // little-endian samples
    }
}
