package com.example.liveclass

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class LiveClassWebSocketManager {

    companion object {
        private const val TAG = "LiveClassWS"
        private const val BASE_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive for WebSocket
        .build()

    private var webSocket: WebSocket? = null

    private val _masterUrl = MutableStateFlow<String?>(null)
    val masterUrl: StateFlow<String?> = _masterUrl.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun connect(
        token: String,
        studentName: String = "Student"
    ) {
        disconnect()

        val peerUuid = UUID.randomUUID().toString()
        val wsUrl = "$BASE_WS_URL?peer=$peerUuid&token=$token&protocol_version=2.5&protocol_spec=20240720"

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened successfully")
                _isConnected.value = true
                _error.value = null

                // Send join message
                val joinMessage = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "join")
                    put("params", JSONObject().apply {
                        put("name", studentName.ifBlank { "Student" })
                        put("disableVidAutoSub", true)
                        put("simulcast", true)
                    })
                    put("jsonrpc", "2.0")
                }

                Log.d(TAG, "Sending join message: ${joinMessage.toString()}")
                ws.send(joinMessage.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val method = msg.optString("method")

                    if (method == "peer-list") {
                        Log.d(TAG, "Received peer-list message")
                        val params = msg.optJSONObject("params")
                        val room = params?.optJSONObject("room")
                        val streaming = room?.optJSONObject("streaming")
                        val hls = streaming?.optJSONObject("hls")
                        val variants = hls?.optJSONArray("variants")

                        if (variants != null && variants.length() > 0) {
                            val variant = variants.optJSONObject(0)
                            val url = variant?.optString("url")
                            if (!url.isNullOrBlank()) {
                                Log.d(TAG, "Found Master Link: $url")
                                _masterUrl.value = url
                            }
                        }

                        // Calculate initial peer/viewer count
                        val peers = params?.optJSONObject("peers")
                        val initialCount = peers?.length() ?: 0
                        if (initialCount > 0) {
                            _viewerCount.value = initialCount
                        }
                    }

                    if (method == "session-info") {
                        val params = msg.optJSONObject("params")
                        val peerCount = params?.optInt("peer_count", 0) ?: 0
                        if (peerCount > 0) {
                            _viewerCount.value = peerCount
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message: ${e.message}", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                _isConnected.value = false
                _error.value = t.message ?: "WebSocket connection failed"
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                _isConnected.value = false
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Leaving Live Class")
            webSocket = null
            _isConnected.value = false
            _masterUrl.value = null
            _viewerCount.value = 0
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket: ${e.message}", e)
        }
    }
}
