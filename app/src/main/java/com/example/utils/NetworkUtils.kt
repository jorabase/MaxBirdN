package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object NetworkUtils {

    /**
     * Checks if device is connected to any network interface (Wi-Fi, Cellular, Ethernet)
     */
    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Actively tests if real internet is reachable.
     * Detects cases where WiFi or Mobile Data is ON, but internet is NOT working
     * (e.g. captive portal, exhausted data plan, disconnected router, no external DNS/IP routing).
     */
    suspend fun isInternetReachable(context: Context, timeoutMs: Int = 2500): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkConnected(context)) {
            return@withContext false
        }

        // Method 1: Standard Android Captive Portal check (clients3.google.com/generate_204)
        try {
            val url = URL("https://clients3.google.com/generate_204")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            if (code == 204 || code == 200) {
                return@withContext true
            }
        } catch (_: Exception) {
            // Fall through to socket check
        }

        // Method 2: Fast raw socket probe to Google Public DNS (8.8.8.8:53)
        try {
            Socket().use { socket ->
                val socketAddress = InetSocketAddress("8.8.8.8", 53)
                socket.connect(socketAddress, timeoutMs)
                return@withContext true
            }
        } catch (_: Exception) {
            // Fall through
        }

        // Method 3: Backup raw socket probe to Cloudflare DNS (1.1.1.1:53)
        try {
            Socket().use { socket ->
                val socketAddress = InetSocketAddress("1.1.1.1", 53)
                socket.connect(socketAddress, timeoutMs)
                return@withContext true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Observes real-time network connectivity changes
     */
    fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            override fun onLost(network: Network) {
                // Re-check active network so Wi-Fi <-> Cellular handoff doesn't flash false offline
                trySend(isNetworkConnected(context))
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
            trySend(isNetworkConnected(context))
        } catch (e: Exception) {
            trySend(isNetworkConnected(context))
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }.distinctUntilChanged()
}
