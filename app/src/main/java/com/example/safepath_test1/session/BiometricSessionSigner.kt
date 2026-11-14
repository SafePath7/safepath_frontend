package com.example.safepath_test1.session

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Session-scoped signing keys. The server must verify signatures and enforce one-time challenges. */
object BiometricSessionSigner {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val PREFIX = "safepath_session_"

    data class Registration(val keyAlias: String, val publicKeyDer: ByteArray)

    /** API 24–29 deliberately use the password fallback: strong-only cannot be enforced here. */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Call once when the user opts in at session start; send only publicKeyDer to the server. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createKey(context: Context): Registration {
        check(isAvailable(context)) { "강한 생체인증을 사용할 수 없습니다. 비밀번호를 사용하세요." }
        val alias = PREFIX + UUID.randomUUID()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return Registration(alias, generator.generateKeyPair().public.encoded)
    }

    /** challenge must come from the server and be bound to the session and requested action. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun signChallenge(
        activity: Activity,
        keyAlias: String,
        challenge: ByteArray,
        onSigned: (ByteArray) -> Unit,
        onFallback: () -> Unit,
    ): CancellationSignal {
        require(keyAlias.startsWith(PREFIX))
        require(challenge.isNotEmpty())
        val cancellation = CancellationSignal()
        val completed = AtomicBoolean(false)
        val challengeBytes = challenge.copyOf()
        val fallback = { if (completed.compareAndSet(false, true)) onFallback() }
        try {
            check(isAvailable(activity))
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = store.getKey(keyAlias, null) as java.security.PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key) }
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("생체인증")
                .setSubtitle("안전귀가 요청을 인증합니다")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("취소", activity.mainExecutor) { _, _ -> fallback() }
                .build()
            prompt.authenticate(BiometricPrompt.CryptoObject(signature), cancellation, activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticated = requireNotNull(result.cryptoObject?.signature)
                            authenticated.update(challengeBytes)
                            val signed = authenticated.sign()
                            if (completed.compareAndSet(false, true)) onSigned(signed)
                        } catch (_: Exception) { fallback() }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = fallback()
                })
        } catch (_: Exception) { fallback() }
        return cancellation
    }

    fun deleteKey(keyAlias: String) {
        require(keyAlias.startsWith(PREFIX))
        KeyStore.getInstance(KEYSTORE).apply { load(null); deleteEntry(keyAlias) }
    }
}
