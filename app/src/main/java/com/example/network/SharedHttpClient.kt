package com.example.network

import com.example.security.AntiTamperSecurity
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized base OkHttpClient providing a shared ConnectionPool and Dispatcher thread pool
 * across repositories, image loading, and streaming helpers.
 *
 * Call [newBuilder] to derive specialized clients with custom timeouts or interceptors
 * without spawning extra thread pools or duplicating TCP/TLS handshakes.
 */
object SharedHttpClient {
    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { AntiTamperSecurity.applyToOkHttpBuilder(this) }
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun newBuilder(): OkHttpClient.Builder = baseClient.newBuilder()
}
