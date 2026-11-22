package com.example.safepath_test1.session

import java.security.MessageDigest
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Local end confirmation; the server does not validate it. */
object JourneyEndAuth {
    var biometricKeyAlias: String? = null
    private var pinDigest: ByteArray? = null
    private const val keyAlias = "safepath_journey_pin_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256).build())
        return generator.generateKey()
    }
    private fun digest(pin: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(key())
        doFinal(pin.toByteArray(Charsets.UTF_8))
    }
    private fun preferenceKey(uid: String, sessionId: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest("$uid:$sessionId".toByteArray()), Base64.NO_WRAP)

    val isAvailable: Boolean get() = biometricKeyAlias != null || pinDigest != null

    fun setEndPin(pin: String) {
        if (pin.isEmpty()) { pinDigest = null; return }
        require(pin.matches(Regex("[0-9]{4}")))
        pinDigest = digest(pin)
    }

    fun verifyEndPin(pin: String): Boolean = pinDigest?.let {
        runCatching { MessageDigest.isEqual(it, digest(pin)) }.getOrDefault(false)
    } ?: false

    fun save(context: Context, uid: String, sessionId: String): Boolean {
        val value = pinDigest ?: return true
        return context.getSharedPreferences("journey_end_auth", Context.MODE_PRIVATE).edit()
            .putString(preferenceKey(uid, sessionId), Base64.encodeToString(value, Base64.NO_WRAP)).commit()
    }

    fun restore(context: Context, uid: String, sessionId: String) {
        pinDigest = null
        val value = context.getSharedPreferences("journey_end_auth", Context.MODE_PRIVATE)
            .getString(preferenceKey(uid, sessionId), null) ?: return
        pinDigest = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
    }

    fun forget(context: Context, uid: String, sessionId: String) {
        context.getSharedPreferences("journey_end_auth", Context.MODE_PRIVATE).edit()
            .remove(preferenceKey(uid, sessionId)).apply()
    }

    fun clear() {
        pinDigest = null
        biometricKeyAlias?.let { runCatching { BiometricSessionSigner.deleteKey(it) } }
        biometricKeyAlias = null
    }
}
