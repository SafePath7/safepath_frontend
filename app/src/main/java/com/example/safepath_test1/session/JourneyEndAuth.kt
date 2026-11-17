package com.example.safepath_test1.session

import java.security.MessageDigest
import java.security.SecureRandom

/** Local end confirmation for the current app process; the server does not validate it. */
object JourneyEndAuth {
    var biometricKeyAlias: String? = null
    private val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var pinDigest: ByteArray? = null

    val isAvailable: Boolean get() = biometricKeyAlias != null || pinDigest != null

    fun setEndPin(pin: String) {
        if (pin.isEmpty()) { pinDigest = null; return }
        require(pin.matches(Regex("[0-9]{4}")))
        pinDigest = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
    }

    fun verifyEndPin(pin: String): Boolean = pinDigest?.let {
        MessageDigest.isEqual(it, MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()))
    } ?: false

    fun clear() {
        pinDigest = null
        biometricKeyAlias?.let { runCatching { BiometricSessionSigner.deleteKey(it) } }
        biometricKeyAlias = null
    }
}
