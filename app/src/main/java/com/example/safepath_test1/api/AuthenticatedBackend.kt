package com.example.safepath_test1.api

import android.content.Context
import com.example.safepath_test1.BuildConfig
import com.example.safepath_test1.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Shares authentication and transport setup while preserving each API's timeout. */
internal class AuthenticatedBackend(context: Context, private val readTimeoutSeconds: Long) {
    private val applicationContext = context.applicationContext
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun <T> createApi(type: Class<T>): T {
        val url = checkedAccountBaseUrl(applicationContext.getString(R.string.safepath_api_base_url), BuildConfig.DEBUG)
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder().baseUrl(url).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(type)
    }

    suspend fun <T> request(forceRefresh: Boolean = false, block: suspend (String) -> T): T {
        val user = auth.currentUser ?: error("로그인이 필요합니다.")
        val uid = user.uid
        val token = user.getIdToken(forceRefresh).await().token ?: error("로그인 인증을 확인할 수 없습니다.")
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        val result = try {
            block("Bearer $token")
        } catch (error: HttpException) {
            if (error.code() == 401 && auth.currentUser?.uid == uid) auth.signOut()
            throw error
        }
        check(auth.currentUser?.uid == uid) { "로그인 계정이 변경되었습니다." }
        return result
    }
}
