package com.example.liveclass

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HmsRoomTokens(
    val primaryToken: String,
    val teacherToken: String? = null
)

data class LiveStudyMaterial(
    val id: String,
    val name: String,
    val fileUrl: String
)

data class AcademicLiveClassInfo(
    val id: String,
    val title: String? = null,
    val playbackUrl: String? = null,
    val isOnGoing: Boolean = false,
    val classType: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val slideUrls: List<String> = emptyList(),
    val studyMaterials: List<LiveStudyMaterial> = emptyList(),
    val topicNames: List<String> = emptyList(),
    val candidateUrls: List<String> = emptyList()
)

class LiveClassService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val TAG = "LiveClassService"
        private const val GRAPHQL_URL = "https://api.shikho.com/graphql"
        private const val HMS_TOKEN_URL = "https://api.shikho.com/hms/token"
        private const val BUILD_VERSION = "(605) 6.0.5"
        private const val TIMEZONE = "Asia/Dhaka"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private fun extractRoomIdFromJoinLink(joinLink: String?): String? {
        if (joinLink.isNullOrBlank()) return null
        val meetingMatch = Regex("""/meeting/([a-zA-Z0-9_\-]+)""").find(joinLink)?.groupValues?.getOrNull(1)
        if (!meetingMatch.isNullOrBlank()) return meetingMatch
        val roomParamMatch = Regex("""[?&]room_?id=([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(joinLink)?.groupValues?.getOrNull(1)
        return roomParamMatch?.takeIf { it.isNotBlank() }
    }

    private fun executeJoinLiveClassMutation(
        primaryId: String,
        secondaryId: String,
        userToken: String
    ): Pair<String?, String?> {
        val jsonBody = JSONObject().apply {
            put(
                "query",
                "mutation JoinLiveClass(\$joinLiveCLassId: String!, \$lesson_id: String!) { joinLiveCLass(id: \$joinLiveCLassId, lesson_id: \$lesson_id) { join_link provider hms_room_id } }"
            )
            put("variables", JSONObject().apply {
                put("joinLiveCLassId", primaryId)
                put("lesson_id", secondaryId)
            })
        }

        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $userToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Build-Version", BUILD_VERSION)
            .addHeader("X-User-Timezone", TIMEZONE)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "GraphQL JoinLiveClass failed (${response.code}): $responseBody")
                return Pair(null, "সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি (কোড ${response.code})")
            }

            val jsonResponse = JSONObject(responseBody)
            val joinLiveClass = jsonResponse.optJSONObject("data")?.optJSONObject("joinLiveCLass")
            val directRoomId = joinLiveClass?.optString("hms_room_id")?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val joinLink = joinLiveClass?.optString("join_link")?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            val resolvedRoomId = directRoomId ?: extractRoomIdFromJoinLink(joinLink)
            if (!resolvedRoomId.isNullOrBlank()) {
                return Pair(resolvedRoomId, null)
            }

            val firstError = jsonResponse.optJSONArray("errors")
                ?.optJSONObject(0)
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
            return Pair(null, firstError)
        }
    }

    /**
     * Step 1: GraphQL API call to get hms_room_id
     */
    suspend fun getHmsRoomId(
        classId: String,
        lessonId: String,
        userToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanClassId = classId.trim()
            val cleanLessonId = lessonId.trim().ifBlank { cleanClassId }

            val (firstRoomId, firstErr) = executeJoinLiveClassMutation(cleanClassId, cleanLessonId, userToken)
            var roomId = firstRoomId
            var lastErrorMsg = firstErr

            // Retry with swapped IDs if first attempt returned no room and classId != lessonId
            if (roomId.isNullOrBlank() && cleanClassId != cleanLessonId && cleanLessonId.isNotBlank()) {
                try {
                    val (swappedRoomId, swappedErr) = executeJoinLiveClassMutation(cleanLessonId, cleanClassId, userToken)
                    if (!swappedRoomId.isNullOrBlank()) {
                        roomId = swappedRoomId
                        Log.d(TAG, "Extracted room ID from swapped ID query: $roomId")
                    } else if (!swappedErr.isNullOrBlank() && lastErrorMsg.isNullOrBlank()) {
                        lastErrorMsg = swappedErr
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Swapped ID query attempt failed: ${e.message}")
                }
            }

            if (roomId.isNullOrBlank() && cleanClassId.length in 20..36 && !cleanClassId.contains(" ")) {
                Log.d(TAG, "Using classId directly as fallback room ID: $cleanClassId")
                roomId = cleanClassId
            }

            if (!roomId.isNullOrBlank()) {
                Log.d(TAG, "Successfully retrieved hms_room_id: $roomId")
                Result.success(roomId)
            } else {
                val errorMsg = lastErrorMsg
                    ?: "লাইভ ক্লাসের রুম পাওয়া যায়নি বা ক্লাসটি এখনো শুরু হয়নি।"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HMS Room ID: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractJwtRole(token: String?): String {
        if (token.isNullOrBlank()) return ""
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                JSONObject(String(decoded, Charsets.UTF_8)).optString("role").trim()
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun requestHmsTokenInternal(
        jsonBody: JSONObject,
        userToken: String
    ): Result<String> {
        return try {
            val request = Request.Builder()
                .url(HMS_TOKEN_URL)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Build-Version", BUILD_VERSION)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        Exception("লাইভ ক্লাস টোকেন সংগ্রহে ত্রুটি হয়েছে (কোড ${response.code})")
                    )
                }

                val jsonResponse = JSONObject(responseBody)
                val token = jsonResponse.optString("token").trim().ifBlank {
                    jsonResponse.optJSONObject("data")?.optString("token")?.trim().orEmpty()
                }

                if (token.isNotBlank() && !token.equals("null", ignoreCase = true)) {
                    Result.success(token)
                } else {
                    Result.failure(Exception("100ms token not found in response"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Step 2: HMS Token API call to get 100ms token
     */
    suspend fun getHmsToken(
        roomId: String,
        userToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanRoomId = roomId.trim()
        val standardBody = JSONObject().apply {
            put("room_id", cleanRoomId)
            put("type", "android")
        }
        requestHmsTokenInternal(standardBody, userToken)
    }

    /**
     * Fetches both the standard student 100ms token and attempts to obtain a teacher-role token
     * so the app can receive messages sent by other students who select "teacher" as recipient.
     */
    suspend fun getHmsRoomTokens(
        roomId: String,
        userToken: String
    ): Result<HmsRoomTokens> = withContext(Dispatchers.IO) {
        coroutineScope {
            val cleanRoomId = roomId.trim()

            val primaryDeferred = async {
                val standardBody = JSONObject().apply {
                    put("room_id", cleanRoomId)
                    put("type", "android")
                }
                requestHmsTokenInternal(standardBody, userToken)
            }

            val teacherDeferred = async {
                val candidatePayloads = listOf(
                    JSONObject().apply {
                        put("room_id", cleanRoomId)
                        put("type", "android")
                        put("role", "teacher")
                    },
                    JSONObject().apply {
                        put("room_id", cleanRoomId)
                        put("type", "teacher")
                        put("role", "teacher")
                    },
                    JSONObject().apply {
                        put("room_id", cleanRoomId)
                        put("type", "web")
                        put("role", "teacher")
                        put("user_role", "teacher")
                    }
                )

                var fallbackToken: String? = null
                for (payload in candidatePayloads) {
                    val res = requestHmsTokenInternal(payload, userToken).getOrNull()
                    if (!res.isNullOrBlank()) {
                        val jwtRole = extractJwtRole(res)
                        if (jwtRole.contains("teacher", ignoreCase = true) ||
                            jwtRole.contains("host", ignoreCase = true) ||
                            jwtRole.contains("moderator", ignoreCase = true)
                        ) {
                            return@async res
                        }
                        if (fallbackToken == null) {
                            fallbackToken = res
                        }
                    }
                }
                fallbackToken
            }

            val primaryRes = primaryDeferred.await()
            val teacherToken = teacherDeferred.await()

            val primaryToken = primaryRes.getOrNull() ?: teacherToken
            if (!primaryToken.isNullOrBlank()) {
                Result.success(
                    HmsRoomTokens(
                        primaryToken = primaryToken,
                        teacherToken = teacherToken ?: primaryToken
                    )
                )
            } else {
                Result.failure(
                    primaryRes.exceptionOrNull()
                        ?: Exception("লাইভ ক্লাস টোকেন সংগ্রহে ত্রুটি হয়েছে")
                )
            }
        }
    }

    /**
     * Query academic program live class details using Shikho's valid GraphQL schema.
     * Extracts playback_url (direct HLS stream or recording), on_going status, lecture slides, and metadata.
     */
    suspend fun getAcademicLiveClassInfo(
        classId: String,
        lessonId: String,
        userToken: String
    ): Result<AcademicLiveClassInfo> = withContext(Dispatchers.IO) {
        val candidateIds = listOf(classId.trim(), lessonId.trim()).distinct().filter { it.isNotBlank() }
        for (id in candidateIds) {
            try {
                val queryStr = """
                    query GetAcademicLiveClassDetails(${'$'}id: String!) {
                      academicProgramLiveClass(id: ${'$'}id) {
                        id
                        title
                        playback_url
                        on_going
                        class_type
                        start_time
                        end_time
                        study_materials {
                          file_url
                          id
                          name
                        }
                        topics {
                          id
                          name
                        }
                      }
                    }
                """.trimIndent()

                val jsonBody = JSONObject().apply {
                    put("query", queryStr)
                    put("variables", JSONObject().apply {
                        put("id", id)
                    })
                }

                val request = Request.Builder()
                    .url(GRAPHQL_URL)
                    .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("Authorization", "Bearer $userToken")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Build-Version", BUILD_VERSION)
                    .addHeader("X-User-Timezone", TIMEZONE)
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use
                    val json = JSONObject(body)
                    val liveClass = json.optJSONObject("data")?.optJSONObject("academicProgramLiveClass")
                    if (liveClass != null) {
                        val foundId = liveClass.optString("id", id)
                        val title = liveClass.optString("title")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        val pbUrl = liveClass.optString("playback_url")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        val onGoing = liveClass.optBoolean("on_going", false)
                        val classType = liveClass.optString("class_type")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        val startTime = liveClass.optString("start_time")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        val endTime = liveClass.optString("end_time")
                            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                        val slides = mutableListOf<String>()
                        val studyMaterials = mutableListOf<LiveStudyMaterial>()
                        val materials = liveClass.optJSONArray("study_materials")
                        if (materials != null) {
                            for (i in 0 until materials.length()) {
                                val mat = materials.optJSONObject(i) ?: continue
                                val fUrl = mat.optString("file_url").trim()
                                if (fUrl.isNotBlank() && !fUrl.equals("null", ignoreCase = true)) {
                                    slides.add(fUrl)
                                    val matId = mat.optString("id").ifBlank { "slide_${i + 1}" }
                                    val matName = mat.optString("name")
                                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                        ?: "লেকচার নোট ${i + 1}"
                                    studyMaterials.add(
                                        LiveStudyMaterial(
                                            id = matId,
                                            name = matName,
                                            fileUrl = fUrl
                                        )
                                    )
                                }
                            }
                        }

                        val topicNames = mutableListOf<String>()
                        val topicsArr = liveClass.optJSONArray("topics")
                        if (topicsArr != null) {
                            for (i in 0 until topicsArr.length()) {
                                val topicObj = topicsArr.optJSONObject(i) ?: continue
                                val tName = topicObj.optString("name").trim()
                                if (tName.isNotBlank() && !tName.equals("null", ignoreCase = true)) {
                                    topicNames.add(tName)
                                }
                            }
                        }

                        val candidates = mutableListOf<String>()
                        if (!pbUrl.isNullOrBlank()) {
                            candidates.add(pbUrl)
                        }

                        Log.d(
                            TAG,
                            "Found academic live class info for $id: pbUrl=$pbUrl, onGoing=$onGoing, slidesCount=${slides.size}"
                        )
                        return@withContext Result.success(
                            AcademicLiveClassInfo(
                                id = foundId,
                                title = title,
                                playbackUrl = pbUrl,
                                isOnGoing = onGoing,
                                classType = classType,
                                startTime = startTime,
                                endTime = endTime,
                                slideUrls = slides.distinct(),
                                studyMaterials = studyMaterials,
                                topicNames = topicNames,
                                candidateUrls = candidates.distinct()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Querying academic live class info for $id failed: ${e.message}")
            }
        }
        Result.failure(Exception("Academic live class details not found"))
    }

    /**
     * Fallback to query academic program live class details for direct HLS or live stream URL
     */
    suspend fun getDirectLiveClassStream(
        classId: String,
        userToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val infoResult = getAcademicLiveClassInfo(classId, "", userToken)
        val info = infoResult.getOrNull()
        val direct = info?.playbackUrl
        if (!direct.isNullOrBlank()) {
            Log.d(TAG, "Found direct stream/playback URL: $direct")
            Result.success(direct)
        } else {
            Result.failure(Exception("No direct playback stream URL available"))
        }
    }
}
