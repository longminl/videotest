package com.videocollect.app.api

import com.google.gson.GsonBuilder
import com.videocollect.app.api.models.ApiResult
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var baseUrl: String = "http://localhost:8080/"
    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun updateBaseUrl(host: String, port: Int) {
        val url = if (port == 80) "http://$host/" else "http://$host:$port/"
        if (url == baseUrl) return
        baseUrl = url
        retrofit = null
        apiService = null
    }

    fun getBaseUrl(): String = baseUrl

    fun getApiService(): ApiService {
        if (apiService == null) {
            val r = getRetrofit()
            apiService = r.create(ApiService::class.java)
        }
        return apiService!!
    }

    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        return retrofit!!
    }

    fun testConnection(host: String, port: Int, callback: (Boolean, String) -> Unit) {
        val url = if (port == 80) "http://$host/" else "http://$host:$port/"
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder()
            .url("${url}api/list?page=1&pageSize=1")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.message ?: "连接失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        callback(true, "连接成功")
                    } else {
                        callback(false, "服务器返回 ${it.code}")
                    }
                }
            }
        })
    }
}
