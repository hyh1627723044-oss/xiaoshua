package io.github.hyh1627723044.shortvideokws

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

class SecretCodecTest {
    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()

    @Test fun roundTripsUnicodeSecrets() {
        val stored = SecretCodec.encrypt(key, "JEV_API_KEY", "jv_live_密钥-123")
        assertTrue(stored.startsWith("v1:"))
        assertFalse(stored.contains("jv_live"))
        assertEquals("jv_live_密钥-123", SecretCodec.decrypt(key, "JEV_API_KEY", stored))
    }
    @Test fun everyEncryptionUsesAFreshNonce() {
        val a = SecretCodec.encrypt(key, "n", "same")
        val b = SecretCodec.encrypt(key, "n", "same")
        assertNotEquals(a, b)
        assertNotEquals(Base64.getDecoder().decode(a.drop(3)).take(12), Base64.getDecoder().decode(b.drop(3)).take(12))
    }
    @Test fun tamperedTruncatedWrongKeyOrMovedValuesReadAsMissing() {
        val stored = SecretCodec.encrypt(key, "ASR_API_KEY", "secret")
        val bytes = Base64.getDecoder().decode(stored.drop(3))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        val tampered = "v1:" + Base64.getEncoder().encodeToString(bytes)
        assertNull(SecretCodec.decrypt(key, "ASR_API_KEY", tampered))
        assertNull(SecretCodec.decrypt(key, "ASR_API_KEY", stored.dropLast(8)))
        assertNull(SecretCodec.decrypt(newKey(), "ASR_API_KEY", stored))
        assertNull(SecretCodec.decrypt(key, "JEV_API_KEY", stored))   // AAD binds the field name
        listOf("", "v1:", "v1:!!!", "v2:" + stored.drop(3), "secret").forEach {
            assertNull(it, SecretCodec.decrypt(key, "ASR_API_KEY", it))
        }
    }
}
