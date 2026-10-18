package com.example.liveclass

import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.liveclass.poll.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LiveClassWebSocketManager {

    companion object {
        private const val TAG = "LiveClassWS"
        private const val DEFAULT_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
        private const val INIT_ENDPOINT_URL = "https://prod-init.100ms.live/init"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var hlsPollWatcherJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var shouldAutoReconnect: Boolean = false
    private var reconnectAttempts: Int = 0
    private var lastAuthToken: String = ""

    private var currentPeerId: String = ""
    private var currentUserId: String = ""
    private var currentStudentName: String = ""
    private var currentPeerRole: String = "student"
    private var handRaisedAtTimestamp: Long = 0L

    private data class RpcRequestMeta(
        val method: String,
        val pollId: String = "",
        val extra: String = "",
        val fallbackBroadcastPayload: String? = null
    )

    private val pendingRpcRequests = ConcurrentHashMap<String, RpcRequestMeta>()
    private val pendingChatCallbacks = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val peerNamesMap = ConcurrentHashMap<String, String>()
    private val peerRolesMap = ConcurrentHashMap<String, String>()
    private val recentlySentClientMsgIds = ConcurrentHashMap<String, Long>()

    private val _masterUrl = MutableStateFlow<String?>(null)
    val masterUrl: StateFlow<String?> = _masterUrl.asStateFlow()

    private val _candidateUrls = MutableStateFlow<List<String>>(emptyList())
    val candidateUrls: StateFlow<List<String>> = _candidateUrls.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingChatMessages = MutableStateFlow<LiveChatMessage?>(null)
    val incomingChatMessages: StateFlow<LiveChatMessage?> = _incomingChatMessages.asStateFlow()

    private val _receivedMessagesHistory = MutableStateFlow<List<LiveChatMessage>>(emptyList())
    val receivedMessagesHistory: StateFlow<List<LiveChatMessage>> = _receivedMessagesHistory.asStateFlow()

    private val _pinnedMessage = MutableStateFlow<LiveChatMessage?>(null)
    val pinnedMessage: StateFlow<LiveChatMessage?> = _pinnedMessage.asStateFlow()

    private val _teacherName = MutableStateFlow("")
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    private val _roomTitle = MutableStateFlow("")
    val roomTitle: StateFlow<String> = _roomTitle.asStateFlow()

    // Real-time Chat Enabled/Disabled State controlled by Teacher (from on-metadata-change "chatState")
    private val _isChatEnabled = MutableStateFlow(true)
    val isChatEnabled: StateFlow<Boolean> = _isChatEnabled.asStateFlow()

    private val _chatStateUpdatedBy = MutableStateFlow("")
    val chatStateUpdatedBy: StateFlow<String> = _chatStateUpdatedBy.asStateFlow()

    // Real-time Hand Raise State (from group-join / group-leave "_handraise" & peer-update)
    private val _isHandRaised = MutableStateFlow(false)
    val isHandRaised: StateFlow<Boolean> = _isHandRaised.asStateFlow()

    private val _raisedHandsCount = MutableStateFlow(0)
    val raisedHandsCount: StateFlow<Int> = _raisedHandsCount.asStateFlow()

    // 100ms Real Live Poll / Quiz State Flows
    private val _activePoll = MutableStateFlow<LivePollItem?>(null)
    val activePoll: StateFlow<LivePollItem?> = _activePoll.asStateFlow()

    private val _previousPolls = MutableStateFlow<List<LivePollItem>>(emptyList())
    val previousPolls: StateFlow<List<LivePollItem>> = _previousPolls.asStateFlow()

    private val _pollLeaderboard = MutableStateFlow<Map<String, List<LivePollLeaderboardEntry>>>(emptyMap())
    val pollLeaderboard: StateFlow<Map<String, List<LivePollLeaderboardEntry>>> = _pollLeaderboard.asStateFlow()

    fun getCurrentPeerId(): String = currentPeerId
    fun getCurrentUserId(): String = currentUserId
    fun getCurrentStudentName(): String = currentStudentName

    fun setMasterUrl(url: String, viewers: Int = 1) {
        if (url.isBlank()) return
        _masterUrl.value = url
        if (_viewerCount.value <= 0) {
            _viewerCount.value = viewers.coerceAtLeast(1)
        }
        _isConnected.value = true
        _error.value = null
        startHlsMetadataWatcher(url)
    }

    fun addCandidateUrls(urls: List<String>) {
        val current = _candidateUrls.value.toMutableList()
        current.addAll(urls.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) })
        _candidateUrls.value = current.distinct()
    }

    private fun parseJwtPayload(token: String): JSONObject? {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decodedBytes = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                JSONObject(String(decodedBytes, Charsets.UTF_8))
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode JWT payload: ${e.message}")
            null
        }
    }

    private fun buildUserAgentV2(): String {
        val sdkInt = Build.VERSION.SDK_INT
        val model = Build.MODEL?.replace(",", "")?.replace(" ", "_") ?: "Android"
        return "os:android,os_version:$sdkInt,sdk:android,sdk_version:2.9.81,device_model:$model,env:prod,is_prebuilt:true"
    }

    private fun fetchWsEndpointFromInit(token: String, userAgentV2Encoded: String): String {
        return try {
            val req = Request.Builder()
                .url("$INIT_ENDPOINT_URL?user_agent_v2=$userAgentV2Encoded")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("accept-type", "application/json")
                .addHeader("user-agent", "okhttp/5.3.2")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val ep = json.optString("endpoint").trim()
                    if (ep.isNotBlank() && !ep.equals("null", ignoreCase = true)) ep else DEFAULT_WS_URL
                } else {
                    DEFAULT_WS_URL
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Init endpoint lookup fallback: ${e.message}")
            DEFAULT_WS_URL
        }
    }

    fun connect(
        token: String,
        studentName: String = ""
    ) {
        if (token.isBlank()) return
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (_: Exception) {
        }
        webSocket = null

        shouldAutoReconnect = true
        reconnectAttempts = 0
        lastAuthToken = token
        currentPeerId = UUID.randomUUID().toString()
        currentStudentName = studentName.trim()

        val jwtJson = parseJwtPayload(token)
        currentUserId = jwtJson?.optString("user_id").orEmpty().ifBlank {
            jwtJson?.optString("sub").orEmpty()
        }
        val tokenRole = jwtJson?.optString("role").orEmpty().trim()
        if (tokenRole.isNotBlank() && !tokenRole.equals("null", ignoreCase = true)) {
            currentPeerRole = tokenRole
        }

        if (currentStudentName.isNotBlank()) {
            if (currentPeerId.isNotBlank()) peerNamesMap[currentPeerId] = currentStudentName
            if (currentUserId.isNotBlank()) peerNamesMap[currentUserId] = currentStudentName
        }

        openSocketInternal(token)
    }

    private fun openSocketInternal(token: String) {
        scope.launch {
            val rawUa = buildUserAgentV2()
            val encodedUa = URLEncoder.encode(rawUa, "UTF-8")
            val baseWsEndpoint = fetchWsEndpointFromInit(token, encodedUa)

            val wsUrl =
                "$baseWsEndpoint?peer=$currentPeerId&token=$token&user_agent_v2=$encodedUa&protocol_version=2.5&protocol_spec=20240720"
            Log.d(TAG, "Connecting to 100ms WebSocket: $wsUrl")

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("User-Agent", "okhttp/5.3.2")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "100ms WebSocket Opened as '$currentStudentName'")
                    reconnectAttempts = 0
                    _isConnected.value = true
                    _error.value = null

                    val effectiveName = currentStudentName.trim().ifBlank { "Student" }
                    val joinId = UUID.randomUUID().toString()
                    pendingRpcRequests[joinId] = RpcRequestMeta("join")
                    val joinMessage = JSONObject().apply {
                        put("id", joinId)
                        put("method", "join")
                        put("params", JSONObject().apply {
                            put("name", effectiveName)
                            put("data", JSONObject().apply {
                                put("isBRBOn", false)
                                put("name", effectiveName)
                                if (_isHandRaised.value && handRaisedAtTimestamp > 0L) {
                                    put("handRaisedAt", handRaisedAtTimestamp)
                                }
                            }.toString())
                            put("disableVidAutoSub", true)
                            put("simulcast", true)
                        })
                        put("jsonrpc", "2.0")
                    }
                    ws.send(joinMessage.toString())

                    requestPollList("started")
                    requestPollList("stopped")
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleWebSocketMessage(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                    _isConnected.value = false
                    if (shouldAutoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && lastAuthToken.isNotBlank()) {
                        val attempt = ++reconnectAttempts
                        val backoffMs = (1500L * attempt).coerceAtMost(8000L)
                        reconnectJob?.cancel()
                        reconnectJob = scope.launch {
                            delay(backoffMs)
                            if (shouldAutoReconnect) {
                                Log.d(TAG, "Auto-reconnecting WebSocket (attempt $attempt)...")
                                openSocketInternal(lastAuthToken)
                            }
                        }
                    } else if (_masterUrl.value.isNullOrBlank()) {
                        _error.value = t.message ?: "লাইভ সার্ভারের সাথে সংযোগ বিচ্ছিন্ন হয়েছে"
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket Closed: $code / $reason")
                    _isConnected.value = false
                }
            })
        }
    }

    /**
     * Exact 100ms Hand Raise toggle matching protocol:
     * 1. Raise: "group-join" ("name": "_handraise") + "peer-update" ({"handRaisedAt": ts, "isBRBOn": false, "name": ...})
     * 2. Lower: "group-leave" ("name": "_handraise", "version": "1.0") + "peer-update" ({"isBRBOn": false, "name": ...})
     */
    fun toggleHandRaise(studentName: String = currentStudentName) {
        val ws = webSocket ?: return
        val newTargetState = !_isHandRaised.value
        _isHandRaised.value = newTargetState
        val displayName = studentName.trim().ifBlank { currentStudentName.ifBlank { "Student" } }
        currentStudentName = displayName

        try {
            if (newTargetState) {
                handRaisedAtTimestamp = System.currentTimeMillis()
                val joinGroupId = UUID.randomUUID().toString()
                pendingRpcRequests[joinGroupId] = RpcRequestMeta("group-join", extra = "_handraise")
                val joinGroupMsg = JSONObject().apply {
                    put("id", joinGroupId)
                    put("method", "group-join")
                    put("params", JSONObject().apply {
                        put("name", "_handraise")
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(joinGroupMsg.toString())

                val peerUpdateId = UUID.randomUUID().toString()
                pendingRpcRequests[peerUpdateId] = RpcRequestMeta("peer-update")
                val peerDataStr = JSONObject().apply {
                    put("handRaisedAt", handRaisedAtTimestamp)
                    put("isBRBOn", false)
                    put("name", displayName)
                }.toString()

                val peerUpdateMsg = JSONObject().apply {
                    put("id", peerUpdateId)
                    put("method", "peer-update")
                    put("params", JSONObject().apply {
                        put("data", peerDataStr)
                        put("version", "1.0")
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(peerUpdateMsg.toString())
            } else {
                handRaisedAtTimestamp = 0L
                val leaveGroupId = UUID.randomUUID().toString()
                pendingRpcRequests[leaveGroupId] = RpcRequestMeta("group-leave", extra = "_handraise")
                val leaveGroupMsg = JSONObject().apply {
                    put("id", leaveGroupId)
                    put("method", "group-leave")
                    put("params", JSONObject().apply {
                        put("name", "_handraise")
                        put("version", "1.0")
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(leaveGroupMsg.toString())

                val peerUpdateId = UUID.randomUUID().toString()
                pendingRpcRequests[peerUpdateId] = RpcRequestMeta("peer-update")
                val peerDataStr = JSONObject().apply {
                    put("isBRBOn", false)
                    put("name", displayName)
                }.toString()

                val peerUpdateMsg = JSONObject().apply {
                    put("id", peerUpdateId)
                    put("method", "peer-update")
                    put("params", JSONObject().apply {
                        put("data", peerDataStr)
                        put("version", "1.0")
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(peerUpdateMsg.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle hand raise: ${e.message}")
        }
    }

    fun requestPollList(state: String = "started", count: Int = 50) {
        val ws = webSocket ?: return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("poll-list", state)
            val msg = JSONObject().apply {
                put("id", reqId)
                put("method", "poll-list")
                put("params", JSONObject().apply {
                    put("count", count)
                    put("state", state)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            ws.send(msg.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send poll-list ($state): ${e.message}")
        }
    }

    fun loadFullPollDetails(pollId: String) {
        if (pollId.isBlank()) return
        fetchPollQuestions(pollId)
        fetchUserPollResponse(pollId)
        fetchPollLeaderboard(pollId)
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val method = msg.optString("method")
            val msgId = msg.optString("id")

            when (method) {
                "on-policy-change" -> {
                    val params = msg.optJSONObject("params")
                    val peer = params?.optJSONObject("peer")
                    val peerId = peer?.optString("peer_id").orEmpty()
                    if (peerId.isNotBlank()) currentPeerId = peerId
                    val peerRole = peer?.optString("role").orEmpty().trim()
                    if (peerRole.isNotBlank() && !peerRole.equals("null", ignoreCase = true)) {
                        currentPeerRole = peerRole
                        if (currentPeerId.isNotBlank()) peerRolesMap[currentPeerId] = peerRole
                    }
                    val info = peer?.optJSONObject("info")
                    val uid = info?.optString("user_id").orEmpty()
                    if (uid.isNotBlank()) currentUserId = uid

                    if (currentStudentName.isNotBlank()) {
                        if (currentPeerId.isNotBlank()) peerNamesMap[currentPeerId] = currentStudentName
                        if (currentUserId.isNotBlank()) peerNamesMap[currentUserId] = currentStudentName
                    }

                    val groupsArr = peer?.optJSONArray("groups")
                    if (groupsArr != null) {
                        var handRaised = false
                        for (i in 0 until groupsArr.length()) {
                            if (groupsArr.optString(i) == "_handraise") {
                                handRaised = true
                                break
                            }
                        }
                        _isHandRaised.value = handRaised
                    }
                }

                "on-peer-update" -> {
                    val params = msg.optJSONObject("params")
                    val pId = params?.optString("peer_id").orEmpty()
                    val pRole = params?.optString("role").orEmpty()
                    val info = params?.optJSONObject("info")
                    val pName = info?.optString("name").orEmpty()
                    val uId = info?.optString("user_id").orEmpty()
                    if (pName.isNotBlank()) {
                        if (pId.isNotBlank()) peerNamesMap[pId] = pName
                        if (uId.isNotBlank()) peerNamesMap[uId] = pName
                    }
                    if (pRole.isNotBlank() && pId.isNotBlank()) {
                        peerRolesMap[pId] = pRole
                    }

                    val groupsArr = params?.optJSONArray("groups")
                    if (pId == currentPeerId && groupsArr != null) {
                        var handRaised = false
                        for (i in 0 until groupsArr.length()) {
                            if (groupsArr.optString(i) == "_handraise") {
                                handRaised = true
                                break
                            }
                        }
                        _isHandRaised.value = handRaised
                    }
                }

                "on-peer-join" -> {
                    val params = msg.optJSONObject("params")
                    val peerObj = params?.optJSONObject("peer") ?: params
                    val pId = peerObj?.optString("peer_id").orEmpty()
                    val pRole = peerObj?.optString("role").orEmpty()
                    val info = peerObj?.optJSONObject("info")
                    val pName = info?.optString("name").orEmpty()
                    val uId = info?.optString("user_id").orEmpty()
                    if (pName.isNotBlank()) {
                        if (pId.isNotBlank()) peerNamesMap[pId] = pName
                        if (uId.isNotBlank()) peerNamesMap[uId] = pName
                    }
                    if (pRole.isNotBlank() && pId.isNotBlank()) {
                        peerRolesMap[pId] = pRole
                    }
                    if (pRole.equals("teacher", ignoreCase = true) && pName.isNotBlank()) {
                        _teacherName.value = pName
                        updatePollsTeacherName(pId, pName)
                    }
                    if (_viewerCount.value > 0) {
                        _viewerCount.value += 1
                    }
                }

                "on-peer-leave" -> {
                    if (_viewerCount.value > 1) {
                        _viewerCount.value -= 1
                    }
                }

                "room-info" -> {
                    val params = msg.optJSONObject("params")
                    val desc = params?.optString("description").orEmpty()
                    if (desc.isNotBlank()) {
                        val cleanTitle = desc.substringBeforeLast(" - ").trim()
                        if (cleanTitle.isNotBlank()) {
                            _roomTitle.value = cleanTitle
                        }
                    }
                }

                "peer-list" -> {
                    val params = msg.optJSONObject("params")
                    val room = params?.optJSONObject("room")
                    val streaming = room?.optJSONObject("streaming")
                    val hls = streaming?.optJSONObject("hls")
                    val variants = hls?.optJSONArray("variants")

                    if (variants != null && variants.length() > 0) {
                        val url = variants.optJSONObject(0)?.optString("url")
                        if (!url.isNullOrBlank()) {
                            _masterUrl.value = url
                            startHlsMetadataWatcher(url)
                        }
                    }

                    val peersObj = params?.optJSONObject("peers")
                    if (peersObj != null) {
                        val keys = peersObj.keys()
                        var count = 0
                        var handRaiseCount = 0
                        while (keys.hasNext()) {
                            val key = keys.next()
                            count++
                            val pObj = peersObj.optJSONObject(key) ?: continue
                            val pId = pObj.optString("peer_id", key)
                            val pRole = pObj.optString("role")
                            val info = pObj.optJSONObject("info")
                            val pName = info?.optString("name").orEmpty()
                            val uId = info?.optString("user_id").orEmpty()

                            val groups = pObj.optJSONArray("groups")
                            if (groups != null) {
                                for (g in 0 until groups.length()) {
                                    if (groups.optString(g) == "_handraise") {
                                        handRaiseCount++
                                        break
                                    }
                                }
                            }

                            if (pName.isNotBlank()) {
                                if (pId.isNotBlank()) peerNamesMap[pId] = pName
                                if (uId.isNotBlank()) peerNamesMap[uId] = pName
                            }
                            if (pRole.isNotBlank() && pId.isNotBlank()) {
                                peerRolesMap[pId] = pRole
                            }
                            if (pRole.equals("teacher", ignoreCase = true) && pName.isNotBlank()) {
                                _teacherName.value = pName
                                updatePollsTeacherName(pId, pName)
                            }
                        }
                        _raisedHandsCount.value = handRaiseCount
                        if (count > 0 && _viewerCount.value == 0) {
                            _viewerCount.value = count
                        }
                    }
                }

                "session-info" -> {
                    val params = msg.optJSONObject("params")
                    val peerCount = params?.optInt("peer_count", 0) ?: 0
                    if (peerCount > 0) {
                        _viewerCount.value = peerCount
                    }
                }

                "on-metadata-change" -> {
                    val params = msg.optJSONObject("params")
                    val values = params?.optJSONArray("values")
                    if (values != null) {
                        for (i in 0 until values.length()) {
                            val vObj = values.optJSONObject(i) ?: continue
                            val key = vObj.optString("key")

                            // 1. Check chatState (enabled / disabled by Teacher)
                            if (key == "chatState") {
                                val dataObj = vObj.optJSONObject("data")
                                if (dataObj != null) {
                                    val enabled = dataObj.optBoolean("enabled", true)
                                    _isChatEnabled.value = enabled
                                    val updatedByObj = dataObj.optJSONObject("updatedBy")
                                    val teacherUser = updatedByObj?.optString("userName").orEmpty()
                                    if (teacherUser.isNotBlank()) {
                                        _chatStateUpdatedBy.value = teacherUser
                                        if (_teacherName.value.isBlank()) {
                                            _teacherName.value = teacherUser
                                        }
                                    }
                                }
                            }

                            // 2. Check pinnedMessages
                            if (key == "pinnedMessages") {
                                val dataArr = vObj.optJSONArray("data")
                                if (dataArr == null || dataArr.length() == 0) {
                                    _pinnedMessage.value = null
                                } else {
                                    for (j in 0 until dataArr.length()) {
                                        val pinObj = dataArr.optJSONObject(j) ?: continue
                                        val pinId = pinObj.optString("id").ifBlank { UUID.randomUUID().toString() }
                                        val pinnedBy = pinObj.optString(
                                            "pinnedBy",
                                            _teacherName.value.ifBlank { "শিক্ষক" }
                                        )
                                        val rawText = pinObj.optString("text").trim()
                                        if (pinnedBy.isNotBlank() && _teacherName.value.isBlank()) {
                                            _teacherName.value = pinnedBy
                                        }
                                        if (rawText.isNotBlank()) {
                                            val cleanMsg = rawText.removePrefix("$pinnedBy:").trim()
                                            val pinnedChatMsg = LiveChatMessage(
                                                id = "pin_$pinId",
                                                senderName = pinnedBy,
                                                message = cleanMsg,
                                                timestamp = "পিন করা ঘোষণা",
                                                isTeacher = true,
                                                role = "teacher",
                                                targetRole = "all",
                                                isPinned = true
                                            )
                                            _pinnedMessage.value = pinnedChatMsg
                                            appendIncomingMessage(pinnedChatMsg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "on-poll-start" -> {
                    val params = msg.optJSONObject("params")
                    val pollsArr = params?.optJSONArray("polls")
                    if (pollsArr != null) {
                        handlePollsArray(pollsArr, forceState = "started")
                    }
                }

                "on-poll-stop" -> {
                    val params = msg.optJSONObject("params")
                    val pollsArr = params?.optJSONArray("polls")
                    if (pollsArr != null) {
                        handlePollsArray(pollsArr, forceState = "stopped")
                    }
                }

                "on-poll-stats" -> {
                    val params = msg.optJSONObject("params")
                    val pollsArr = params?.optJSONArray("polls")
                    if (pollsArr != null && pollsArr.length() > 0) {
                        for (i in 0 until pollsArr.length()) {
                            val stObj = pollsArr.optJSONObject(i) ?: continue
                            val pollId = stObj.optString("poll_id")
                            if (pollId.isBlank()) continue

                            val totalResp = stObj.optInt("total_response", 0)
                            val userCount = stObj.optInt("user_count", 0)
                            val maxUser = stObj.optInt("max_user", 0)
                            val qStatsArr = stObj.optJSONArray("questions")

                            updatePollWithStats(pollId, totalResp, userCount, maxUser, qStatsArr)
                        }
                    }
                }

                "on-broadcast", "broadcast", "on-message", "message", "on-chat-message", "chat-message" -> {
                    parseIncomingChatMessage(msg.optJSONObject("params") ?: msg)
                }
            }

            val reqMeta = if (msgId.isNotBlank()) pendingRpcRequests.remove(msgId) else null
            val errorObj = msg.optJSONObject("error")
            if (errorObj != null && reqMeta?.method == "broadcast") {
                val errMsg = errorObj.optString("message")
                Log.w(TAG, "100ms broadcast RPC error for $msgId: $errMsg")
                val fallbackPayload = reqMeta.fallbackBroadcastPayload
                val ws = webSocket
                if (!fallbackPayload.isNullOrBlank() && ws != null && _isConnected.value) {
                    Log.d(TAG, "Retrying broadcast with room-wide fallback payload...")
                    val fallbackSent = ws.send(fallbackPayload)
                    pendingChatCallbacks.remove(msgId)?.invoke(fallbackSent)
                } else {
                    pendingChatCallbacks.remove(msgId)?.invoke(false)
                }
            }

            val result = msg.optJSONObject("result")
            if (result != null) {
                if (reqMeta?.method == "broadcast") {
                    pendingChatCallbacks.remove(msgId)?.invoke(true)
                }
                handleRpcResult(result, reqMeta)
            }

            if (_masterUrl.value.isNullOrBlank() && text.contains(".m3u8", ignoreCase = true)) {
                val regex = Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")
                val match = regex.find(text)?.value
                if (!match.isNullOrBlank()) {
                    val cleanUrl = match.replace("\\/", "/")
                    _masterUrl.value = cleanUrl
                    startHlsMetadataWatcher(cleanUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WebSocket message: ${e.message}", e)
        }
    }

    private fun handleRpcResult(result: JSONObject, reqMeta: RpcRequestMeta?) {
        // Check group-join / group-leave response for _handraise
        if (reqMeta?.method == "group-join" || reqMeta?.method == "group-leave") {
            val groupsArr = result.optJSONArray("groups")
            if (groupsArr != null) {
                var raised = false
                for (i in 0 until groupsArr.length()) {
                    if (groupsArr.optString(i) == "_handraise") {
                        raised = true
                        break
                    }
                }
                _isHandRaised.value = raised
            }
        }

        val room = result.optJSONObject("room")
        val streaming = room?.optJSONObject("streaming") ?: result.optJSONObject("streaming")
        val variants = streaming?.optJSONObject("hls")?.optJSONArray("variants")
        if (variants != null && variants.length() > 0) {
            val url = variants.optJSONObject(0)?.optString("url")
            if (!url.isNullOrBlank()) {
                _masterUrl.value = url
                startHlsMetadataWatcher(url)
            }
        }

        val pollsArr = result.optJSONArray("polls")
        if (pollsArr != null) {
            handlePollsArray(pollsArr, forceState = null)
        }

        val pollId = result.optString("poll_id").ifBlank { reqMeta?.pollId.orEmpty() }

        val submissionResultArr = result.optJSONArray("result")
        if (submissionResultArr != null && pollId.isNotBlank()) {
            val firstRes = submissionResultArr.optJSONObject(0)
            if (firstRes != null) {
                val qIdx = firstRes.optInt("question", 1)
                val isCorrect = firstRes.optBoolean("correct", false)
                val respId = firstRes.optString("response_id")

                updatePollState(pollId) { existing ->
                    val updatedUserResp = existing.userResponse?.copy(
                        responseId = respId.ifBlank { existing.userResponse.responseId },
                        questionIndex = qIdx,
                        isCorrect = isCorrect,
                        userName = currentStudentName.ifBlank { existing.userResponse.userName }
                    )
                    existing.copy(userResponse = updatedUserResp)
                }
                fetchUserPollResponse(pollId)
                fetchPollLeaderboard(pollId)
            }
        }

        val questionsArr = result.optJSONArray("questions")
        if (questionsArr != null && pollId.isNotBlank()) {
            val isLeaderboardResponse = reqMeta?.method == "poll-leaderboard" ||
                result.has("voted_users") ||
                result.has("total_users") ||
                result.has("avg_time") ||
                (questionsArr.length() > 0 && questionsArr.optJSONObject(0)?.has("position") == true)

            if (isLeaderboardResponse) {
                parseLeaderboardResult(pollId, result, questionsArr)
            } else {
                parsePollQuestionsResult(pollId, questionsArr)
            }
        } else if (reqMeta?.method == "poll-leaderboard" && pollId.isNotBlank()) {
            parseLeaderboardResult(pollId, result, JSONArray())
        }

        val responsesArr = result.optJSONArray("responses")
        if (responsesArr != null && pollId.isNotBlank()) {
            parseUserPollResponses(pollId, responsesArr)
        }
    }

    private fun handlePollsArray(pollsArr: JSONArray, forceState: String?) {
        for (i in 0 until pollsArr.length()) {
            val pObj = pollsArr.optJSONObject(i) ?: continue
            val pollId = pObj.optString("poll_id").ifBlank { pObj.optString("id") }
            if (pollId.isBlank()) continue

            val title = pObj.optString("title", "")
            val state = forceState ?: pObj.optString("state", "started")
            val type = pObj.optString("type", "quiz")
            val createdBy = pObj.optString("created_by")
            val startedBy = pObj.optString("started_by")
            val stoppedBy = pObj.optString("stopped_by")
            val resolvedTeacher = peerNamesMap[createdBy]
                ?: peerNamesMap[startedBy]
                ?: _teacherName.value

            val existing = _previousPolls.value.find { it.pollId == pollId }
                ?: _activePoll.value?.takeIf { it.pollId == pollId }

            val mergedPoll = (existing ?: LivePollItem(pollId = pollId, title = title)).copy(
                title = title.ifBlank { existing?.title ?: pollId },
                state = state,
                type = type,
                createdBy = createdBy.ifBlank { existing?.createdBy.orEmpty() },
                startedBy = startedBy.ifBlank { existing?.startedBy.orEmpty() },
                stoppedBy = stoppedBy.ifBlank { existing?.stoppedBy.orEmpty() },
                teacherName = resolvedTeacher.ifBlank { existing?.teacherName.orEmpty() },
                roomId = pObj.optString("room_id").ifBlank { existing?.roomId.orEmpty() },
                sessionId = pObj.optString("session_id").ifBlank { existing?.sessionId.orEmpty() },
                anonymous = pObj.optBoolean("anonymous", existing?.anonymous ?: false),
                visibility = pObj.optBoolean("visibility", existing?.visibility ?: true),
                locked = pObj.optBoolean("locked", existing?.locked ?: true),
                mode = pObj.optString("mode", existing?.mode.orEmpty()),
                createdAt = pObj.optLong("created_at", existing?.createdAt ?: 0L),
                startedAt = pObj.optLong("started_at", existing?.startedAt ?: 0L),
                stoppedAt = pObj.optLong("stopped_at", existing?.stoppedAt ?: 0L),
                updatedAt = pObj.optLong("updated_at", existing?.updatedAt ?: 0L),
                durationSeconds = pObj.optInt("duration", existing?.durationSeconds ?: 0),
                questionsCount = pObj.optInt("questions_count", existing?.questionsCount ?: 1).coerceAtLeast(1)
            )

            upsertPollInLists(mergedPoll)

            fetchPollQuestions(pollId)
            fetchUserPollResponse(pollId)
            fetchPollLeaderboard(pollId)
        }
    }

    private fun parsePollQuestionsResult(pollId: String, questionsArr: JSONArray) {
        val parsedQuestions = mutableListOf<LivePollQuestion>()
        for (qIdx in 0 until questionsArr.length()) {
            val qEntry = questionsArr.optJSONObject(qIdx) ?: continue
            val qObj = qEntry.optJSONObject("question") ?: qEntry

            val qIndex = qObj.optInt("index", qIdx + 1)
            val qText = qObj.optString("text", "")
            val qType = qObj.optString("type", "single-choice")
            val qWeight = qObj.optInt("weight", 10)
            val qSkippable = qObj.optBoolean("skippable", false)
            val qOnce = qObj.optBoolean("once", false)
            val qNegative = qObj.optBoolean("negative", false)
            val qDuration = qObj.optInt("duration", 0)

            val optionsArr = qEntry.optJSONArray("options") ?: qObj.optJSONArray("options")
            val parsedOptions = mutableListOf<LivePollOption>()
            if (optionsArr != null) {
                for (oIdx in 0 until optionsArr.length()) {
                    val optObj = optionsArr.optJSONObject(oIdx) ?: continue
                    parsedOptions.add(
                        LivePollOption(
                            index = optObj.optInt("index", oIdx + 1),
                            text = optObj.optString("text", ""),
                            weight = optObj.optInt("weight", 0)
                        )
                    )
                }
            }

            val ansObj = qEntry.optJSONObject("answer") ?: qObj.optJSONObject("answer")
            val ansHidden = ansObj?.optBoolean("hidden", false) ?: false
            val correctOptionsList = mutableListOf<Int>()
            val ansOptionsArr = ansObj?.optJSONArray("options")
            if (ansOptionsArr != null) {
                for (aIdx in 0 until ansOptionsArr.length()) {
                    val v = ansOptionsArr.optInt(aIdx, 0)
                    if (v > 0) correctOptionsList.add(v)
                }
            }
            val correctOpt = when {
                ansObj != null && ansObj.has("option") && !ansObj.isNull("option") -> {
                    val optVal = ansObj.optInt("option", 0)
                    if (optVal > 0) optVal else correctOptionsList.firstOrNull()
                }
                else -> correctOptionsList.firstOrNull()
            }
            if (correctOpt != null && !correctOptionsList.contains(correctOpt)) {
                correctOptionsList.add(0, correctOpt)
            }

            parsedQuestions.add(
                LivePollQuestion(
                    index = qIndex,
                    text = qText,
                    type = qType,
                    skippable = qSkippable,
                    once = qOnce,
                    weight = qWeight,
                    negative = qNegative,
                    durationSeconds = qDuration,
                    options = parsedOptions,
                    correctOptionIndex = correctOpt,
                    correctOptionIndices = correctOptionsList,
                    answerHidden = ansHidden
                )
            )
        }

        if (parsedQuestions.isNotEmpty()) {
            val firstQ = parsedQuestions.first()
            updatePollState(pollId) { existing ->
                val mergedQuestions = parsedQuestions.map { newQ ->
                    val oldQ = existing.questions.find { it.index == newQ.index }
                    if (oldQ != null) {
                        newQ.copy(
                            totalVotes = oldQ.totalVotes,
                            correctVotes = oldQ.correctVotes,
                            skippedVotes = oldQ.skippedVotes,
                            options = newQ.options.map { newOpt ->
                                val oldOpt = oldQ.options.find { it.index == newOpt.index }
                                if (oldOpt != null) newOpt.copy(votesCount = oldOpt.votesCount) else newOpt
                            }
                        )
                    } else newQ
                }

                val updatedUserResp = existing.userResponse?.let { resp ->
                    if (firstQ.correctOptionIndex != null) {
                        resp.copy(isCorrect = resp.selectedOptionIndex == firstQ.correctOptionIndex)
                    } else resp
                }

                val derivedTitle = existing.title.takeIf { it.isNotBlank() && it != existing.pollId }
                    ?: firstQ.text.removeSuffix("?").trim().ifBlank { existing.title }

                existing.copy(
                    title = derivedTitle,
                    questions = mergedQuestions,
                    userResponse = updatedUserResp
                )
            }
        }
    }

    private fun parseUserPollResponses(pollId: String, responsesArr: JSONArray) {
        if (responsesArr.length() == 0) return
        val rEntry = responsesArr.optJSONObject(0) ?: return
        val peerObj = rEntry.optJSONObject("peer")
        val rObj = rEntry.optJSONObject("response") ?: rEntry

        val chosenOpt = rObj.optInt("option", 0)
        val optionsJsonArr = rObj.optJSONArray("options")
        val selectedIndices = mutableListOf<Int>()
        if (optionsJsonArr != null) {
            for (i in 0 until optionsJsonArr.length()) {
                val v = optionsJsonArr.optInt(i, 0)
                if (v > 0) selectedIndices.add(v)
            }
        }
        val effectiveChosenOpt = if (chosenOpt > 0) chosenOpt else (selectedIndices.firstOrNull() ?: 0)
        if (effectiveChosenOpt <= 0) return
        if (!selectedIndices.contains(effectiveChosenOpt)) {
            selectedIndices.add(0, effectiveChosenOpt)
        }

        val resolvedUserName = currentStudentName.ifBlank {
            peerObj?.optString("username").orEmpty()
        }

        val userResp = LivePollUserResponse(
            responseId = rObj.optString("response_id"),
            pollId = pollId,
            questionIndex = rObj.optInt("question", 1),
            selectedOptionIndex = effectiveChosenOpt,
            selectedOptionIndices = selectedIndices,
            durationMs = rObj.optLong("duration", 0L),
            isCorrect = rObj.optBoolean("correct", false),
            skipped = rObj.optBoolean("skipped", false),
            update = rObj.optBoolean("update", false),
            peerId = peerObj?.optString("peerid").orEmpty().ifBlank { currentPeerId },
            userId = peerObj?.optString("userid").orEmpty().ifBlank { currentUserId },
            userName = resolvedUserName
        )

        updatePollState(pollId) { existing ->
            val qCorrectOpt = existing.questions.firstOrNull()?.correctOptionIndex
            val verifiedCorrect = if (rObj.has("correct")) {
                userResp.isCorrect
            } else if (qCorrectOpt != null) {
                effectiveChosenOpt == qCorrectOpt
            } else {
                false
            }
            existing.copy(userResponse = userResp.copy(isCorrect = verifiedCorrect))
        }
    }

    private fun parseLeaderboardResult(pollId: String, result: JSONObject, questionsArr: JSONArray) {
        val entries = mutableListOf<LivePollLeaderboardEntry>()
        for (k in 0 until questionsArr.length()) {
            val row = questionsArr.optJSONObject(k) ?: continue
            val peer = row.optJSONObject("peer")
            val peerId = peer?.optString("peerid").orEmpty()
            val userId = peer?.optString("userid").orEmpty()

            val isCurrentStudent = (currentUserId.isNotBlank() && userId.equals(currentUserId, ignoreCase = true)) ||
                (currentPeerId.isNotBlank() && peerId.equals(currentPeerId, ignoreCase = true))

            val rawPeerName = peer?.optString("username")?.takeIf { it.isNotBlank() }
                ?: peer?.optString("name")?.takeIf { it.isNotBlank() }
                ?: row.optString("username").takeIf { it.isNotBlank() }
                ?: peerNamesMap[peerId]
                ?: peerNamesMap[userId]
                ?: ""

            val effectivePeerName = if (isCurrentStudent && currentStudentName.isNotBlank()) {
                currentStudentName
            } else {
                rawPeerName
            }

            val durMs = row.optLong("duration", 0L)
            val durSec = durMs / 1000.0

            entries.add(
                LivePollLeaderboardEntry(
                    position = row.optInt("position", k + 1),
                    questionIndex = row.optInt("question", 0),
                    peerId = peerId,
                    userId = userId,
                    userName = effectivePeerName,
                    score = row.optInt("score", 0),
                    totalScore = 10,
                    durationMs = durMs,
                    durationSeconds = durSec,
                    correctResponses = row.optInt("correct_responses", 0),
                    totalResponses = row.optInt("total_responses", 1).coerceAtLeast(1)
                )
            )
        }

        val totalUsers = result.optInt("total_users", 0)
        val votedUsers = result.optInt("voted_users", 0)
        val correctUsers = result.optInt("correct_users", 0)
        val avgTime = result.optDouble("avg_time", 0.0)
        val avgScore = result.optDouble("avg_score", 0.0)

        val currentMap = _pollLeaderboard.value.toMutableMap()
        currentMap[pollId] = entries
        _pollLeaderboard.value = currentMap

        updatePollState(pollId) { existing ->
            val myLeaderboardRow = entries.find { entry ->
                (currentUserId.isNotBlank() && entry.userId.equals(currentUserId, ignoreCase = true)) ||
                    (currentPeerId.isNotBlank() && entry.peerId.equals(currentPeerId, ignoreCase = true)) ||
                    (currentStudentName.isNotBlank() && entry.userName.equals(currentStudentName, ignoreCase = true))
            }

            val inferredUserResponse = existing.userResponse ?: myLeaderboardRow?.let { myRow ->
                val correctOpt = existing.questions.firstOrNull()?.correctOptionIndex ?: 0
                LivePollUserResponse(
                    pollId = pollId,
                    questionIndex = 1,
                    selectedOptionIndex = if (myRow.correctResponses > 0 && correctOpt > 0) correctOpt else 0,
                    durationMs = myRow.durationMs,
                    isCorrect = myRow.correctResponses > 0,
                    peerId = myRow.peerId,
                    userId = myRow.userId,
                    userName = currentStudentName.ifBlank { myRow.userName }
                )
            }

            existing.copy(
                leaderboard = entries,
                totalUsers = if (totalUsers > 0) totalUsers else existing.totalUsers,
                votedUsers = if (votedUsers > 0) votedUsers else existing.votedUsers,
                correctUsers = if (correctUsers > 0) correctUsers else existing.correctUsers,
                avgTimeMs = if (avgTime > 0.0) avgTime else existing.avgTimeMs,
                avgScore = if (avgScore > 0.0) avgScore else existing.avgScore,
                userResponse = inferredUserResponse
            )
        }
    }

    private fun updatePollWithStats(
        pollId: String,
        totalResp: Int,
        userCount: Int,
        maxUser: Int,
        qStatsArr: JSONArray?
    ) {
        updatePollState(pollId) { existing ->
            val updatedQuestions = if (qStatsArr != null && qStatsArr.length() > 0) {
                val qStat = qStatsArr.optJSONObject(0)
                val optVotesArr = qStat?.optJSONArray("options")
                val correctCount = qStat?.optInt("correct", 0) ?: 0
                val skippedCount = qStat?.optInt("skipped", 0) ?: 0
                val qTotal = qStat?.optInt("total", totalResp) ?: totalResp

                existing.questions.mapIndexed { idx, q ->
                    if (idx == 0) {
                        val updatedOpts = q.options.mapIndexed { oIdx, opt ->
                            val vCount = optVotesArr?.optInt(oIdx, opt.votesCount) ?: opt.votesCount
                            opt.copy(votesCount = vCount)
                        }
                        q.copy(
                            options = updatedOpts,
                            totalVotes = qTotal,
                            correctVotes = correctCount,
                            skippedVotes = skippedCount
                        )
                    } else q
                }
            } else {
                existing.questions
            }

            existing.copy(
                totalResponses = totalResp,
                votedUsers = userCount,
                maxUsers = maxUser,
                questions = updatedQuestions
            )
        }
    }

    private fun updatePollsTeacherName(teacherPeerId: String, teacherName: String) {
        _activePoll.value?.let { active ->
            if (active.createdBy == teacherPeerId || active.teacherName.isBlank()) {
                _activePoll.value = active.copy(teacherName = teacherName)
            }
        }
        _previousPolls.value = _previousPolls.value.map { p ->
            if (p.createdBy == teacherPeerId || p.teacherName.isBlank()) {
                p.copy(teacherName = teacherName)
            } else p
        }
    }

    private fun upsertPollInLists(poll: LivePollItem) {
        if (poll.isLive) {
            _activePoll.value = poll
        } else if (_activePoll.value?.pollId == poll.pollId) {
            _activePoll.value = poll
        }

        val currentList = _previousPolls.value.toMutableList()
        val idx = currentList.indexOfFirst { it.pollId == poll.pollId }
        if (idx >= 0) {
            currentList[idx] = poll
        } else {
            currentList.add(poll)
        }
        currentList.sortByDescending { it.pollId.toLongOrNull() ?: it.createdAt }
        _previousPolls.value = currentList
    }

    private fun updatePollState(pollId: String, transform: (LivePollItem) -> LivePollItem) {
        val currentActive = _activePoll.value
        if (currentActive != null && currentActive.pollId == pollId) {
            _activePoll.value = transform(currentActive)
        }

        val currentList = _previousPolls.value.toMutableList()
        val idx = currentList.indexOfFirst { it.pollId == pollId }
        if (idx >= 0) {
            val updated = transform(currentList[idx])
            currentList[idx] = updated
            currentList.sortByDescending { it.pollId.toLongOrNull() ?: it.createdAt }
            _previousPolls.value = currentList
        } else {
            val created = transform(
                LivePollItem(
                    pollId = pollId,
                    title = pollId,
                    state = "stopped",
                    teacherName = _teacherName.value
                )
            )
            currentList.add(created)
            currentList.sortByDescending { it.pollId.toLongOrNull() ?: it.createdAt }
            _previousPolls.value = currentList
        }
    }

    private fun startHlsMetadataWatcher(masterPlaylistUrl: String) {
        hlsPollWatcherJob?.cancel()
        hlsPollWatcherJob = scope.launch {
            val streamPlaylistUrl = if (masterPlaylistUrl.endsWith("master.m3u8", ignoreCase = true)) {
                masterPlaylistUrl.substringBeforeLast("/") + "/stream_0/stream.m3u8"
            } else {
                masterPlaylistUrl
            }

            while (isActive) {
                try {
                    val req = Request.Builder()
                        .url(streamPlaylistUrl)
                        .addHeader("Accept-Encoding", "gzip")
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val m3u8Text = resp.body?.string().orEmpty()
                            parseHlsDateRangePollPayloads(m3u8Text)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(4000)
            }
        }
    }

    private fun parseHlsDateRangePollPayloads(m3u8Text: String) {
        val regex = Regex("X-100MSLIVE-PAYLOAD=\"([^\"]+)\"")
        regex.findAll(m3u8Text).forEach { matchResult ->
            val b64 = matchResult.groupValues.getOrNull(1).orEmpty()
            if (b64.isNotBlank()) {
                try {
                    val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    val payloadJson = JSONObject(decoded)
                    val payloadVal = payloadJson.optString("payload")
                    if (payloadVal.startsWith("poll:")) {
                        val pollId = payloadVal.substringAfter("poll:").trim()
                        if (pollId.isNotBlank() && _previousPolls.value.none { it.pollId == pollId }) {
                            loadFullPollDetails(pollId)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun fetchPollQuestions(pollId: String) {
        val ws = webSocket ?: return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("poll-questions-get", pollId)
            val req = JSONObject().apply {
                put("id", reqId)
                put("method", "poll-questions-get")
                put("params", JSONObject().apply {
                    put("index", 0)
                    put("count", 50)
                    put("poll_id", pollId)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request poll questions: ${e.message}")
        }
    }

    fun fetchUserPollResponse(pollId: String) {
        val ws = webSocket ?: return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("poll-responses", pollId)
            val req = JSONObject().apply {
                put("id", reqId)
                put("method", "poll-responses")
                put("params", JSONObject().apply {
                    put("index", 0)
                    put("count", 50)
                    put("self", true)
                    put("poll_id", pollId)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch user poll response: ${e.message}")
        }
    }

    fun fetchPollLeaderboard(pollId: String) {
        val ws = webSocket ?: return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("poll-leaderboard", pollId)
            val req = JSONObject().apply {
                put("id", reqId)
                put("method", "poll-leaderboard")
                put("params", JSONObject().apply {
                    put("count", 200)
                    put("includeCurrentPeer", false)
                    put("poll_id", pollId)
                    put("position", 0)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch poll leaderboard: ${e.message}")
        }
    }

    fun submitPollAnswer(
        pollId: String,
        questionIndex: Int,
        optionIndex: Int,
        durationMs: Long
    ) {
        val currentPoll = _activePoll.value?.takeIf { it.pollId == pollId }
            ?: _previousPolls.value.firstOrNull { it.pollId == pollId }
        val question = currentPoll?.questions?.firstOrNull { it.index == questionIndex }
            ?: currentPoll?.questions?.firstOrNull()
        val isCorrect = question?.correctOptionIndex != null && question.correctOptionIndex == optionIndex

        val localUserResp = LivePollUserResponse(
            pollId = pollId,
            questionIndex = questionIndex,
            selectedOptionIndex = optionIndex,
            selectedOptionIndices = listOf(optionIndex),
            durationMs = durationMs,
            isCorrect = isCorrect,
            peerId = currentPeerId,
            userId = currentUserId,
            userName = currentStudentName
        )

        updatePollState(pollId) { existing ->
            existing.copy(userResponse = localUserResp)
        }

        val ws = webSocket ?: return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("poll-response", pollId)

            val responsesArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "")
                    put("duration", durationMs)
                    put("question", questionIndex)
                    put("type", question?.type ?: "single-choice")
                    put("option", optionIndex)
                    put("skipped", false)
                    put("update", false)
                })
            }

            val req = JSONObject().apply {
                put("id", reqId)
                put("method", "poll-response")
                put("params", JSONObject().apply {
                    put("poll_id", pollId)
                    put("responses", responsesArr)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit poll answer: ${e.message}")
        }
    }

    private fun appendIncomingMessage(message: LiveChatMessage) {
        _incomingChatMessages.value = message
        val currentList = _receivedMessagesHistory.value
        val existingIdx = currentList.indexOfFirst { it.id == message.id }
        if (existingIdx >= 0) {
            val mutable = currentList.toMutableList()
            mutable[existingIdx] = message
            _receivedMessagesHistory.value = mutable
        } else {
            _receivedMessagesHistory.value = currentList + message
        }
    }

    private fun parseIncomingChatMessage(params: JSONObject?) {
        if (params == null) return
        val peerObj = params.optJSONObject("peer")
        val peerIdFromParams = params.optString("peer_id")
            .ifBlank { peerObj?.optString("peer_id").orEmpty() }
            .ifBlank { if (peerObj == null) params.optString("peer") else "" }
            .trim()

        // Ignore our own echoed broadcast messages so we don't duplicate local optimistic messages
        if (peerIdFromParams.isNotBlank() && peerIdFromParams == currentPeerId) {
            return
        }

        var chatText: String? = null
        var sender: String? = null
        var role: String? = null
        var targetRole: String? = null
        var clientMsgId: String? = null
        var senderPeerIdFromInfo: String? = null
        var timestampMillis = System.currentTimeMillis()

        val infoRaw = params.opt("info") ?: params.opt("data")
        when (infoRaw) {
            is JSONObject -> {
                clientMsgId = infoRaw.optString("clientMsgId").takeIf { it.isNotBlank() }
                senderPeerIdFromInfo = infoRaw.optString("senderPeerId").takeIf { it.isNotBlank() }
                chatText = infoRaw.optString("message").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("msg").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("text").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("body").takeIf { it.isNotBlank() }
                sender = infoRaw.optString("sender").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("senderName").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("sender_name").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("name").takeIf { it.isNotBlank() }
                role = infoRaw.optString("role").takeIf { it.isNotBlank() }
                targetRole = infoRaw.optString("targetRole").takeIf { it.isNotBlank() }
                if (infoRaw.has("time")) timestampMillis = infoRaw.optLong("time", timestampMillis)
            }
            is String -> {
                if (infoRaw.trim().startsWith("{") && infoRaw.trim().endsWith("}")) {
                    try {
                        val infoJson = JSONObject(infoRaw)
                        clientMsgId = infoJson.optString("clientMsgId").takeIf { it.isNotBlank() }
                        senderPeerIdFromInfo = infoJson.optString("senderPeerId").takeIf { it.isNotBlank() }
                        chatText = infoJson.optString("message").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("msg").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("text").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("body").takeIf { it.isNotBlank() }
                        sender = infoJson.optString("sender").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("senderName").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("sender_name").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("name").takeIf { it.isNotBlank() }
                        role = infoJson.optString("role").takeIf { it.isNotBlank() }
                        targetRole = infoJson.optString("targetRole").takeIf { it.isNotBlank() }
                        if (infoJson.has("time")) timestampMillis = infoJson.optLong("time", timestampMillis)
                    } catch (_: Exception) {
                        chatText = infoRaw
                    }
                } else {
                    chatText = infoRaw
                }
            }
        }

        if (!senderPeerIdFromInfo.isNullOrBlank() && senderPeerIdFromInfo == currentPeerId) {
            return
        }

        if (!clientMsgId.isNullOrBlank() && recentlySentClientMsgIds.containsKey(clientMsgId)) {
            return
        }

        if (chatText.isNullOrBlank()) {
            chatText = params.optString("message").takeIf { it.isNotBlank() }
                ?: params.optString("msg").takeIf { it.isNotBlank() }
                ?: params.optString("text").takeIf { it.isNotBlank() }
        }

        val resolvedPeerId = peerIdFromParams.ifBlank { senderPeerIdFromInfo.orEmpty() }

        if (sender.isNullOrBlank()) {
            val peerInfoObj = peerObj?.optJSONObject("info")
            sender = peerObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: peerInfoObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: peerNamesMap[resolvedPeerId]
                ?: params.optString("sender").takeIf { it.isNotBlank() }
                ?: params.optString("from").takeIf { it.isNotBlank() }
                ?: "শিক্ষার্থী"
        } else if (resolvedPeerId.isNotBlank()) {
            peerNamesMap[resolvedPeerId] = sender
        }

        val peerActualRole = peerObj?.optString("role")?.takeIf { it.isNotBlank() }
            ?: peerRolesMap[resolvedPeerId]

        val rolesArr = params.optJSONArray("roles")
        val rolesFromParams = if (rolesArr != null && rolesArr.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until rolesArr.length()) {
                val r = rolesArr.optString(i).trim()
                if (r.isNotBlank()) list.add(r)
            }
            when {
                list.size > 1 -> "all"
                list.firstOrNull()?.contains("teacher", ignoreCase = true) == true -> "teacher"
                list.firstOrNull()?.contains("student", ignoreCase = true) == true -> "student"
                else -> list.firstOrNull()
            }
        } else {
            null
        }

        if (role.isNullOrBlank()) {
            role = peerActualRole ?: rolesFromParams ?: params.optString("role").takeIf { it.isNotBlank() }
        }

        val isSenderActualTeacher = peerActualRole?.contains("teacher", ignoreCase = true) == true ||
            (_teacherName.value.isNotBlank() && sender?.equals(_teacherName.value, ignoreCase = true) == true)

        val resolvedTargetRole = when {
            !targetRole.isNullOrBlank() -> when {
                targetRole.contains("teacher", ignoreCase = true) -> "teacher"
                targetRole.equals("*") || targetRole.contains("all", ignoreCase = true) -> "all"
                else -> "student"
            }
            !rolesFromParams.isNullOrBlank() -> rolesFromParams
            role?.contains("teacher", ignoreCase = true) == true && !isSenderActualTeacher -> "teacher"
            role?.equals("*") == true || role?.contains("all", ignoreCase = true) == true -> "all"
            isSenderActualTeacher -> "all"
            else -> "student"
        }

        val normalizedRole = when {
            isSenderActualTeacher -> "teacher"
            role?.equals("*") == true || role?.contains("all", ignoreCase = true) == true -> "all"
            else -> "student"
        }

        if (!chatText.isNullOrBlank()) {
            val formattedTime = try {
                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestampMillis))
            } catch (_: Exception) {
                "এখন"
            }

            val incomingMsg = LiveChatMessage(
                id = clientMsgId ?: UUID.randomUUID().toString(),
                senderName = sender ?: "শিক্ষার্থী",
                message = chatText,
                timestamp = formattedTime,
                isTeacher = isSenderActualTeacher,
                role = normalizedRole,
                targetRole = resolvedTargetRole,
                senderPeerId = resolvedPeerId,
                isMine = false
            )
            appendIncomingMessage(incomingMsg)
        }
    }

    /**
     * Sends broadcast message using the 100ms JSON-RPC protocol.
     * Supports targeting "teacher", "student", or "all" participants.
     * Uses exact valid room role names ("teacher", "student") so 100ms never rejects the broadcast!
     */
    fun sendChatMessage(
        text: String,
        studentName: String,
        role: String = "teacher",
        targetRole: String = "teacher",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val ws = webSocket
        if (ws == null || !_isConnected.value) {
            Log.w(TAG, "Cannot send chat: WebSocket is null or not connected")
            onComplete?.invoke(false)
            return
        }
        try {
            val reqId = UUID.randomUUID().toString()
            val clientMsgId = UUID.randomUUID().toString()
            val nowMs = System.currentTimeMillis()
            recentlySentClientMsgIds[clientMsgId] = nowMs

            val effectiveSenderName = studentName.trim().ifBlank { currentStudentName.ifBlank { "Student" } }
            currentStudentName = effectiveSenderName

            val activeStudentRole = currentPeerRole.trim().ifBlank { "student" }.let {
                if (it.equals("teacher", ignoreCase = true)) "student" else it
            }

            val targetRolesArray = JSONArray().apply {
                when (targetRole.lowercase().trim()) {
                    "teacher" -> {
                        put("teacher")
                    }
                    "student", "students" -> {
                        put(activeStudentRole)
                        if (!activeStudentRole.equals("student", ignoreCase = true)) {
                            put("student")
                        }
                    }
                    "all", "*" -> {
                        put("teacher")
                        put(activeStudentRole)
                        if (!activeStudentRole.equals("student", ignoreCase = true)) {
                            put("student")
                        }
                    }
                    else -> {
                        put("teacher")
                    }
                }
            }

            val infoJson = JSONObject().apply {
                put("clientMsgId", clientMsgId)
                put("msg", text)
                put("message", text)
                put("text", text)
                put("type", "chat")
                put("sender", effectiveSenderName)
                put("name", effectiveSenderName)
                put("senderName", effectiveSenderName)
                put("sender_name", effectiveSenderName)
                put("role", role)
                put("targetRole", targetRole)
                put("senderPeerId", currentPeerId)
                put("senderUserId", currentUserId)
                put("time", nowMs)
            }

            // Room-wide fallback payload (without "roles" constraint) in case template restricts role array
            val fallbackMsg = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("method", "broadcast")
                put("params", JSONObject().apply {
                    put("info", infoJson)
                })
                put("jsonrpc", "2.0")
            }.toString()

            pendingRpcRequests[reqId] = RpcRequestMeta(
                method = "broadcast",
                extra = targetRole,
                fallbackBroadcastPayload = fallbackMsg
            )
            if (onComplete != null) {
                pendingChatCallbacks[reqId] = onComplete
            }

            val broadcastMsg = JSONObject().apply {
                put("id", reqId)
                put("method", "broadcast")
                put("params", JSONObject().apply {
                    put("info", infoJson)
                    put("roles", targetRolesArray)
                })
                put("jsonrpc", "2.0")
            }

            Log.d(TAG, "Sending 100ms broadcast chat as '$effectiveSenderName' to '$targetRole' (roles=$targetRolesArray)")
            val sent = ws.send(broadcastMsg.toString())
            if (!sent) {
                pendingRpcRequests.remove(reqId)
                pendingChatCallbacks.remove(reqId)?.invoke(false)
            } else {
                // Confirm delivery after server ack or 900ms if no RPC error was returned
                scope.launch {
                    delay(900L)
                    pendingChatCallbacks.remove(reqId)?.invoke( _isConnected.value )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send chat message over WebSocket: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    fun dismissActivePoll() {
        _activePoll.value = null
    }

    /**
     * Updates the student's display name across the active live class session:
     * 1. Updates local peer & user name mappings
     * 2. Sends "change-name" and "peer-update" RPCs to 100ms WebSocket
     * 3. Updates active and previous poll responses & leaderboard entries for the student
     */
    fun updateStudentName(newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        currentStudentName = cleanName
        if (currentPeerId.isNotBlank()) peerNamesMap[currentPeerId] = cleanName
        if (currentUserId.isNotBlank()) peerNamesMap[currentUserId] = cleanName

        // Update student name in active and previous polls
        _activePoll.value?.let { active ->
            _activePoll.value = updatePollStudentNameInternal(active, cleanName)
        }
        _previousPolls.value = _previousPolls.value.map { poll ->
            updatePollStudentNameInternal(poll, cleanName)
        }

        val ws = webSocket
        if (ws != null && _isConnected.value) {
            try {
                val reqId = UUID.randomUUID().toString()
                pendingRpcRequests[reqId] = RpcRequestMeta("change-name")
                val changeNameMsg = JSONObject().apply {
                    put("id", reqId)
                    put("method", "change-name")
                    put("params", JSONObject().apply {
                        put("name", cleanName)
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(changeNameMsg.toString())

                val peerUpdateId = UUID.randomUUID().toString()
                pendingRpcRequests[peerUpdateId] = RpcRequestMeta("peer-update")
                val peerDataStr = JSONObject().apply {
                    if (_isHandRaised.value && handRaisedAtTimestamp > 0L) {
                        put("handRaisedAt", handRaisedAtTimestamp)
                    }
                    put("isBRBOn", false)
                    put("name", cleanName)
                }.toString()

                val peerUpdateMsg = JSONObject().apply {
                    put("id", peerUpdateId)
                    put("method", "peer-update")
                    put("params", JSONObject().apply {
                        put("data", peerDataStr)
                        put("version", "1.0")
                    })
                    put("jsonrpc", "2.0")
                }
                ws.send(peerUpdateMsg.toString())
                Log.d(TAG, "Synchronized student name to WebSocket: $cleanName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update peer name over WebSocket: ${e.message}")
            }
        }
    }

    private fun updatePollStudentNameInternal(poll: LivePollItem, newName: String): LivePollItem {
        val updatedResp = poll.userResponse?.copy(userName = newName)
        val updatedBoard = poll.leaderboard.map { entry ->
            val isMine = (currentUserId.isNotBlank() && entry.userId.equals(currentUserId, ignoreCase = true)) ||
                (currentPeerId.isNotBlank() && entry.peerId.equals(currentPeerId, ignoreCase = true))
            if (isMine) entry.copy(userName = newName) else entry
        }
        return poll.copy(userResponse = updatedResp, leaderboard = updatedBoard)
    }

    fun setDirectMasterUrl(url: String) {
        if (url.isNotBlank()) {
            _masterUrl.value = url
            _isConnected.value = true
            startHlsMetadataWatcher(url)
        }
    }

    fun disconnect() {
        try {
            shouldAutoReconnect = false
            reconnectAttempts = 0
            reconnectJob?.cancel()
            reconnectJob = null
            hlsPollWatcherJob?.cancel()
            hlsPollWatcherJob = null
            webSocket?.close(1000, "Leaving Live Class")
            webSocket = null
            pendingRpcRequests.clear()
            recentlySentClientMsgIds.clear()
            _isConnected.value = false
            _masterUrl.value = null
            _candidateUrls.value = emptyList()
            _viewerCount.value = 0
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket: ${e.message}", e)
        }
    }
}
