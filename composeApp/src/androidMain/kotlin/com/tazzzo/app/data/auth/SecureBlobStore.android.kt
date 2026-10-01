package com.tazzzo.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Application context holder; set once from `MainActivity` before any secure read. */
object AndroidAppContext {
    @Volatile private var ctx: Context? = null
    fun init(context: Context) { ctx = context.applicationContext }
    fun get(): Context = checkNotNull(ctx) { "AndroidAppContext not initialised" }
}

actual fun createSecureBlobStore(): SecureBlobStore = KeystoreBlobStore(AndroidAppContext.get())

/**
 * AES-256-GCM with a NON-EXPORTABLE Android Keystore key; the ciphertext
 * (`iv || ciphertext+tag`, base64) sits in private SharedPreferences that are
 * excluded from backup (see `backup_rules.xml` / `data_extraction_rules.xml`).
 * If the key is gone (restore, new device, key invalidation) decryption throws
 * and the caller wipes the entry: the customer is simply signed out.
 */
internal class KeystoreBlobStore(private val context: Context) : SecureBlobStore {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(): String? {
        val stored = prefs.getString(ENTRY, null) ?: return null
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        require(raw.size > IV_BYTES) { "blob too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    override fun write(value: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // Keystore generates a fresh random IV
        val out = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(ENTRY, Base64.encodeToString(out, Base64.NO_WRAP)).commit()
    }

    @Synchronized
    override fun delete() {
        prefs.edit().remove(ENTRY).commit()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "tazzzo.auth.key.v1"
        const val PREFS = "tazzzo_secure_auth"
        const val ENTRY = "session.v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
