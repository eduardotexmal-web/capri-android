package io.github.emmanuelquintana.capri

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Her files, as on the desktop: config.json (config.js), memory.json (main.js), and her custom
 * sounds folder. API keys are sealed with a key kept in Android's Keystore, as the desktop seals
 * them with your Windows account: "enc:<base64 iv + ciphertext>".
 */
class Store(private val context: Context) {
    private val dir: File get() = context.filesDir

    val soundsDir: File get() = File(context.getExternalFilesDir(null) ?: dir, "sounds").apply { mkdirs() }

    fun read(name: String): String? = try {
        File(dir, name).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun write(name: String, text: String) {
        // written next to it, then swapped in: a crash halfway never leaves her settings broken
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }

    /** A text setting straight from config.json (the app's screen: who answers her). */
    fun configString(key: String): String? = try {
        org.json.JSONObject(read(CONFIG) ?: "{}").optString(key).ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /** A setting straight from config.json, for the Kotlin side (boot, notification). */
    fun configFlag(key: String, default: Boolean): Boolean = try {
        val json = org.json.JSONObject(read(CONFIG) ?: "{}")
        if (json.has(key)) json.getBoolean(key) else default
    } catch (_: Exception) {
        default
    }

    fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            "enc:" + Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (_: Exception) {
            plain
        }
    }

    fun open(stored: String): String {
        if (!stored.startsWith("enc:")) return stored
        return try {
            val raw = Base64.decode(stored.substring(4), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        const val CONFIG = "config.json"
        const val MEMORY = "memory.json"
        private const val ALIAS = "capri-api-keys"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
