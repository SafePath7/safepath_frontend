package com.example.safepath_test1.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            val googleIdOption =
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(
                        context.getString(com.example.safepath_test1.R.string.default_web_client_id)
                    )
                    .build()

            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

            val result = credentialManager.getCredential(context = context, request = request)

            val credential = result.credential

            if (
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.idToken, null)

                auth.signInWithCredential(firebaseCredential).await()

                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Google credential이 아닙니다."))
            }
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("사용 가능한 Google 계정을 찾지 못했습니다.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
