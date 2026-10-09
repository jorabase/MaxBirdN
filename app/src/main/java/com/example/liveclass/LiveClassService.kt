package com.example.liveclass

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveClassService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "LiveClassService"
        private const val GRAPHQL_URL = "https://api.shikho.com/graphql"
        private const val HMS_TOKEN_URL = "https://api.shikho.com/hms/token"
        private const val BUILD_VERSION = "(605) 6.0.5"
        private const val TIMEZONE = "Asia/Dhaka"
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
            val jsonBody = JSONObject().apply {
                put("query", "mutation JoinLiveClass(\$joinLiveCLassId: String!, \$lesson_id: String!) { joinLiveCLass(id: \$joinLiveCLassId, lesson_id: \$lesson_id) { join_link provider hms_room_id } }")
                put("variables", JSONObject().apply {
                    put("joinLiveCLassId", classId)
                    put("lesson_id", lessonId)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(GRAPHQL_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Build-Version", BUILD_VERSION)
                .addHeader("X-User-Timezone", TIMEZONE)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "GraphQL request failed (${response.code}): $responseBody")
                return@withContext Result.failure(Exception("GraphQL request failed with code ${response.code}"))
            }

            val jsonResponse = JSONObject(responseBody)
            val data = jsonResponse.optJSONObject("data")
            val joinLiveClass = data?.optJSONObject("joinLiveCLass")
            var roomId = joinLiveClass?.optString("hms_room_id")?.trim()
            val joinLink = joinLiveClass?.optString("join_link")?.trim()

            if (roomId.isNullOrBlank() && !joinLink.isNullOrBlank()) {
                val extracted = Regex("""/meeting/([a-zA-Z0-9_\-]+)""").find(joinLink)?.groupValues?.getOrNull(1)
                if (!extracted.isNullOrBlank()) {
                    roomId = extracted
                    Log.d(TAG, "Extracted room ID from join_link: $roomId")
                }
            }

            // Retry with swapped IDs if first attempt returned no room and classId != lessonId
            if (roomId.isNullOrBlank() && classId != lessonId && lessonId.isNotBlank()) {
                try {
                    val swapBody = JSONObject().apply {
                        put("query", "mutation JoinLiveClass(\$joinLiveCLassId: String!, \$lesson_id: String!) { joinLiveCLass(id: \$joinLiveCLassId, lesson_id: \$lesson_id) { join_link provider hms_room_id } }")
                        put("variables", JSONObject().apply {
                            put("joinLiveCLassId", lessonId)
                            put("lesson_id", classId)
                        })
                    }
                    val swapRequest = Request.Builder()
                        .url(GRAPHQL_URL)
                        .post(swapBody.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("Authorization", "Bearer $userToken")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Build-Version", BUILD_VERSION)
                        .addHeader("X-User-Timezone", TIMEZONE)
                        .build()
                    val swapResp = client.newCall(swapRequest).execute()
                    val swapJson = JSONObject(swapResp.body?.string() ?: "")
                    val swapJoin = swapJson.optJSONObject("data")?.optJSONObject("joinLiveCLass")
                    val altRoomId = swapJoin?.optString("hms_room_id")?.trim()
                    if (!altRoomId.isNullOrBlank()) {
                        roomId = altRoomId
                        Log.d(TAG, "Extracted room ID from swapped ID query: $roomId")
                    } else {
                        val swapJoinLink = swapJoin?.optString("join_link")?.trim()
                        if (!swapJoinLink.isNullOrBlank()) {
                            roomId = Regex("""/meeting/([a-zA-Z0-9_\-]+)""").find(swapJoinLink)?.groupValues?.getOrNull(1)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Swapped ID query attempt failed: ${e.message}")
                }
            }

            if (roomId.isNullOrBlank() && classId.length in 20..36 && !classId.contains(" ")) {
                Log.d(TAG, "Using classId directly as fallback room ID: $classId")
                roomId = classId
            }

            if (!roomId.isNullOrBlank()) {
                Log.d(TAG, "Successfully retrieved hms_room_id: $roomId")
                Result.success(roomId)
            } else {
                val errors = jsonResponse.optJSONArray("errors")
                val errorMsg = errors?.optJSONObject(0)?.optString("message") ?: "লাইভ ক্লাসের রুম পাওয়া যায়নি বা ক্লাসটি এখনো লাইভ হয়নি"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HMS Room ID: ${e.message}", e)
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
        try {
            val jsonBody = JSONObject().apply {
                put("room_id", roomId)
                put("type", "android")
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(HMS_TOKEN_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Build-Version", BUILD_VERSION)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "HMS Token request failed (${response.code}): $responseBody")
                return@withContext Result.failure(Exception("Token request failed with code ${response.code}"))
            }

            val jsonResponse = JSONObject(responseBody)
            val token = jsonResponse.optString("token")

            if (!token.isNullOrBlank()) {
                Log.d(TAG, "Successfully retrieved 100ms token")
                Result.success(token)
            } else {
                Result.failure(Exception("100ms token not found in response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching HMS token: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fallback to query academic program live class details for direct HLS or live stream URL
     */
    suspend fun getDirectLiveClassStream(
        classId: String,
        userToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val queryStr = """
                query GetAcademicLiveClassDetails(${'$'}id: String!) {
                  academicProgramLiveClass(id: ${'$'}id) {
                    id
                    playback_url
                    stream_url
                    recording_url
                    hls_url
                    url
                  }
                }
            """.trimIndent()
            val jsonBody = JSONObject().apply {
                put("query", queryStr)
                put("variables", JSONObject().apply {
                    put("id", classId)
                })
            }
            val request = Request.Builder()
                .url(GRAPHQL_URL)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Build-Version", BUILD_VERSION)
                .addHeader("X-User-Timezone", TIMEZONE)
                .build()
            val resp = client.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            val json = JSONObject(body)
            val liveClass = json.optJSONObject("data")?.optJSONObject("academicProgramLiveClass")
            val candidateUrls = listOfNotNull(
                liveClass?.optString("playback_url"),
                liveClass?.optString("stream_url"),
                liveClass?.optString("hls_url"),
                liveClass?.optString("recording_url"),
                liveClass?.optString("url")
            ).filter { it.isNotBlank() && it != "null" }
            val direct = candidateUrls.firstOrNull { it.contains(".m3u8", ignoreCase = true) }
                ?: candidateUrls.firstOrNull()
            if (!direct.isNullOrBlank()) {
                Log.d(TAG, "Found direct live stream URL: $direct")
                Result.success(direct)
            } else {
                Result.failure(Exception("No direct stream URL available"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query direct live stream: ${e.message}")
            Result.failure(e)
        }
    }
}
