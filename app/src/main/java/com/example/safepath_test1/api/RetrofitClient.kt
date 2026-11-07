package com.example.safepath_test1.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    /** FD-002. The agreed server URL and token source must be supplied by the caller. */
    fun create(baseUrl: String, token: () -> String, onUnauthorized: () -> Unit): SafePathApi {
        require(baseUrl.startsWith("https://")) { "API 서버 주소는 HTTPS여야 합니다." }
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val bearer = token().trim()
            if (bearer.isEmpty()) throw java.io.IOException("로그인이 필요합니다.")
            val response = chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer $bearer").build())
            if (response.code() == 401) onUnauthorized()
            response
        }.build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafePathApi::class.java)
    }
}
