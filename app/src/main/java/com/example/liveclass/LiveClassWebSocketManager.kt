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

data class DiscoveredRoomTeacher(
    val peerId: String = "",
    val userId: String = "",
    val name: String = "",
    val role: String = "teacher",
    val avatarUrl: String? = null
) {
    val effectiveId: String
        get() = userId.ifBlank { peerId }
}

class LiveClassWebSocketManager {

    companion object {
        private const val TAG = "LiveClassWS"
        private const val DEFAULT_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
        private const val INIT_ENDPOINT_URL = "https://prod-init.100ms.live/init"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient = com.example.network.SharedHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var teacherWebSocket: WebSocket? = null
    private var hlsPollWatcherJob: Job? = null
    private var reconnectJob: Job? = null
    private var teacherReconnectJob: Job? = null

    @Volatile
    private var shouldAutoReconnect: Boolean = false
    private var reconnectAttempts: Int = 0
    private var lastAuthToken: String = ""
    private var lastTeacherAuthToken: String = ""

    private var currentPeerId: String = ""
    private var teacherListenerPeerId: String = ""
    private var currentUserId: String = ""
    private var currentStudentName: String = ""
    private var currentPeerRole: String = "viewer-near-realtime"
    private var teacherListenerRole: String = ""
    private var handRaisedAtTimestamp: Long = 0L
    @Volatile
    private var hasSubscribedToBroadcastGroups: Boolean = false
    @Volatile
    private var hasSubscribedToTeacherGroup: Boolean = false
    @Volatile
    private var canViewAllMessages: Boolean = false
    @Volatile
    private var joinAsTeacherIdentityMode: Boolean = false
    @Volatile
    private var selectedTeacherPeerId: String = ""
    @Volatile
    private var selectedTeacherUserId: String = ""
    @Volatile
    private var selectedTeacherDisplayName: String = ""
    @Volatile
    private var isPreJoinProbeActive: Boolean = false
    @Volatile
    private var triedExactTeacherPeerOnListener: Boolean = false
    private val subscribedDynamicGroups = ConcurrentHashMap.newKeySet<String>()

    private data class RpcRequestMeta(
        val method: String,
        val pollId: String = "",
        val extra: String = "",
        val fallbackBroadcastPayload: String? = null,
        val secondFallbackBroadcastPayload: String? = null
    )

    private val pendingRpcRequests = ConcurrentHashMap<String, RpcRequestMeta>()
    private val pendingChatCallbacks = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val peerNamesMap = ConcurrentHashMap<String, String>()
    private val peerRolesMap = ConcurrentHashMap<String, String>()
    private val studentClientPeerIds = ConcurrentHashMap.newKeySet<String>()
    private val recentlySentClientMsgIds = ConcurrentHashMap<String, Long>()
    private val processedIncomingMsgIds = ConcurrentHashMap<String, Long>()

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

    private val _activeRoomTeacher = MutableStateFlow<DiscoveredRoomTeacher?>(null)
    val activeRoomTeacher: StateFlow<DiscoveredRoomTeacher?> = _activeRoomTeacher.asStateFlow()

    private val _discoveredTeachers = MutableStateFlow<List<DiscoveredRoomTeacher>>(emptyList())
    val discoveredTeachers: StateFlow<List<DiscoveredRoomTeacher>> = _discoveredTeachers.asStateFlow()

    private val _isJoinedAsTeacherIdentity = MutableStateFlow(false)
    val isJoinedAsTeacherIdentity: StateFlow<Boolean> = _isJoinedAsTeacherIdentity.asStateFlow()

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

    // Auto Poll Answer Engine State (Strictly ONLY for `can_view_profiles` users when Auto is enabled)
    private val _autoPollEnabled = MutableStateFlow(false)
    val autoPollEnabled: StateFlow<Boolean> = _autoPollEnabled.asStateFlow()

    private val _autoPollDelayMs = MutableStateFlow(670L)
    val autoPollDelayMs: StateFlow<Long> = _autoPollDelayMs.asStateFlow()

    private val _autoPollDelaySeconds = MutableStateFlow(1)
    val autoPollDelaySeconds: StateFlow<Int> = _autoPollDelaySeconds.asStateFlow()

    private val _autoPollToastEvent = MutableStateFlow<String?>(null)
    val autoPollToastEvent: StateFlow<String?> = _autoPollToastEvent.asStateFlow()

    private val autoPollJobs = ConcurrentHashMap<String, Job>()
    private val autoSubmittedPollKeys = ConcurrentHashMap.newKeySet<String>()

    fun getCurrentPeerId(): String = currentPeerId
    fun getCurrentUserId(): String = currentUserId
    fun getCurrentStudentName(): String = currentStudentName
    fun getCanViewAllMessages(): Boolean = canViewAllMessages

    fun consumeAutoPollToastEvent() {
        _autoPollToastEvent.value = null
    }

    private fun formatDelayMsLabel(delayMs: Long): String {
        return try {
            java.math.BigDecimal.valueOf(delayMs / 1000.0)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        } catch (_: Exception) {
            String.format(java.util.Locale.US, "%.2f", delayMs / 1000.0)
        }
    }

    /**
     * Configures the Auto Poll Answer Engine with millisecond precision (e.g. 670ms = 0.67s, 1210ms = 1.21s).
     * Strictly requires `canViewAllMessages == true`.
     */
    fun setAutoPollConfigurationMs(enabled: Boolean, delayMs: Long) {
        val safeMs = delayMs.coerceIn(10L, 120_000L)
        _autoPollDelayMs.value = safeMs
        _autoPollDelaySeconds.value = ((safeMs + 500L) / 1000L).toInt().coerceIn(1, 120)
        val effectiveEnabled = enabled && canViewAllMessages
        _autoPollEnabled.value = effectiveEnabled

        if (!effectiveEnabled) {
            autoPollJobs.values.forEach { it.cancel() }
            autoPollJobs.clear()
        } else {
            _activePoll.value?.let { active ->
                if (active.isLive) {
                    scheduleAutoPollSubmissionIfEligible(active.pollId)
                }
            }
        }
    }

    fun setAutoPollConfiguration(enabled: Boolean, delaySeconds: Int) {
        val safeSec = delaySeconds.coerceIn(1, 120)
        setAutoPollConfigurationMs(enabled, safeSec * 1000L)
    }

    fun setInitialTeacherHint(
        teacherId: String?,
        teacherName: String?,
        teacherAvatar: String? = null
    ) {
        val cleanId = teacherId?.trim().orEmpty()
        val cleanName = teacherName?.trim().orEmpty()
        if (cleanId.isBlank() && cleanName.isBlank()) return
        registerDiscoveredTeacher(
            peerId = "",
            userId = cleanId,
            name = cleanName,
            role = "teacher",
            avatarUrl = teacherAvatar
        )
    }

    private fun registerDiscoveredTeacher(
        peerId: String,
        userId: String,
        name: String,
        role: String = "teacher",
        avatarUrl: String? = null
    ) {
        val cleanPeerId = peerId.trim()
        val cleanUserId = userId.trim()
        val cleanName = name.trim()
        if (cleanPeerId.isBlank() && cleanUserId.isBlank() && cleanName.isBlank()) return

        if (cleanName.isNotBlank()) {
            _teacherName.value = cleanName
            if (cleanPeerId.isNotBlank()) {
                updatePollsTeacherName(cleanPeerId, cleanName)
            }
        }

        val currentList = _discoveredTeachers.value.toMutableList()
        val matchIdx = currentList.indexOfFirst { existing ->
            (cleanUserId.isNotBlank() && existing.userId.equals(cleanUserId, ignoreCase = true)) ||
                (cleanPeerId.isNotBlank() && existing.peerId.equals(cleanPeerId, ignoreCase = true)) ||
                (cleanName.isNotBlank() && existing.name.equals(cleanName, ignoreCase = true))
        }

        val merged = if (matchIdx >= 0) {
            val prev = currentList[matchIdx]
            prev.copy(
                peerId = cleanPeerId.ifBlank { prev.peerId },
                userId = cleanUserId.ifBlank { prev.userId },
                name = cleanName.ifBlank { prev.name },
                role = role.ifBlank { prev.role },
                avatarUrl = avatarUrl?.takeIf { it.isNotBlank() } ?: prev.avatarUrl
            )
        } else {
            DiscoveredRoomTeacher(
                peerId = cleanPeerId,
                userId = cleanUserId,
                name = cleanName.ifBlank { com.example.utils.AppLang.tr("Teacher", "শিক্ষক") },
                role = role.ifBlank { "teacher" },
                avatarUrl = avatarUrl?.takeIf { it.isNotBlank() }
            )
        }

        if (matchIdx >= 0) {
            currentList[matchIdx] = merged
        } else {
            currentList.add(0, merged)
        }
        _discoveredTeachers.value = currentList

        val currentActive = _activeRoomTeacher.value
        if (currentActive == null ||
            (merged.peerId.isNotBlank() && currentActive.peerId.isBlank()) ||
            (merged.userId.isNotBlank() && currentActive.userId.isBlank()) ||
            currentActive.name.equals(merged.name, ignoreCase = true)
        ) {
            _activeRoomTeacher.value = merged
        }

        if (joinAsTeacherIdentityMode) {
            if (selectedTeacherUserId.isBlank() && merged.userId.isNotBlank()) {
                selectedTeacherUserId = merged.userId
            }
            if (selectedTeacherPeerId.isBlank() && merged.peerId.isNotBlank()) {
                selectedTeacherPeerId = merged.peerId
            }
            if (selectedTeacherDisplayName.isBlank() && merged.name.isNotBlank()) {
                selectedTeacherDisplayName = merged.name
            }
        }

        if (canViewAllMessages && joinAsTeacherIdentityMode) {
            subscribeToDynamicTeacherGroups(cleanPeerId, cleanUserId)
        }
    }

    private fun subscribeToDynamicTeacherGroups(peerId: String, userId: String) {
        val candidateGroups = listOf(peerId.trim(), userId.trim(), "teacher", "admin")
            .filter { it.isNotBlank() }
            .distinct()
        for (grp in candidateGroups) {
            if (subscribedDynamicGroups.add(grp)) {
                sendGroupJoinRpc(webSocket, grp)
                sendGroupJoinRpc(teacherWebSocket, grp)
            }
        }
    }

    private fun sendGroupJoinRpc(ws: WebSocket?, groupName: String) {
        if (ws == null || groupName.isBlank()) return
        try {
            val reqId = UUID.randomUUID().toString()
            pendingRpcRequests[reqId] = RpcRequestMeta("group-join", extra = groupName)
            val msg = JSONObject().apply {
                put("id", reqId)
                put("method", "group-join")
                put("params", JSONObject().apply {
                    put("name", groupName)
                })
                put("jsonrpc", "2.0")
            }
            ws.send(msg.toString())
        } catch (_: Exception) {
        }
    }

    /**
     * Updates whether this device has Admin-granted `can_view_profiles` permission
     * to view all students' messages, comment in class, edit name, auto-submit polls,
     * and optionally join with teacher identity when explicitly clicked.
     */
    fun setCanViewAllMessages(allowed: Boolean) {
        val previous = canViewAllMessages
        canViewAllMessages = allowed
        if (allowed) {
            val ws = webSocket
            if (ws != null && _isConnected.value && !hasSubscribedToTeacherGroup) {
                hasSubscribedToTeacherGroup = true
                if (joinAsTeacherIdentityMode) {
                    val activeTeacher = _activeRoomTeacher.value
                    subscribeToDynamicTeacherGroups(
                        peerId = selectedTeacherPeerId.ifBlank { activeTeacher?.peerId.orEmpty() },
                        userId = selectedTeacherUserId.ifBlank { activeTeacher?.userId.orEmpty() }
                    )
                } else {
                    subscribeToDynamicTeacherGroups(peerId = "", userId = "")
                }
            }
            // Strictly NEVER open teacher listener socket unless the user explicitly chose joinAsTeacherIdentityMode
            if (joinAsTeacherIdentityMode &&
                teacherWebSocket == null &&
                lastTeacherAuthToken.isNotBlank() &&
                lastTeacherAuthToken != lastAuthToken &&
                shouldAutoReconnect &&
                !isPreJoinProbeActive
            ) {
                openTeacherListenerSocketInternal(lastTeacherAuthToken)
            }
        } else {
            joinAsTeacherIdentityMode = false
            _isJoinedAsTeacherIdentity.value = false
            _autoPollEnabled.value = false
            autoPollJobs.values.forEach { it.cancel() }
            autoPollJobs.clear()
            if (previous || hasSubscribedToTeacherGroup || teacherWebSocket != null) {
                teacherReconnectJob?.cancel()
                try {
                    teacherWebSocket?.close(1000, "Teacher listener restricted")
                } catch (_: Exception) {
                }
                teacherWebSocket = null
                subscribedDynamicGroups.clear()

                val ws = webSocket
                if (ws != null && _isConnected.value && hasSubscribedToTeacherGroup) {
                    hasSubscribedToTeacherGroup = false
                    try {
                        val leaveReqId = UUID.randomUUID().toString()
                        pendingRpcRequests[leaveReqId] = RpcRequestMeta("group-leave", extra = "teacher")
                        val leaveMsg = JSONObject().apply {
                            put("id", leaveReqId)
                            put("method", "group-leave")
                            put("params", JSONObject().apply {
                                put("name", "teacher")
                                put("version", "1.0")
                            })
                            put("jsonrpc", "2.0")
                        }
                        ws.send(leaveMsg.toString())
                    } catch (_: Exception) {
                    }
                }
            }
            // Purge any non-teacher and non-self messages from history immediately
            val filtered = _receivedMessagesHistory.value.filter { it.isTeacher || it.isMine }
            if (filtered.size != _receivedMessagesHistory.value.size) {
                _receivedMessagesHistory.value = filtered
            }
            val latest = _incomingChatMessages.value
            if (latest != null && !latest.isTeacher && !latest.isMine) {
                _incomingChatMessages.value = filtered.lastOrNull()
            }
        }
    }

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

    private fun isPeerMarkedAsStudentClient(peerObj: JSONObject?, infoObj: JSONObject?): Boolean {
        val dataStr = infoObj?.optString("data").orEmpty()
            .ifBlank { peerObj?.optString("data").orEmpty() }
            .trim()
        if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
            try {
                val dataJson = JSONObject(dataStr)
                if (dataJson.optBoolean("isStudentClient", false)) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun extractNameFromPeerData(peerObj: JSONObject?, infoObj: JSONObject?): String? {
        val candidates = listOf(
            infoObj?.optString("data").orEmpty().trim(),
            peerObj?.optString("data").orEmpty().trim()
        )
        for (dataStr in candidates) {
            if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                try {
                    val dataJson = JSONObject(dataStr)
                    val parsedName = dataJson.optString("name").trim()
                        .ifBlank { dataJson.optString("senderName").trim() }
                        .ifBlank { dataJson.optString("username").trim() }
                    if (parsedName.isNotBlank() && !parsedName.equals("null", ignoreCase = true)) {
                        return parsedName
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun subscribeToBroadcastGroupsOnce(ws: WebSocket) {
        if (hasSubscribedToBroadcastGroups) return
        hasSubscribedToBroadcastGroups = true
        try {
            val activeTeacher = _activeRoomTeacher.value
            val tPeerId = selectedTeacherPeerId.ifBlank { activeTeacher?.peerId.orEmpty() }
            val tUserId = selectedTeacherUserId.ifBlank { activeTeacher?.userId.orEmpty() }

            val groupsToJoin = if (canViewAllMessages) {
                hasSubscribedToTeacherGroup = true
                buildList {
                    add("teacher")
                    add("admin")
                    add("viewer-near-realtime")
                    add("student-on-stage")
                    add("hls-viewer")
                    add("student")
                    if (tPeerId.isNotBlank()) add(tPeerId)
                    if (tUserId.isNotBlank()) add(tUserId)
                }.distinct()
            } else {
                hasSubscribedToTeacherGroup = false
                listOf("viewer-near-realtime", "student")
            }
            for (groupName in groupsToJoin) {
                subscribedDynamicGroups.add(groupName)
                sendGroupJoinRpc(ws, groupName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to join broadcast groups: ${e.message}")
        }
    }

    /**
     * Lightweight pre-join probe while user is on the Pre-Join Lobby screen (`LiveClassState.Preview`).
     * Fetches the live `peer-list`, active teacher's exact `peer_id`, `user_id`, `name`, viewer count,
     * and HLS `masterUrl` so the Pre-Join screen displays real-time room & teacher info before joining.
     */
    fun probeRoomBeforeJoin(
        token: String,
        probeDisplayName: String = "Student"
    ) {
        if (token.isBlank()) return
        isPreJoinProbeActive = true
        lastAuthToken = token
        currentPeerId = UUID.randomUUID().toString()
        studentClientPeerIds.add(currentPeerId)
        currentStudentName = probeDisplayName.trim().ifBlank { "Student" }
        openSocketInternal(token)
    }

    /**
     * On-demand Teacher Identity Extractor:
     * Called ONLY when a user with `can_view_profiles` explicitly clicks "শিক্ষকের আইডি দিয়ে জয়েন করুন".
     * Never called during normal student preview or normal student join.
     */
    suspend fun extractRoomTeacherIdentityOnDemand(
        token: String,
        fallbackTeacherId: String? = null,
        fallbackTeacherName: String? = null,
        fallbackTeacherAvatar: String? = null
    ): DiscoveredRoomTeacher {
        if (!fallbackTeacherId.isNullOrBlank() || !fallbackTeacherName.isNullOrBlank()) {
            setInitialTeacherHint(
                teacherId = fallbackTeacherId,
                teacherName = fallbackTeacherName,
                teacherAvatar = fallbackTeacherAvatar
            )
        }

        val existing = _activeRoomTeacher.value
        if (existing != null && existing.userId.isNotBlank() && existing.name.isNotBlank()) {
            return existing
        }

        if (token.isNotBlank() && !_isConnected.value) {
            probeRoomBeforeJoin(token, "Student")
            var waitedMs = 0L
            while (waitedMs < 2200L) {
                val discovered = _activeRoomTeacher.value
                if (discovered != null && (discovered.userId.isNotBlank() || discovered.peerId.isNotBlank()) && discovered.name.isNotBlank()) {
                    break
                }
                delay(150L)
                waitedMs += 150L
            }
        }

        val resolved = _activeRoomTeacher.value
        return resolved ?: DiscoveredRoomTeacher(
            peerId = "",
            userId = fallbackTeacherId?.trim().orEmpty(),
            name = fallbackTeacherName?.trim()?.ifBlank { _teacherName.value.ifBlank { com.example.utils.AppLang.tr("Teacher", "শিক্ষক") } }
                ?: _teacherName.value.ifBlank { com.example.utils.AppLang.tr("Teacher", "শিক্ষক") },
            role = "teacher",
            avatarUrl = fallbackTeacherAvatar
        )
    }

    fun connect(
        token: String,
        studentName: String = "",
        teacherToken: String? = null,
        joinWithTeacherIdentity: Boolean = false,
        targetTeacher: DiscoveredRoomTeacher? = null
    ) {
        if (token.isBlank()) return
        isPreJoinProbeActive = false
        reconnectJob?.cancel()
        teacherReconnectJob?.cancel()
        try {
            webSocket?.close(1000, "Joining Live Class")
        } catch (_: Exception) {
        }
        try {
            teacherWebSocket?.close(1000, "Joining Live Class")
        } catch (_: Exception) {
        }
        webSocket = null
        teacherWebSocket = null

        shouldAutoReconnect = true
        reconnectAttempts = 0
        hasSubscribedToBroadcastGroups = false
        hasSubscribedToTeacherGroup = false
        subscribedDynamicGroups.clear()
        triedExactTeacherPeerOnListener = false

        lastAuthToken = token

        // Strict Opt-In Discipline:
        // Only use Teacher Identity if `canViewAllMessages == true` AND the user explicitly clicked
        // "শিক্ষকের আইডি দিয়ে জয়েন করুন" (`joinWithTeacherIdentity == true`).
        // Otherwise, connect 100% as a normal student with the user's own student ID and name!
        val useTeacherIdentity = canViewAllMessages && joinWithTeacherIdentity
        joinAsTeacherIdentityMode = useTeacherIdentity
        _isJoinedAsTeacherIdentity.value = useTeacherIdentity

        val jwtJson = parseJwtPayload(token)
        val jwtUserId = jwtJson?.optString("user_id").orEmpty().ifBlank {
            jwtJson?.optString("sub").orEmpty()
        }

        if (useTeacherIdentity) {
            val effectiveTeacher = targetTeacher ?: _activeRoomTeacher.value
            selectedTeacherPeerId = effectiveTeacher?.peerId?.trim().orEmpty()
            selectedTeacherUserId = effectiveTeacher?.userId?.trim().orEmpty()
            selectedTeacherDisplayName = effectiveTeacher?.name?.trim().orEmpty()

            val distinctTeacherToken = teacherToken?.trim()?.takeIf { it.isNotBlank() && it != token }.orEmpty()
            lastTeacherAuthToken = distinctTeacherToken

            currentUserId = selectedTeacherUserId.ifBlank { jwtUserId }
            currentStudentName = selectedTeacherDisplayName.ifBlank { studentName.trim().ifBlank { "Teacher" } }
        } else {
            // Normal Student Join: clear any teacher impersonation fields completely
            selectedTeacherPeerId = ""
            selectedTeacherUserId = ""
            selectedTeacherDisplayName = ""
            lastTeacherAuthToken = ""

            currentUserId = jwtUserId
            currentStudentName = studentName.trim()
        }

        currentPeerId = UUID.randomUUID().toString()
        teacherListenerPeerId = UUID.randomUUID().toString()
        studentClientPeerIds.add(currentPeerId)
        studentClientPeerIds.add(teacherListenerPeerId)

        val tokenRole = jwtJson?.optString("role").orEmpty().trim()
        if (tokenRole.isNotBlank() && !tokenRole.equals("null", ignoreCase = true)) {
            currentPeerRole = tokenRole
        }

        if (currentStudentName.isNotBlank()) {
            if (currentPeerId.isNotBlank()) peerNamesMap[currentPeerId] = currentStudentName
            if (jwtUserId.isNotBlank()) peerNamesMap[jwtUserId] = currentStudentName
            if (currentUserId.isNotBlank()) peerNamesMap[currentUserId] = currentStudentName
        }

        openSocketInternal(token)

        // Only open secondary teacher listener socket if explicitly in Teacher Identity Mode AND a separate token exists
        if (useTeacherIdentity && lastTeacherAuthToken.isNotBlank() && lastTeacherAuthToken != token) {
            openTeacherListenerSocketInternal(lastTeacherAuthToken)
        }
    }

    private fun openSocketInternal(token: String) {
        scope.launch {
            hasSubscribedToBroadcastGroups = false
            hasSubscribedToTeacherGroup = false
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
                    Log.d(TAG, "100ms WebSocket Opened as '$currentStudentName' (teacherIdentity=$joinAsTeacherIdentityMode)")
                    reconnectAttempts = 0
                    _isConnected.value = true
                    _error.value = null

                    val effectiveName = currentStudentName.trim().ifBlank { "Student" }
                    val activeTeacher = _activeRoomTeacher.value
                    val tUserId = selectedTeacherUserId.ifBlank { activeTeacher?.userId.orEmpty() }
                    val tPeerId = selectedTeacherPeerId.ifBlank { activeTeacher?.peerId.orEmpty() }

                    val joinId = UUID.randomUUID().toString()
                    pendingRpcRequests[joinId] = RpcRequestMeta("join")
                    val joinMessage = JSONObject().apply {
                        put("id", joinId)
                        put("method", "join")
                        put("params", JSONObject().apply {
                            put("name", effectiveName)
                            if (canViewAllMessages && joinAsTeacherIdentityMode) {
                                // Co-Session Teacher Mode:
                                // Set role="teacher" and include teacher metadata inside `data`,
                                // but do NOT overwrite top-level `user_id` with the live teacher's raw user_id
                                // so 100ms SFU never kicks/disconnects the real teacher from the class!
                                put("role", "teacher")
                            }
                            put("data", JSONObject().apply {
                                put("isBRBOn", false)
                                put("name", effectiveName)
                                if (canViewAllMessages && joinAsTeacherIdentityMode) {
                                    put("role", "teacher")
                                    if (tUserId.isNotBlank()) put("user_id", tUserId)
                                    if (tPeerId.isNotBlank()) put("teacher_peer_id", tPeerId)
                                } else {
                                    put("isStudentClient", true)
                                }
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
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleWebSocketMessage(text, isTeacherListenerSocket = false)
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
                        _error.value = t.message ?: com.example.utils.AppLang.tr("Disconnected from live server", "লাইভ সার্ভারের সাথে সংযোগ বিচ্ছিন্ন হয়েছে")
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
     * Secondary Co-Session Teacher-Identity Listener WebSocket (Active ONLY for `can_view_profiles` users):
     * Connects using a dedicated unique `peer` UUID (NEVER reusing `tPeerId` or top-level `tUserId`)
     * so the real teacher is NEVER kicked out of the live session, while both share the teacher groups and identity!
     */
    private fun openTeacherListenerSocketInternal(token: String) {
        if (token.isBlank() || !canViewAllMessages) return
        scope.launch {
            try {
                val rawUa = buildUserAgentV2()
                val encodedUa = URLEncoder.encode(rawUa, "UTF-8")
                val baseWsEndpoint = fetchWsEndpointFromInit(token, encodedUa)

                val activeTeacher = _activeRoomTeacher.value
                val tPeerId = selectedTeacherPeerId.ifBlank { activeTeacher?.peerId.orEmpty() }
                val tUserId = selectedTeacherUserId.ifBlank { activeTeacher?.userId.orEmpty() }
                val tName = selectedTeacherDisplayName
                    .ifBlank { activeTeacher?.name.orEmpty() }
                    .ifBlank { _teacherName.value }
                    .ifBlank { currentStudentName.ifBlank { "Teacher" } }

                // CRITICAL CO-SESSION RULE:
                // Always generate a unique `teacherListenerPeerId` UUID for `?peer=` and NEVER pass `tPeerId`.
                // Passing `tPeerId` in `?peer=` causes 100ms to evict/kick the real teacher's active WebSocket!
                if (teacherListenerPeerId.isBlank() || teacherListenerPeerId == tPeerId || teacherListenerPeerId == currentPeerId) {
                    teacherListenerPeerId = UUID.randomUUID().toString()
                }
                val peerParam = teacherListenerPeerId
                studentClientPeerIds.add(teacherListenerPeerId)

                val wsUrl =
                    "$baseWsEndpoint?peer=$peerParam&token=$token&user_agent_v2=$encodedUa&protocol_version=2.5&protocol_spec=20240720"
                Log.d(TAG, "Connecting 100ms Co-Session Teacher WebSocket (teacher=$tName, coPeer=$peerParam)")

                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("User-Agent", "okhttp/5.3.2")
                    .build()

                teacherWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        val joinId = UUID.randomUUID().toString()
                        pendingRpcRequests[joinId] = RpcRequestMeta("join", extra = "teacher_listener")
                        val joinMessage = JSONObject().apply {
                            put("id", joinId)
                            put("method", "join")
                            put("params", JSONObject().apply {
                                put("name", tName)
                                put("role", "teacher")
                                put("data", JSONObject().apply {
                                    put("isBRBOn", false)
                                    put("name", tName)
                                    put("role", "teacher")
                                    if (tUserId.isNotBlank()) put("user_id", tUserId)
                                    if (tPeerId.isNotBlank()) put("peer_id", tPeerId)
                                }.toString())
                                put("disableVidAutoSub", true)
                                put("simulcast", true)
                            })
                            put("jsonrpc", "2.0")
                        }
                        ws.send(joinMessage.toString())

                        // Subscribe listener socket to teacher & student broadcast groups and teacher IDs
                        val listenerGroups = buildList {
                            add("teacher")
                            add("admin")
                            add("viewer-near-realtime")
                            add("student-on-stage")
                            add("hls-viewer")
                            add("student")
                            if (tPeerId.isNotBlank()) add(tPeerId)
                            if (tUserId.isNotBlank()) add(tUserId)
                        }.distinct()
                        for (grp in listenerGroups) {
                            sendGroupJoinRpc(ws, grp)
                        }
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        handleWebSocketMessage(text, isTeacherListenerSocket = true)
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        if (shouldAutoReconnect && canViewAllMessages && lastTeacherAuthToken.isNotBlank() && code != 1000) {
                            teacherReconnectJob?.cancel()
                            teacherReconnectJob = scope.launch {
                                delay(1500L)
                                if (shouldAutoReconnect && canViewAllMessages) {
                                    openTeacherListenerSocketInternal(lastTeacherAuthToken)
                                }
                            }
                        }
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        if (shouldAutoReconnect && canViewAllMessages && lastTeacherAuthToken.isNotBlank()) {
                            teacherReconnectJob?.cancel()
                            teacherReconnectJob = scope.launch {
                                delay(2500L)
                                if (shouldAutoReconnect && canViewAllMessages) {
                                    openTeacherListenerSocketInternal(lastTeacherAuthToken)
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Teacher listener socket init error: ${e.message}")
            }
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

    private fun handleWebSocketMessage(text: String, isTeacherListenerSocket: Boolean = false) {
        try {
            val msg = JSONObject(text)
            val method = msg.optString("method")
            val msgId = msg.optString("id")

            when (method) {
                "on-policy-change" -> {
                    val params = msg.optJSONObject("params")
                    val peer = params?.optJSONObject("peer")
                    val peerId = peer?.optString("peer_id").orEmpty()
                    val peerRole = peer?.optString("role").orEmpty().trim()
                        .ifBlank { params?.optString("name").orEmpty().trim() }
                    if (isTeacherListenerSocket) {
                        if (peerId.isNotBlank()) {
                            teacherListenerPeerId = peerId
                            studentClientPeerIds.add(peerId)
                        }
                        if (peerRole.isNotBlank() && !peerRole.equals("null", ignoreCase = true)) {
                            teacherListenerRole = peerRole
                        }
                        return
                    }

                    if (peerId.isNotBlank()) {
                        currentPeerId = peerId
                        studentClientPeerIds.add(peerId)
                    }
                    if (peerRole.isNotBlank() && !peerRole.equals("null", ignoreCase = true)) {
                        currentPeerRole = peerRole
                        if (currentPeerId.isNotBlank()) peerRolesMap[currentPeerId] = "student"
                    }
                    val info = peer?.optJSONObject("info")
                    val uid = info?.optString("user_id").orEmpty()
                    if (uid.isNotBlank()) currentUserId = uid

                    if (currentStudentName.isNotBlank()) {
                        if (currentPeerId.isNotBlank()) peerNamesMap[currentPeerId] = currentStudentName
                        if (currentUserId.isNotBlank()) peerNamesMap[currentUserId] = currentStudentName
                    }

                    webSocket?.let { subscribeToBroadcastGroupsOnce(it) }

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
                    if (isTeacherListenerSocket) return
                    val params = msg.optJSONObject("params")
                    val pId = params?.optString("peer_id").orEmpty()
                    val pRole = params?.optString("role").orEmpty()
                    val info = params?.optJSONObject("info")
                    val pName = info?.optString("name").orEmpty()
                    val uId = info?.optString("user_id").orEmpty()
                    val isStudentClient = isPeerMarkedAsStudentClient(params, info) ||
                        pId == currentPeerId ||
                        pId == teacherListenerPeerId ||
                        (uId.isNotBlank() && uId == currentUserId)

                    if (isStudentClient && pId.isNotBlank()) {
                        studentClientPeerIds.add(pId)
                    }

                    if (pName.isNotBlank()) {
                        if (pId.isNotBlank()) peerNamesMap[pId] = pName
                        if (uId.isNotBlank()) peerNamesMap[uId] = pName
                    }
                    if (pRole.isNotBlank() && pId.isNotBlank()) {
                        peerRolesMap[pId] = if (isStudentClient) "student" else pRole
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
                    if (isTeacherListenerSocket) return
                    val params = msg.optJSONObject("params")
                    val peerObj = params?.optJSONObject("peer") ?: params
                    val pId = peerObj?.optString("peer_id").orEmpty()
                    val pRole = peerObj?.optString("role").orEmpty()
                    val info = peerObj?.optJSONObject("info")
                    val pName = info?.optString("name").orEmpty()
                    val uId = info?.optString("user_id").orEmpty()
                    val isStudentClient = isPeerMarkedAsStudentClient(peerObj, info) ||
                        pId == currentPeerId ||
                        pId == teacherListenerPeerId

                    if (isStudentClient && pId.isNotBlank()) {
                        studentClientPeerIds.add(pId)
                    }

                    if (pName.isNotBlank()) {
                        if (pId.isNotBlank()) peerNamesMap[pId] = pName
                        if (uId.isNotBlank()) peerNamesMap[uId] = pName
                    }
                    if (pRole.isNotBlank() && pId.isNotBlank()) {
                        peerRolesMap[pId] = if (isStudentClient) "student" else pRole
                    }
                    if (!isStudentClient &&
                        (pRole.equals("teacher", ignoreCase = true) ||
                            pRole.contains("host", ignoreCase = true) ||
                            pRole.contains("instructor", ignoreCase = true)) &&
                        pName.isNotBlank()
                    ) {
                        registerDiscoveredTeacher(
                            peerId = pId,
                            userId = uId,
                            name = pName,
                            role = pRole.ifBlank { "teacher" }
                        )
                    }
                    if (_viewerCount.value > 0 && pId != teacherListenerPeerId) {
                        _viewerCount.value += 1
                    }
                }

                "on-peer-leave" -> {
                    if (isTeacherListenerSocket) return
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
                            val pObj = peersObj.optJSONObject(key) ?: continue
                            val pId = pObj.optString("peer_id", key)
                            if (pId == teacherListenerPeerId) continue
                            count++
                            val pRole = pObj.optString("role")
                            val info = pObj.optJSONObject("info")
                            val pName = info?.optString("name").orEmpty()
                            val uId = info?.optString("user_id").orEmpty()
                            val isStudentClient = isPeerMarkedAsStudentClient(pObj, info) ||
                                pId == currentPeerId ||
                                pId == teacherListenerPeerId

                            if (isStudentClient && pId.isNotBlank()) {
                                studentClientPeerIds.add(pId)
                            }

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
                                peerRolesMap[pId] = if (isStudentClient) "student" else pRole
                            }
                            if (!isStudentClient &&
                                (pRole.equals("teacher", ignoreCase = true) ||
                                    pRole.contains("host", ignoreCase = true) ||
                                    pRole.contains("instructor", ignoreCase = true)) &&
                                (pName.isNotBlank() || uId.isNotBlank())
                            ) {
                                registerDiscoveredTeacher(
                                    peerId = pId,
                                    userId = uId,
                                    name = pName,
                                    role = pRole.ifBlank { "teacher" }
                                )
                            }
                        }
                        if (!isTeacherListenerSocket) {
                            _raisedHandsCount.value = handRaiseCount
                            if (count > 0 && _viewerCount.value == 0) {
                                _viewerCount.value = count
                            }
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

                            // 1. Check chatState (enabled / disabled by Teacher + Teacher exact peerId & userId!)
                            if (key == "chatState") {
                                val dataObj = vObj.optJSONObject("data")
                                if (dataObj != null) {
                                    val enabled = dataObj.optBoolean("enabled", true)
                                    _isChatEnabled.value = enabled
                                    val updatedByObj = dataObj.optJSONObject("updatedBy")
                                    val teacherUser = updatedByObj?.optString("userName").orEmpty()
                                    val teacherPeerId = updatedByObj?.optString("peerId").orEmpty()
                                    val teacherUserId = updatedByObj?.optString("userId").orEmpty()
                                    if (teacherUser.isNotBlank() || teacherPeerId.isNotBlank() || teacherUserId.isNotBlank()) {
                                        if (teacherUser.isNotBlank()) {
                                            _chatStateUpdatedBy.value = teacherUser
                                        }
                                        registerDiscoveredTeacher(
                                            peerId = teacherPeerId,
                                            userId = teacherUserId,
                                            name = teacherUser,
                                            role = "teacher"
                                        )
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
                                            _teacherName.value.ifBlank { com.example.utils.AppLang.tr("Teacher", "শিক্ষক") }
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
                                                timestamp = com.example.utils.AppLang.tr("Pinned Announcement", "পিন করা ঘোষণা"),
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
                    val fallbackReqId = try {
                        JSONObject(fallbackPayload).optString("id")
                    } catch (_: Exception) {
                        ""
                    }
                    if (fallbackReqId.isNotBlank()) {
                        pendingRpcRequests[fallbackReqId] = RpcRequestMeta(
                            method = "broadcast",
                            extra = reqMeta.extra,
                            fallbackBroadcastPayload = reqMeta.secondFallbackBroadcastPayload
                        )
                        val existingCb = pendingChatCallbacks.remove(msgId)
                        if (existingCb != null) {
                            pendingChatCallbacks[fallbackReqId] = existingCb
                        }
                    }
                    Log.d(TAG, "Retrying broadcast with fallback payload...")
                    val fallbackSent = ws.send(fallbackPayload)
                    if (!fallbackSent && fallbackReqId.isNotBlank()) {
                        pendingRpcRequests.remove(fallbackReqId)
                        pendingChatCallbacks.remove(fallbackReqId)?.invoke(false)
                    }
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
        if (reqMeta?.method == "join") {
            webSocket?.let { subscribeToBroadcastGroupsOnce(it) }
            requestPollList("started")
            requestPollList("stopped")
        }

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
        if (room != null) {
            val peerCount = room.optInt("peer_count", 0)
            if (peerCount > 0) {
                _viewerCount.value = peerCount
            }
            val desc = room.optString("description").orEmpty()
            if (desc.isNotBlank() && _roomTitle.value.isBlank()) {
                val cleanTitle = desc.substringBeforeLast(" - ").trim()
                if (cleanTitle.isNotBlank()) {
                    _roomTitle.value = cleanTitle
                }
            }
        }
        val streaming = room?.optJSONObject("streaming") ?: result.optJSONObject("streaming")
        val variants = streaming?.optJSONObject("hls")?.optJSONArray("variants")
        if (variants != null && variants.length() > 0) {
            val url = variants.optJSONObject(0)?.optString("url")
            if (!url.isNullOrBlank()) {
                _masterUrl.value = url
                startHlsMetadataWatcher(url)
            }
        }

        val pollsArr = result.optJSONArray("polls") ?: room?.optJSONArray("polls")
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
            val optionExplicitCorrectIndices = mutableListOf<Int>()
            if (optionsArr != null) {
                for (oIdx in 0 until optionsArr.length()) {
                    val optObj = optionsArr.optJSONObject(oIdx) ?: continue
                    val parsedIdx = optObj.optInt("index", oIdx + 1)
                    val parsedWeight = optObj.optInt("weight", 0)
                    val isMarkedCorrect = optObj.optBoolean("is_correct", false) ||
                        optObj.optBoolean("correct", false) ||
                        optObj.optBoolean("isCorrect", false) ||
                        parsedWeight > 0
                    if (isMarkedCorrect && parsedIdx > 0) {
                        optionExplicitCorrectIndices.add(parsedIdx)
                    }
                    parsedOptions.add(
                        LivePollOption(
                            index = parsedIdx,
                            text = optObj.optString("text", ""),
                            weight = parsedWeight
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
            for (cIdx in optionExplicitCorrectIndices) {
                if (!correctOptionsList.contains(cIdx)) {
                    correctOptionsList.add(cIdx)
                }
            }

            val directCorrectField = qObj.optInt("correct_option", 0)
                .takeIf { it > 0 }
                ?: qEntry.optInt("correct_option", 0).takeIf { it > 0 }
                ?: qObj.optInt("answer_option", 0).takeIf { it > 0 }

            val correctOpt = when {
                ansObj != null && ansObj.has("option") && !ansObj.isNull("option") -> {
                    val optVal = ansObj.optInt("option", 0)
                    if (optVal > 0) optVal else (directCorrectField ?: correctOptionsList.firstOrNull())
                }
                directCorrectField != null -> directCorrectField
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

            // Automatically schedule poll answer submission if Auto Poll is enabled for this permitted user
            scheduleAutoPollSubmissionIfEligible(pollId)
        }
    }

    /**
     * Background Auto Poll Answer Engine:
     * When `canViewAllMessages && _autoPollEnabled.value` is true, automatically waits
     * `_autoPollDelaySeconds.value` seconds and submits the correct option for each question
     * in the active live poll without requiring the user to open the poll UI.
     */
    private fun scheduleAutoPollSubmissionIfEligible(pollId: String) {
        if (!canViewAllMessages || !_autoPollEnabled.value) return
        val poll = _activePoll.value?.takeIf { it.pollId == pollId }
            ?: _previousPolls.value.firstOrNull { it.pollId == pollId }
            ?: return

        if (!poll.isLive) return
        if (poll.userResponse != null && poll.userResponse.selectedOptionIndex > 0) return
        if (poll.questions.isEmpty()) return

        for (question in poll.questions) {
            val key = "${pollId}_${question.index}"
            if (autoSubmittedPollKeys.contains(key) || autoPollJobs[key]?.isActive == true) {
                continue
            }

            val resolvedCorrectOption = question.correctOptionIndex
                ?: question.correctOptionIndices.firstOrNull { it > 0 }
                ?: question.options.firstOrNull { it.weight > 0 }?.index
                ?: question.options.firstOrNull()?.index
                ?: continue

            if (resolvedCorrectOption <= 0) continue

            val job = scope.launch {
                try {
                    val delayMs = _autoPollDelayMs.value.coerceIn(10L, 120_000L)
                    delay(delayMs)

                    if (!canViewAllMessages || !_autoPollEnabled.value) return@launch
                    val currentPoll = _activePoll.value?.takeIf { it.pollId == pollId }
                        ?: _previousPolls.value.firstOrNull { it.pollId == pollId }
                        ?: return@launch
                    if (!currentPoll.isLive) return@launch
                    if (currentPoll.userResponse != null && currentPoll.userResponse.selectedOptionIndex > 0) {
                        return@launch
                    }

                    val latestQuestion = currentPoll.questions.firstOrNull { it.index == question.index } ?: question
                    val finalCorrectOption = latestQuestion.correctOptionIndex
                        ?: latestQuestion.correctOptionIndices.firstOrNull { it > 0 }
                        ?: latestQuestion.options.firstOrNull { it.weight > 0 }?.index
                        ?: resolvedCorrectOption

                    if (!autoSubmittedPollKeys.add(key)) return@launch

                    submitPollAnswer(
                        pollId = pollId,
                        questionIndex = latestQuestion.index,
                        optionIndex = finalCorrectOption,
                        durationMs = delayMs
                    )

                    val optText = latestQuestion.options
                        .firstOrNull { it.index == finalCorrectOption }
                        ?.text
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: com.example.utils.AppLang.tr("Option #$finalCorrectOption", "অপশন #$finalCorrectOption")

                    val delayLabel = formatDelayMsLabel(delayMs)
                    _autoPollToastEvent.value =
                        com.example.utils.AppLang.tr(
                            "⚡ Auto Poll: Correct answer ($optText) auto-submitted in ${delayLabel}s!",
                            "⚡ অটো পোল: ${delayLabel}s এ সঠিক উত্তর ($optText) অটোমেটিক সাবমিট হয়েছে!"
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Auto poll submission error: ${e.message}")
                } finally {
                    autoPollJobs.remove(key)
                }
            }
            autoPollJobs[key] = job
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
            .ifBlank { peerObj?.optString("id").orEmpty() }
            .ifBlank { peerObj?.optString("peerid").orEmpty() }
            .ifBlank { if (peerObj == null) params.optString("peer") else "" }
            .trim()

        // Ignore our own echoed broadcast messages that we ourselves sent from this client
        val actualTeacherPeerId = _activeRoomTeacher.value?.peerId.orEmpty()
        if (peerIdFromParams.isNotBlank() &&
            peerIdFromParams != actualTeacherPeerId &&
            (peerIdFromParams == currentPeerId || peerIdFromParams == teacherListenerPeerId)
        ) {
            return
        }

        var chatText: String? = null
        var sender: String? = null
        var role: String? = null
        var senderRoleFromInfo: String? = null
        var targetRole: String? = null
        var clientMsgId: String? = null
        var senderPeerIdFromInfo: String? = null
        var isStudentClientFromInfo = false
        var timestampMillis = System.currentTimeMillis()

        val infoRaw = params.opt("info") ?: params.opt("data")
        when (infoRaw) {
            is JSONObject -> {
                clientMsgId = infoRaw.optString("clientMsgId").takeIf { it.isNotBlank() }
                senderPeerIdFromInfo = infoRaw.optString("senderPeerId").takeIf { it.isNotBlank() }
                isStudentClientFromInfo = infoRaw.optBoolean("isStudentClient", false)
                senderRoleFromInfo = infoRaw.optString("senderRole").takeIf { it.isNotBlank() }
                val nestedDataObj = infoRaw.optJSONObject("data")
                chatText = infoRaw.optString("message").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("msg").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("text").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("body").takeIf { it.isNotBlank() }
                    ?: nestedDataObj?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: nestedDataObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: nestedDataObj?.optString("text")?.takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("data").takeIf { it.isNotBlank() && !it.trim().startsWith("{") }
                sender = infoRaw.optString("sender").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("senderName").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("sender_name").takeIf { it.isNotBlank() }
                    ?: infoRaw.optString("name").takeIf { it.isNotBlank() }
                    ?: nestedDataObj?.optString("sender")?.takeIf { it.isNotBlank() }
                    ?: nestedDataObj?.optString("name")?.takeIf { it.isNotBlank() }
                role = infoRaw.optString("role").takeIf { it.isNotBlank() }
                targetRole = infoRaw.optString("targetRole").takeIf { it.isNotBlank() }
                if (infoRaw.has("time")) timestampMillis = infoRaw.optLong("time", timestampMillis)
            }
            is String -> {
                if (infoRaw.trim().startsWith("{") && infoRaw.trim().endsWith("}")) {
                    try {
                        val infoJson = JSONObject(infoRaw)
                        val nestedDataObj = infoJson.optJSONObject("data")
                        clientMsgId = infoJson.optString("clientMsgId").takeIf { it.isNotBlank() }
                        senderPeerIdFromInfo = infoJson.optString("senderPeerId").takeIf { it.isNotBlank() }
                        isStudentClientFromInfo = infoJson.optBoolean("isStudentClient", false)
                        senderRoleFromInfo = infoJson.optString("senderRole").takeIf { it.isNotBlank() }
                        chatText = infoJson.optString("message").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("msg").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("text").takeIf { it.isNotBlank() }
                            ?: infoJson.optString("body").takeIf { it.isNotBlank() }
                            ?: nestedDataObj?.optString("msg")?.takeIf { it.isNotBlank() }
                            ?: nestedDataObj?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: nestedDataObj?.optString("text")?.takeIf { it.isNotBlank() }
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

        if (!senderPeerIdFromInfo.isNullOrBlank() &&
            senderPeerIdFromInfo != actualTeacherPeerId &&
            (senderPeerIdFromInfo == currentPeerId || senderPeerIdFromInfo == teacherListenerPeerId)
        ) {
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

        if (chatText.isNullOrBlank()) return

        val resolvedPeerId = peerIdFromParams.ifBlank { senderPeerIdFromInfo.orEmpty() }
        if (isStudentClientFromInfo && resolvedPeerId.isNotBlank() && resolvedPeerId != actualTeacherPeerId) {
            studentClientPeerIds.add(resolvedPeerId)
        }

        val peerInfoObj = peerObj?.optJSONObject("info")
        if (isPeerMarkedAsStudentClient(peerObj, peerInfoObj) &&
            resolvedPeerId.isNotBlank() &&
            resolvedPeerId != actualTeacherPeerId
        ) {
            studentClientPeerIds.add(resolvedPeerId)
        }

        if (sender.isNullOrBlank()) {
            sender = extractNameFromPeerData(peerObj, peerInfoObj)
                ?: peerObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: peerObj?.optString("username")?.takeIf { it.isNotBlank() }
                ?: peerInfoObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: peerInfoObj?.optString("username")?.takeIf { it.isNotBlank() }
                ?: peerNamesMap[resolvedPeerId]
                ?: params.optString("sender").takeIf { it.isNotBlank() }
                ?: params.optString("from").takeIf { it.isNotBlank() }
                ?: com.example.utils.AppLang.tr("Student", "শিক্ষার্থী")
        } else if (resolvedPeerId.isNotBlank()) {
            peerNamesMap[resolvedPeerId] = sender
        }

        val rawPeerRole = peerObj?.optString("role")?.takeIf { it.isNotBlank() }
            ?: peerRolesMap[resolvedPeerId]

        val isKnownStudentPeer = isStudentClientFromInfo ||
            senderRoleFromInfo.equals("student", ignoreCase = true) ||
            rawPeerRole?.contains("student", ignoreCase = true) == true ||
            rawPeerRole?.contains("viewer", ignoreCase = true) == true ||
            (resolvedPeerId.isNotBlank() && resolvedPeerId != actualTeacherPeerId && studentClientPeerIds.contains(resolvedPeerId))

        val peerActualRole = if (isKnownStudentPeer) "student" else rawPeerRole

        val rolesArr = params.optJSONArray("roles")
        val rolesFromParams = if (rolesArr != null && rolesArr.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until rolesArr.length()) {
                val r = rolesArr.optString(i).trim()
                if (r.isNotBlank()) list.add(r)
            }
            when {
                list.size > 1 && list.any { it.contains("teacher", true) } && list.any { it.contains("viewer", true) || it.contains("student", true) } -> "all"
                list.all { it.contains("teacher", ignoreCase = true) } -> "teacher"
                list.any { it.contains("viewer", ignoreCase = true) || it.contains("student", ignoreCase = true) } -> "student"
                else -> list.firstOrNull()
            }
        } else {
            null
        }

        // Only treat sender as the real teacher if they are NOT a student peer AND their actual room role is teacher/host/moderator
        val isSenderActualTeacher = (resolvedPeerId.isNotBlank() && resolvedPeerId == actualTeacherPeerId) ||
            (!isKnownStudentPeer && (
                peerActualRole?.contains("teacher", ignoreCase = true) == true ||
                    peerActualRole?.contains("host", ignoreCase = true) == true ||
                    peerActualRole?.contains("moderator", ignoreCase = true) == true ||
                    peerActualRole?.contains("instructor", ignoreCase = true) == true ||
                    (peerActualRole.isNullOrBlank() && role?.equals("teacher", ignoreCase = true) == true) ||
                    (peerActualRole.isNullOrBlank() && _teacherName.value.isNotBlank() && sender?.equals(_teacherName.value, ignoreCase = true) == true)
                ))

        // Strict Privacy & Anti-Abuse Gate:
        // If this device does NOT have `can_view_profiles` permission, it must NEVER receive or display
        // any other student's chat messages—only messages from the actual Teacher are allowed!
        if (!canViewAllMessages && !isSenderActualTeacher) {
            return
        }

        val resolvedTargetRole = when {
            !targetRole.isNullOrBlank() -> when {
                targetRole.contains("teacher", ignoreCase = true) -> "teacher"
                targetRole.equals("*") || targetRole.contains("all", ignoreCase = true) -> "all"
                else -> "student"
            }
            rolesFromParams == "teacher" && !isSenderActualTeacher -> "teacher"
            role?.contains("teacher", ignoreCase = true) == true && !isSenderActualTeacher -> "teacher"
            !rolesFromParams.isNullOrBlank() -> rolesFromParams
            role?.equals("*") == true || role?.contains("all", ignoreCase = true) == true -> "all"
            isSenderActualTeacher -> "all"
            else -> "teacher"
        }

        val normalizedSenderRole = when {
            isSenderActualTeacher -> "teacher"
            else -> "student"
        }

        val dedupId = clientMsgId ?: run {
            val timeBucket = timestampMillis / 2500L
            val senderKey = resolvedPeerId.ifBlank { sender.orEmpty().trim().lowercase() }
            "msg_${senderKey}_${chatText.trim().hashCode()}_$timeBucket"
        }

        if (processedIncomingMsgIds.putIfAbsent(dedupId, timestampMillis) != null) {
            return
        }

        val formattedTime = try {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestampMillis))
        } catch (_: Exception) {
            com.example.utils.AppLang.tr("Now", "এখন")
        }

        val incomingMsg = LiveChatMessage(
            id = dedupId,
            senderName = sender ?: com.example.utils.AppLang.tr("Student", "শিক্ষার্থী"),
            message = chatText,
            timestamp = formattedTime,
            isTeacher = isSenderActualTeacher,
            role = normalizedSenderRole,
            targetRole = resolvedTargetRole,
            senderPeerId = resolvedPeerId,
            isMine = false
        )
        appendIncomingMessage(incomingMsg)
    }

    /**
     * Sends broadcast message using the 100ms JSON-RPC protocol (`protocol_version=2.5`).
     * - STRICT PERMISSION GATE:
     *   Only devices with `canViewAllMessages == true` (`can_view_profiles` enabled) are allowed
     *   to comment/send messages in Live Classes. Regular students (`canViewAllMessages == false`)
     *   are completely blocked from sending messages.
     * - STRICT ROLE TARGETING (NO LEAKS):
     *   1. `targetRole == "teacher"`: Sends ONLY to `roles = ["teacher"]` (and NEVER includes
     *      `"viewer-near-realtime"` or `"student"`, and NEVER falls back to room-wide broadcast!).
     *   2. `targetRole == "student"`: Sends to `roles = ["viewer-near-realtime"]` (the exact
     *      student role in Shikho's 100ms `known_roles`).
     *   3. `targetRole == "all"`: Sends to both teacher and students (`["teacher", "viewer-near-realtime"]`
     *      with room-wide fallback).
     */
    fun sendChatMessage(
        text: String,
        studentName: String,
        role: String = "student",
        targetRole: String = "teacher",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!canViewAllMessages) {
            Log.w(TAG, "Cannot send chat: Profile View Permission (can_view_profiles) is required to comment")
            onComplete?.invoke(false)
            return
        }

        val ws = webSocket ?: teacherWebSocket
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
            processedIncomingMsgIds[clientMsgId] = nowMs

            val effectiveSenderName = studentName.trim().ifBlank { currentStudentName.ifBlank { "Student" } }
            currentStudentName = effectiveSenderName

            val activeStudentRole = currentPeerRole.trim()
                .takeIf { it.isNotBlank() && !it.equals("teacher", ignoreCase = true) && !it.equals("student", ignoreCase = true) }
                ?: "viewer-near-realtime"

            val effectiveTargetRole = targetRole.lowercase().trim().ifBlank { "teacher" }

            // Official Shikho 100ms chat info payload + metadata
            val infoJson = JSONObject().apply {
                put("message", text)
                put("type", "chat")
                put("msg", text)
                put("text", text)
                put("clientMsgId", clientMsgId)
                put("sender", effectiveSenderName)
                put("name", effectiveSenderName)
                put("senderName", effectiveSenderName)
                put("sender_name", effectiveSenderName)
                put("role", if (joinAsTeacherIdentityMode) "teacher" else "student")
                put("senderRole", "student")
                put("targetRole", effectiveTargetRole)
                put("isStudentClient", true)
                put("senderPeerId", currentPeerId)
                put("senderUserId", currentUserId)
                put("time", nowMs)
            }

            // Minimal official Shikho payload for strict fallback
            val minimalInfoJson = JSONObject().apply {
                put("message", text)
                put("type", "chat")
            }

            val broadcastParams = JSONObject().apply {
                put("info", infoJson)
            }

            when (effectiveTargetRole) {
                "teacher" -> {
                    // STRICT TEACHER-ONLY: ONLY ["teacher"] role — NEVER send to students or room-wide!
                    val teacherRoles = JSONArray().apply { put("teacher") }
                    broadcastParams.put("roles", teacherRoles)

                    val strictTeacherFallback = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "broadcast")
                        put("params", JSONObject().apply {
                            put("info", minimalInfoJson)
                            put("roles", JSONArray().apply { put("teacher") })
                        })
                        put("jsonrpc", "2.0")
                    }.toString()

                    pendingRpcRequests[reqId] = RpcRequestMeta(
                        method = "broadcast",
                        extra = "teacher",
                        fallbackBroadcastPayload = strictTeacherFallback,
                        secondFallbackBroadcastPayload = null
                    )
                }

                "student", "students" -> {
                    // STUDENT-ONLY: Use exact 100ms student role ("viewer-near-realtime") from known_roles
                    val studentRoles = JSONArray().apply {
                        put(activeStudentRole)
                    }
                    broadcastParams.put("roles", studentRoles)

                    val multiStudentFallback = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "broadcast")
                        put("params", JSONObject().apply {
                            put("info", infoJson)
                            put("roles", JSONArray().apply {
                                put("viewer-near-realtime")
                                put("student-on-stage")
                                put("hls-viewer")
                            })
                        })
                        put("jsonrpc", "2.0")
                    }.toString()

                    val roomWideFallback = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "broadcast")
                        put("params", JSONObject().apply {
                            put("info", infoJson)
                        })
                        put("jsonrpc", "2.0")
                    }.toString()

                    pendingRpcRequests[reqId] = RpcRequestMeta(
                        method = "broadcast",
                        extra = "student",
                        fallbackBroadcastPayload = multiStudentFallback,
                        secondFallbackBroadcastPayload = roomWideFallback
                    )
                }

                else -> {
                    // ALL (* All): Send to both teacher and all students
                    val allRoles = JSONArray().apply {
                        put("teacher")
                        put(activeStudentRole)
                    }
                    broadcastParams.put("roles", allRoles)

                    val roomWideFallback = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "broadcast")
                        put("params", JSONObject().apply {
                            put("info", infoJson)
                        })
                        put("jsonrpc", "2.0")
                    }.toString()

                    pendingRpcRequests[reqId] = RpcRequestMeta(
                        method = "broadcast",
                        extra = "all",
                        fallbackBroadcastPayload = roomWideFallback,
                        secondFallbackBroadcastPayload = null
                    )
                }
            }

            if (onComplete != null) {
                pendingChatCallbacks[reqId] = onComplete
            }

            val broadcastMsg = JSONObject().apply {
                put("id", reqId)
                put("method", "broadcast")
                put("params", broadcastParams)
                put("jsonrpc", "2.0")
            }

            Log.d(
                TAG,
                "Sending 100ms broadcast chat as '$effectiveSenderName' to '$effectiveTargetRole' (roles=${broadcastParams.optJSONArray("roles")})"
            )
            val sent = ws.send(broadcastMsg.toString())
            if (!sent) {
                pendingRpcRequests.remove(reqId)
                pendingChatCallbacks.remove(reqId)?.invoke(false)
            } else {
                scope.launch {
                    delay(900L)
                    pendingChatCallbacks.remove(reqId)?.invoke(_isConnected.value)
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
     * Updates the user's display name across the active live class session (ONLY for `canViewAllMessages == true`):
     * 1. Updates local peer & user name mappings
     * 2. Re-joins the 100ms WebSocket with a fresh peer session & `join` RPC carrying `newName`
     *    so that 100ms server updates `peer.info.name` in reality for all peers in the room!
     */
    fun updateStudentName(newName: String, freshAuthToken: String? = null) {
        if (!canViewAllMessages) {
            Log.w(TAG, "Name change blocked: user does not have can_view_profiles permission")
            return
        }
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

        val tokenToUse = freshAuthToken?.trim()?.takeIf { it.isNotBlank() } ?: lastAuthToken
        if (tokenToUse.isNotBlank() && shouldAutoReconnect && !isPreJoinProbeActive) {
            lastAuthToken = tokenToUse
            try {
                webSocket?.close(1000, "Updating peer display name")
            } catch (_: Exception) {
            }
            webSocket = null
            currentPeerId = UUID.randomUUID().toString()
            studentClientPeerIds.add(currentPeerId)
            peerNamesMap[currentPeerId] = cleanName
            openSocketInternal(tokenToUse)
            Log.d(TAG, "Reconnected 100ms WebSocket with new display name: $cleanName")
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
            teacherReconnectJob?.cancel()
            teacherReconnectJob = null
            hlsPollWatcherJob?.cancel()
            hlsPollWatcherJob = null
            autoPollJobs.values.forEach { it.cancel() }
            autoPollJobs.clear()
            autoSubmittedPollKeys.clear()
            webSocket?.close(1000, "Leaving Live Class")
            webSocket = null
            teacherWebSocket?.close(1000, "Leaving Live Class")
            teacherWebSocket = null
            pendingRpcRequests.clear()
            recentlySentClientMsgIds.clear()
            processedIncomingMsgIds.clear()
            studentClientPeerIds.clear()
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
