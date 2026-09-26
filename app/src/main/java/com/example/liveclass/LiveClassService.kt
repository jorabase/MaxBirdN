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
            val roomId = joinLiveClass?.optString("hms_room_id")

            if (!roomId.isNullOrBlank()) {
                Log.d(TAG, "Successfully retrieved hms_room_id: $roomId")
                Result.success(roomId)
            } else {
                val errors = jsonResponse.optJSONArray("errors")
                val errorMsg = errors?.optJSONObject(0)?.optString("message") ?: "Unable to get HMS room ID"
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
}
