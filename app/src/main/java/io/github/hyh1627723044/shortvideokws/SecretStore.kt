package io.github.hyh1627723044.shortvideokws

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SecretName { ASR_API_KEY, ASR_ACCESS_TOKEN, JEV_API_KEY }

// AES-GCM with a fresh cipher-generated IV per value; the field name is bound as AAD.
object SecretCodec {
    private const val PREFIX = "v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, name: String, plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == IV_BYTES)
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    // Unknown versions, truncation, tampering or a wrong key all read as "not configured".
    fun decrypt(key: SecretKey, name: String, stored: String): String? {
        if (!stored.startsWith(PREFIX)) return null
        val bytes = try { Base64.getDecoder().decode(stored.substring(PREFIX.length)) } catch (e: IllegalArgumentException) { return null }
        if (bytes.size < IV_BYTES + TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) { null }
    }
}

// Ciphertexts live in private prefs; the non-exportable key lives in Android Keystore.
// This does not protect secrets on a rooted or otherwise compromised device.
class SecretStore(context: Context) {
    companion object { private const val ALIAS = "xiaoshua_cloud_secrets_v1" }
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    fun get(name: SecretName): String? {
        val stored = prefs.getString(name.name, null) ?: return null
        // Keystore failures (including ProviderException) also read as "not configured".
        return try { SecretCodec.decrypt(key(), name.name, stored) } catch (e: Exception) { null }
    }
    fun has(name: SecretName) = get(name) != null
    fun put(name: SecretName, value: String) {
        prefs.edit().putString(name.name, SecretCodec.encrypt(key(), name.name, value)).apply()
    }
    fun remove(vararg names: SecretName) {
        prefs.edit().apply { names.forEach { remove(it.name) } }.apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
