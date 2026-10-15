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
import com.example.liveclass.poll.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class LiveClassWebSocketManager {

    companion object {
        private const val TAG = "LiveClassWS"
        private const val BASE_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
    }

    // 15s ping interval ensures carrier NATs and 100ms servers do not drop the connection during live playback
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

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

    // 100ms Live Poll / Quiz State Flows
    private val _activePoll = MutableStateFlow<LivePollItem?>(null)
    val activePoll: StateFlow<LivePollItem?> = _activePoll.asStateFlow()

    private val _previousPolls = MutableStateFlow<List<LivePollItem>>(emptyList())
    val previousPolls: StateFlow<List<LivePollItem>> = _previousPolls.asStateFlow()

    private val _pollLeaderboard = MutableStateFlow<Map<String, List<LivePollLeaderboardEntry>>>(emptyMap())
    val pollLeaderboard: StateFlow<Map<String, List<LivePollLeaderboardEntry>>> = _pollLeaderboard.asStateFlow()


    fun setMasterUrl(url: String, viewers: Int = 1) {
        _masterUrl.value = url
        _viewerCount.value = viewers.coerceAtLeast(1)
        _isConnected.value = true
        _error.value = null
    }

    fun addCandidateUrls(urls: List<String>) {
        val current = _candidateUrls.value.toMutableList()
        current.addAll(urls.filter { it.isNotBlank() && it != "null" })
        _candidateUrls.value = current.distinct()
    }

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

                // 100ms Live Poll/Quiz Discovery: Query started and stopped polls
                try {
                    val getStartedPolls = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "poll-list")
                        put("params", JSONObject().apply {
                            put("count", 10)
                            put("state", "started")
                            put("version", "1.0")
                        })
                        put("jsonrpc", "2.0")
                    }
                    ws.send(getStartedPolls.toString())

                    val getStoppedPolls = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "poll-list")
                        put("params", JSONObject().apply {
                            put("count", 10)
                            put("state", "stopped")
                            put("version", "1.0")
                        })
                        put("jsonrpc", "2.0")
                    }
                    ws.send(getStoppedPolls.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send poll-list queries: ${e.message}")
                }
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

                    // Check result object (JSON-RPC reply to 'join' or poll methods)
                    val result = msg.optJSONObject("result")
                    if (result != null) {
                        val room = result.optJSONObject("room")
                        val streaming = room?.optJSONObject("streaming") ?: result.optJSONObject("streaming")
                        val hls = streaming?.optJSONObject("hls")
                        val variants = hls?.optJSONArray("variants")
                        if (variants != null && variants.length() > 0) {
                            val url = variants.optJSONObject(0)?.optString("url")
                            if (!url.isNullOrBlank()) {
                                Log.d(TAG, "Found Master Link in result: $url")
                                _masterUrl.value = url
                            }
                        }

                        // 1. Check if result contains Poll List
                        val pollsArr = result.optJSONArray("polls")
                            ?: result.optJSONArray("poll_list")
                            ?: result.optJSONArray("items")
                        if (pollsArr != null) {
                            val parsedPolls = mutableListOf<LivePollItem>()
                            for (pIdx in 0 until pollsArr.length()) {
                                val pObj = pollsArr.optJSONObject(pIdx) ?: continue
                                val pId = pObj.optString("poll_id").ifBlank { pObj.optString("id") }
                                if (pId.isBlank()) continue
                                val pTitle = pObj.optString("title", "Quiz")
                                val pState = pObj.optString("state", "stopped")
                                val pType = pObj.optString("type", "quiz")
                                val pollItem = LivePollItem(
                                    pollId = pId,
                                    title = pTitle,
                                    state = pState,
                                    type = pType,
                                    startedAt = pObj.optLong("started_at", 0),
                                    stoppedAt = pObj.optLong("stopped_at", 0)
                                )
                                parsedPolls.add(pollItem)
                                if (pState == "started") {
                                    _activePoll.value = pollItem
                                    fetchPollQuestions(pId)
                                }
                            }
                            if (parsedPolls.isNotEmpty()) {
                                val current = _previousPolls.value.toMutableList()
                                for (np in parsedPolls) {
                                    if (current.none { it.pollId == np.pollId }) {
                                        current.add(np)
                                    }
                                }
                                _previousPolls.value = current
                            }
                        }

                        // 2. Check if result contains Leaderboard or Questions
                        val pollIdInResult = result.optString("poll_id").ifBlank { result.optString("id") }
                        
                        // 2a. Check for Leaderboard entries
                        val lbEntriesArr = result.optJSONArray("entries")
                            ?: result.optJSONArray("leaderboard")
                            ?: result.optJSONArray("rankings")
                        
                        val questionsArr = result.optJSONArray("questions")
                        val isLeaderboardInQuestions = questionsArr != null && questionsArr.length() > 0 &&
                                questionsArr.optJSONObject(0)?.let { it.has("peer") || it.has("score") || it.has("position") } == true

                        if (lbEntriesArr != null || isLeaderboardInQuestions) {
                            val targetArr = lbEntriesArr ?: questionsArr!!
                            val entries = mutableListOf<LivePollLeaderboardEntry>()
                            for (k in 0 until targetArr.length()) {
                                val row = targetArr.optJSONObject(k) ?: continue
                                val peer = row.optJSONObject("peer")
                                val peerName = peer?.optString("username")?.takeIf { it.isNotBlank() }
                                    ?: peer?.optString("name")?.takeIf { it.isNotBlank() }
                                    ?: row.optString("username").takeIf { it.isNotBlank() }
                                    ?: row.optString("userName").takeIf { it.isNotBlank() }
                                    ?: row.optString("name").takeIf { it.isNotBlank() }
                                    ?: "Student"

                                val peerId = peer?.optString("peerid")?.takeIf { it.isNotBlank() }
                                    ?: row.optString("peer_id").takeIf { it.isNotBlank() }
                                    ?: ""

                                val durRaw = if (row.has("duration")) row.optDouble("duration", 0.0) else 0.0
                                val durSec = if (durRaw > 100) durRaw / 1000.0 else durRaw

                                entries.add(
                                    LivePollLeaderboardEntry(
                                        position = row.optInt("position", k + 1),
                                        peerId = peerId,
                                        userId = peer?.optString("userid", "") ?: "",
                                        userName = peerName,
                                        score = row.optInt("score", 10),
                                        totalScore = row.optInt("total_score", 10).coerceAtLeast(10),
                                        durationSeconds = durSec,
                                        correctResponses = row.optInt("correct_responses", 1),
                                        totalResponses = row.optInt("total_responses", 1)
                                    )
                                )
                            }
                            if (pollIdInResult.isNotBlank() && entries.isNotEmpty()) {
                                val currentMap = _pollLeaderboard.value.toMutableMap()
                                currentMap[pollIdInResult] = entries
                                _pollLeaderboard.value = currentMap

                                if (_activePoll.value?.pollId == pollIdInResult) {
                                    _activePoll.value = _activePoll.value?.copy(leaderboard = entries)
                                }
                                _previousPolls.value = _previousPolls.value.map {
                                    if (it.pollId == pollIdInResult) it.copy(leaderboard = entries) else it
                                }
                            }
                        } else if (questionsArr != null && questionsArr.length() > 0) {
                            // 2b. Questions result (supports flat array or nested "question" object)
                            val parsedQuestions = mutableListOf<LivePollQuestion>()
                            for (qIdx in 0 until questionsArr.length()) {
                                val qEntry = questionsArr.optJSONObject(qIdx) ?: continue
                                val qObj = qEntry.optJSONObject("question") ?: qEntry
                                val qIndex = qObj.optInt("index", qIdx + 1)
                                val qText = qObj.optString("text", "Question").ifBlank { "Question" }
                                val qType = qObj.optString("type", "single-choice")
                                val qWeight = qObj.optInt("weight", 10)

                                val optionsArr = qEntry.optJSONArray("options") ?: qObj.optJSONArray("options")
                                val parsedOptions = mutableListOf<LivePollOption>()
                                if (optionsArr != null) {
                                    for (oIdx in 0 until optionsArr.length()) {
                                        val optObj = optionsArr.optJSONObject(oIdx)
                                        if (optObj != null) {
                                            parsedOptions.add(
                                                LivePollOption(
                                                    index = optObj.optInt("index", oIdx + 1),
                                                    text = optObj.optString("text", "${('A'.code + oIdx).toChar()}")
                                                )
                                            )
                                        } else {
                                            parsedOptions.add(
                                                LivePollOption(
                                                    index = oIdx + 1,
                                                    text = "${('A'.code + oIdx).toChar()}"
                                                )
                                            )
                                        }
                                    }
                                }

                                if (parsedOptions.isEmpty()) {
                                    parsedOptions.addAll(
                                        listOf(
                                            LivePollOption(1, "A"),
                                            LivePollOption(2, "B"),
                                            LivePollOption(3, "C"),
                                            LivePollOption(4, "D")
                                        )
                                    )
                                }

                                val ansObj = qEntry.optJSONObject("answer") ?: qObj.optJSONObject("answer")
                                val correctOpt = when {
                                    ansObj != null && ansObj.has("option") -> ansObj.optInt("option")
                                    ansObj != null && ansObj.has("options") -> ansObj.optJSONArray("options")?.optInt(0)
                                    qEntry.has("correct_option") -> qEntry.optInt("correct_option")
                                    qObj.has("correct_option") -> qObj.optInt("correct_option")
                                    else -> null
                                }

                                parsedQuestions.add(
                                    LivePollQuestion(
                                        index = qIndex,
                                        text = qText,
                                        type = qType,
                                        weight = qWeight,
                                        options = parsedOptions,
                                        correctOptionIndex = correctOpt
                                    )
                                )
                            }

                            if (pollIdInResult.isNotBlank() && parsedQuestions.isNotEmpty()) {
                                val firstQ = parsedQuestions.firstOrNull()
                                if (_activePoll.value?.pollId == pollIdInResult) {
                                    val curr = _activePoll.value!!
                                    val updatedResp = curr.userResponse?.let { resp ->
                                        if (firstQ?.correctOptionIndex != null) {
                                            resp.copy(isCorrect = resp.selectedOptionIndex == firstQ.correctOptionIndex)
                                        } else resp
                                    }
                                    _activePoll.value = curr.copy(
                                        questions = parsedQuestions,
                                        userResponse = updatedResp
                                    )
                                }
                                _previousPolls.value = _previousPolls.value.map { p ->
                                    if (p.pollId == pollIdInResult) {
                                        val updatedResp = p.userResponse?.let { resp ->
                                            if (firstQ?.correctOptionIndex != null) {
                                                resp.copy(isCorrect = resp.selectedOptionIndex == firstQ.correctOptionIndex)
                                            } else resp
                                        }
                                        p.copy(questions = parsedQuestions, userResponse = updatedResp)
                                    } else p
                                }
                            }
                        }

                        // 3. Check if result contains User Responses (from poll-responses)
                        val responsesArr = result.optJSONArray("responses")
                        if (responsesArr != null && responsesArr.length() > 0) {
                            val rEntry = responsesArr.optJSONObject(0)
                            val rObj = rEntry?.optJSONObject("response") ?: rEntry
                            if (rObj != null && pollIdInResult.isNotBlank()) {
                                val chosenOpt = rObj.optInt("option", 1)
                                val qIdx = rObj.optInt("question", 1)
                                val userResp = LivePollUserResponse(
                                    pollId = pollIdInResult,
                                    questionIndex = qIdx,
                                    selectedOptionIndex = chosenOpt,
                                    durationMs = rObj.optLong("duration", 0L),
                                    isCorrect = rObj.optBoolean("correct", false)
                                )
                                if (_activePoll.value?.pollId == pollIdInResult) {
                                    _activePoll.value = _activePoll.value?.copy(userResponse = userResp)
                                }
                                _previousPolls.value = _previousPolls.value.map {
                                    if (it.pollId == pollIdInResult) it.copy(userResponse = userResp) else it
                                }
                            }
                        }
                    }

                    // 100ms Live Poll Server Events
                    if (method == "on-poll-start") {
                        val params = msg.optJSONObject("params")
                        val pollList = mutableListOf<JSONObject>()
                        val pollsArr = params?.optJSONArray("polls")
                        if (pollsArr != null) {
                            for (i in 0 until pollsArr.length()) {
                                pollsArr.optJSONObject(i)?.let { pollList.add(it) }
                            }
                        } else if (params?.optJSONObject("poll") != null) {
                            pollList.add(params.optJSONObject("poll")!!)
                        } else if (params?.has("poll_id") == true) {
                            pollList.add(params)
                        }

                        for (pObj in pollList) {
                            val pollId = pObj.optString("poll_id").ifBlank { pObj.optString("id") }
                            if (pollId.isBlank()) continue
                            val title = pObj.optString("title", "Quiz")
                            val type = pObj.optString("type", "quiz")
                            val createdBy = pObj.optString("created_by")

                            val newPoll = LivePollItem(
                                pollId = pollId,
                                title = title,
                                state = "started",
                                type = type,
                                createdBy = createdBy,
                                startedAt = pObj.optLong("started_at", System.currentTimeMillis() / 1000)
                            )
                            _activePoll.value = newPoll

                            val list = _previousPolls.value.filter { it.pollId != pollId }.toMutableList()
                            list.add(0, newPoll)
                            _previousPolls.value = list

                            fetchPollQuestions(pollId)
                        }
                    }

                    if (method == "on-poll-stop") {
                        val params = msg.optJSONObject("params")
                        val stoppedIds = mutableListOf<String>()
                        val pollsArr = params?.optJSONArray("polls")
                        if (pollsArr != null) {
                            for (i in 0 until pollsArr.length()) {
                                val id = pollsArr.optJSONObject(i)?.optString("poll_id")
                                if (!id.isNullOrBlank()) stoppedIds.add(id)
                            }
                        } else if (params?.optJSONObject("poll") != null) {
                            val id = params.optJSONObject("poll")?.optString("poll_id")
                            if (!id.isNullOrBlank()) stoppedIds.add(id)
                        } else if (params?.has("poll_id") == true) {
                            val id = params.optString("poll_id")
                            if (id.isNotBlank()) stoppedIds.add(id)
                        }

                        for (pollId in stoppedIds) {
                            if (_activePoll.value?.pollId == pollId) {
                                _activePoll.value = _activePoll.value?.copy(state = "stopped")
                            }
                            _previousPolls.value = _previousPolls.value.map {
                                if (it.pollId == pollId) it.copy(state = "stopped") else it
                            }
                            // Fetch questions to get revealed answer + fetch leaderboard + user response
                            fetchPollQuestions(pollId)
                            fetchPollLeaderboard(pollId)
                            fetchUserPollResponse(pollId)
                        }
                    }

                    if (method == "on-poll-stats") {
                        val params = msg.optJSONObject("params")
                        val polls = params?.optJSONArray("polls")
                        val stObj = polls?.optJSONObject(0) ?: params?.optJSONObject("poll") ?: params
                        val pollId = stObj?.optString("poll_id") ?: ""
                        val total = stObj?.optInt("total_response", 0) ?: 0
                        val userCount = stObj?.optInt("user_count", 0) ?: 0
                        if (pollId.isNotBlank() && _activePoll.value?.pollId == pollId) {
                            _activePoll.value = _activePoll.value?.copy(
                                totalResponses = total,
                                votedUsers = userCount
                            )
                        }
                    }

                    // Universal regex fallback for any HLS m3u8 stream link in websocket frame
                    if (_masterUrl.value.isNullOrBlank() && text.contains(".m3u8", ignoreCase = true)) {
                        val regex = Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")
                        val match = regex.find(text)?.value
                        if (!match.isNullOrBlank()) {
                            val cleanUrl = match.replace("\\/", "/")
                            Log.d(TAG, "Regex extracted Master HLS Link: $cleanUrl")
                            _masterUrl.value = cleanUrl
                        }
                    }

                    // Broadcast & Direct chat messages parsing (matching 100ms / Shikho real HAR file protocol)
                    if (method == "on-broadcast" || method == "broadcast" || method == "on-message" || method == "message") {
                        val params = msg.optJSONObject("params")
                        var chatText: String? = null
                        var sender: String? = null
                        var role: String? = null
                        var timestampMillis = System.currentTimeMillis()

                        // 1. Try reading info parameter (which in 100ms HAR can be a JSON string, a JSONObject, or plain text)
                        val infoRaw = params?.opt("info")
                        when (infoRaw) {
                            is JSONObject -> {
                                chatText = infoRaw.optString("message").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("msg").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("text").takeIf { it.isNotBlank() }
                                sender = infoRaw.optString("sender").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("name").takeIf { it.isNotBlank() }
                                role = infoRaw.optString("role").takeIf { it.isNotBlank() }
                                if (infoRaw.has("time")) timestampMillis = infoRaw.optLong("time", timestampMillis)
                            }
                            is String -> {
                                if (infoRaw.trim().startsWith("{") && infoRaw.trim().endsWith("}")) {
                                    try {
                                        val infoJson = JSONObject(infoRaw)
                                        chatText = infoJson.optString("message").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("msg").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("text").takeIf { it.isNotBlank() }
                                        sender = infoJson.optString("sender").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("name").takeIf { it.isNotBlank() }
                                        role = infoJson.optString("role").takeIf { it.isNotBlank() }
                                        if (infoJson.has("time")) timestampMillis = infoJson.optLong("time", timestampMillis)
                                    } catch (_: Exception) {
                                        chatText = infoRaw
                                    }
                                } else {
                                    chatText = infoRaw
                                }
                            }
                        }

                        // 2. Fallbacks directly from params if not extracted from info
                        if (chatText.isNullOrBlank()) {
                            chatText = params?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("msg")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("text")?.takeIf { it.isNotBlank() }
                        }

                        if (sender.isNullOrBlank()) {
                            val peer = params?.optJSONObject("peer")
                            sender = peer?.optString("name")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("sender")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("from")?.takeIf { it.isNotBlank() }
                                ?: "শিক্ষার্থী"
                            if (role.isNullOrBlank()) {
                                role = peer?.optString("role")?.takeIf { it.isNotBlank() }
                            }
                        }

                        if (role.isNullOrBlank()) {
                            val rolesArr = params?.optJSONArray("roles")
                            if (rolesArr != null && rolesArr.length() > 0) {
                                role = rolesArr.optString(0)
                            } else {
                                role = params?.optString("role")?.takeIf { it.isNotBlank() }
                            }
                        }

                        // Determine normalized role: teacher, student, all
                        val normalizedRole = when {
                            role?.contains("teacher", ignoreCase = true) == true ||
                            sender?.contains("Teacher", ignoreCase = true) == true ||
                            sender?.contains("শিক্ষক") == true -> "teacher"
                            role?.equals("*") == true || role?.contains("all", ignoreCase = true) == true -> "all"
                            else -> "student"
                        }

                        val isTeacher = normalizedRole == "teacher"

                        if (!chatText.isNullOrBlank()) {
                            val formattedTime = try {
                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(timestampMillis))
                            } catch (_: Exception) {
                                "এখন"
                            }

                            _incomingChatMessages.value = LiveChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderName = sender ?: "শিক্ষার্থী",
                                message = chatText,
                                timestamp = formattedTime,
                                isTeacher = isTeacher,
                                role = normalizedRole
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message: ${e.message}", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                _isConnected.value = false
                // CRITICAL FIX: If masterUrl is ALREADY ACQUIRED, the user's video player is actively
                // streaming over HLS via ExoPlayer. The WebSocket is only a signaling/chat layer.
                // A dropped WebSocket must NEVER kill active video playback or crash to fatal error!
                if (_masterUrl.value.isNullOrBlank()) {
                    _error.value = t.message ?: "WebSocket connection failed"
                } else {
                    Log.d(TAG, "WebSocket dropped while stream is already active; keeping playback uninterrupted.")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                _isConnected.value = false
            }
        })
    }

    /**
     * Send broadcast chat message conforming exactly to 100ms / Shikho real HAR file protocol.
     * Roles supported: "teacher", "student", or "*" / "all"
     */
    fun sendChatMessage(
        text: String,
        studentName: String,
        role: String = "student",
        targetRole: String = "*"
    ) {
        val ws = webSocket ?: return
        try {
            val normalizedRole = when (role.lowercase()) {
                "teacher" -> "teacher"
                "student", "students" -> "student"
                else -> "all"
            }

            val targetRolesArray = org.json.JSONArray().apply {
                when (targetRole.lowercase()) {
                    "teacher" -> put("teacher")
                    "student", "students" -> {
                        put("student")
                        put("students")
                    }
                    else -> put("*")
                }
            }

            val broadcastMsg = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("method", "broadcast")
                put("params", JSONObject().apply {
                    put("info", JSONObject().apply {
                        put("message", text)
                        put("sender", studentName)
                        put("role", normalizedRole)
                        put("time", System.currentTimeMillis())
                    }.toString())
                    put("type", "chat")
                    put("roles", targetRolesArray)
                })
                put("jsonrpc", "2.0")
            }
            Log.d(TAG, "Sending broadcast message (role: $normalizedRole, targets: $targetRolesArray): ${broadcastMsg.toString()}")
            ws.send(broadcastMsg.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send chat message over WebSocket: ${e.message}")
        }
    }

    /**
     * Fetch questions for a specific poll via 100ms protocol: "poll-questions-get"
     */
    fun fetchPollQuestions(pollId: String) {
        val ws = webSocket ?: return
        try {
            val req = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("method", "poll-questions-get")
                put("params", JSONObject().apply {
                    put("index", 0)
                    put("count", 50)
                    put("poll_id", pollId)
                    put("version", "1.0")
                })
                put("jsonrpc", "2.0")
            }
            Log.d(TAG, "Requesting poll questions for $pollId: $req")
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request poll questions: ${e.message}")
        }
    }

    /**
     * Submit response to a live poll question via 100ms protocol: "poll-response"
     */
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

        // Immediately record local answer
        val localUserResp = LivePollUserResponse(
            pollId = pollId,
            questionIndex = questionIndex,
            selectedOptionIndex = optionIndex,
            durationMs = durationMs,
            isCorrect = isCorrect
        )
        if (_activePoll.value?.pollId == pollId) {
            _activePoll.value = _activePoll.value?.copy(userResponse = localUserResp)
        }
        _previousPolls.value = _previousPolls.value.map {
            if (it.pollId == pollId) it.copy(userResponse = localUserResp) else it
        }

        val ws = webSocket
        if (ws != null) {
            try {
                val responsesArr = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "")
                        put("duration", durationMs)
                        put("question", questionIndex)
                        put("type", "single-choice")
                        put("option", optionIndex)
                        put("skipped", false)
                        put("update", false)
                    })
                }

                val req = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "poll-response")
                    put("params", JSONObject().apply {
                        put("poll_id", pollId)
                        put("responses", responsesArr)
                        put("version", "1.0")
                    })
                    put("jsonrpc", "2.0")
                }
                Log.d(TAG, "Submitting poll answer for $pollId: $req")
                ws.send(req.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to submit poll answer: ${e.message}")
            }
        }
    }

    /**
     * Allows triggering or testing a real live poll in the class session at any time.
     */
    fun launchTestPoll(
        title: String = "৭",
        questionText: String = "বলের এসআই (SI) একক কোনটি?",
        options: List<String> = listOf("প্যাসকেল (Pascal)", "জুল (Joule)", "নিউটন (Newton)", "ওয়াট (Watt)"),
        correctOptionIndex: Int = 3,
        teacherName: String = "Md. Sayedur Rahman"
    ) {
        val testPollId = "test_${System.currentTimeMillis()}"
        val pollQuestion = LivePollQuestion(
            index = 1,
            text = questionText,
            type = "single-choice",
            weight = 10,
            options = options.mapIndexed { idx, optText ->
                LivePollOption(index = idx + 1, text = optText)
            },
            correctOptionIndex = correctOptionIndex
        )

        val newPoll = LivePollItem(
            pollId = testPollId,
            title = title,
            state = "started",
            type = "quiz",
            teacherName = teacherName,
            startedAt = System.currentTimeMillis() / 1000,
            questionsCount = 1,
            questions = listOf(pollQuestion)
        )

        _activePoll.value = newPoll
        val list = _previousPolls.value.filter { it.pollId != testPollId }.toMutableList()
        list.add(0, newPoll)
        _previousPolls.value = list
    }

    /**
     * Stop/end an active poll and reveal answers.
     */
    fun stopActivePoll(pollId: String) {
        val curr = _activePoll.value
        if (curr != null && curr.pollId == pollId) {
            _activePoll.value = curr.copy(
                state = "stopped",
                stoppedAt = System.currentTimeMillis() / 1000
            )
        }
        _previousPolls.value = _previousPolls.value.map {
            if (it.pollId == pollId) it.copy(state = "stopped", stoppedAt = System.currentTimeMillis() / 1000) else it
        }
    }

    /**
     * Fetch user's own response for a poll via 100ms protocol: "poll-responses"
     */
    fun fetchUserPollResponse(pollId: String) {
        val ws = webSocket ?: return
        try {
            val req = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
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
            Log.d(TAG, "Requesting user poll response for $pollId: $req")
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch user poll response: ${e.message}")
        }
    }

    /**
     * Fetch leaderboard ranking for an ended poll via 100ms protocol: "poll-leaderboard"
     */
    fun fetchPollLeaderboard(pollId: String) {
        val ws = webSocket ?: return
        try {
            val req = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
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
            Log.d(TAG, "Requesting poll leaderboard for $pollId: $req")
            ws.send(req.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch poll leaderboard: ${e.message}")
        }
    }

    fun dismissActivePoll() {
        _activePoll.value = null
    }

    fun setDirectMasterUrl(url: String) {
        if (url.isNotBlank()) {
            Log.d(TAG, "Setting direct Master URL: $url")
            _masterUrl.value = url
            _isConnected.value = true
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Leaving Live Class")
            webSocket = null
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
