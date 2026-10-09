package com.example.api

import com.example.auth.SessionManager
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit

interface ShikhoApiService {

    @POST("/auth/v2/user/check")
    suspend fun checkUser(@Body request: UserCheckRequest): UserCheckResponse

    @POST("/auth/v2/send/sms")
    suspend fun sendSms(@Body request: SendSmsRequest): SendSmsResponse

    @POST("/auth/v2/verify/otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): VerifyOtpResponse

    @POST("/auth/v2/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/auth/v2/logout")
    suspend fun logout(): LogoutResponse

    @POST("/auth/v2/token/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @POST("/graphql")
    suspend fun getProfile(@Body query: GraphQlQuery): ProfileResponse

    @POST("/graphql")
    suspend fun setPin(@Body query: GraphQlQuery): SetPinResponse

    @POST("/graphql")
    suspend fun getAcademicProgram(@Body query: GraphQlQuery): AcademicProgramResponse

    @POST("/graphql")
    suspend fun getPrioritySubjects(@Body query: GraphQlQuery): PrioritySubjectsResponse

    @POST("/graphql")
    suspend fun upsertPrioritySubjects(@Body query: GraphQlQuery): UpsertPrioritySubjectsResponse

    @POST("/graphql")
    suspend fun getAcademicSubjects(@Body query: GraphQlQuery): AcademicSubjectsResponse

    @POST("/graphql")
    suspend fun getProgramPhases(@Body query: GraphQlQuery): ProgramPhasesResponse

    @POST("/graphql")
    suspend fun getPhaseWiseChapters(@Body query: GraphQlQuery): AcademicChaptersResponse

    @POST("/graphql")
    suspend fun getSubjectHierarchyWithQuestionCounts(@Body query: GraphQlQuery): SubjectHierarchyWithQuestionCountsResponse

    @POST("/graphql")
    suspend fun getStudentLessons(@Body query: GraphQlQuery): StudentSpecificLessonsResponse

    @POST("/graphql")
    suspend fun getUpcomingLessonsPhaseWise(@Body query: GraphQlQuery): UpcomingLessonsPhaseWiseResponse

    @POST("/graphql")
    suspend fun getAcademicLiveClassDetails(@Body query: GraphQlQuery): AcademicLiveClassDetailsResponse

    @POST("/graphql")
    suspend fun getTeacherDetails(@Body query: GraphQlQuery): TeacherDetailsResponse

    @POST("/graphql")
    suspend fun getTopics(@Body query: GraphQlQuery): GetTopicsResponse

    @POST("/graphql")
    suspend fun getPracticeQuizAccess(@Body query: GraphQlQuery): PracticeQuizAccessResponse

    @POST("/graphql")
    suspend fun getVideoList(@Body query: GraphQlQuery): VideoListResponse

    @GET("/class_list")
    suspend fun getClassList(
        @Query("vendor") vendor: String = "BD",
        @Query("type") type: String = "syllabus"
    ): ClassListResponse

    @POST("/graphql")
    suspend fun getBatchOptions(@Body query: GraphQlQuery): BatchOptionsResponse

    @POST("/graphql")
    suspend fun availTrial(@Body query: GraphQlQuery): AvailTrialResponse

    @POST("/graphql")
    suspend fun enrollInFreeProgram(@Body query: GraphQlQuery): EnrollInFreeProgramResponse

    @POST("/graphql")
    suspend fun changeSyllabus(@Body query: GraphQlQuery): ChangeSyllabusResponse

    @POST("/graphql")
    suspend fun updateExamYear(@Body query: GraphQlQuery): UpdateExamYearResponse

    @GET("/address")
    suspend fun getAddress(
        @Query("country_code") countryCode: String? = null,
        @Query("division_id") divisionId: String? = null
    ): AddressListResponse

    @POST("/graphql")
    suspend fun getSchools(@Body query: GraphQlQuery): SchoolSearchResponse

    @POST("/graphql")
    suspend fun updateProfile(@Body query: GraphQlQuery): UpdateProfileResponse

    @POST("/graphql")
    suspend fun updateUserSchool(@Body query: GraphQlQuery): UpdateSchoolResponse

    @POST("/graphql")
    suspend fun getLiveExamInfo(@Body query: GraphQlQuery): GetLiveExamInfoResponse

    @POST("/graphql")
    suspend fun getLiveQuestions(@Body query: GraphQlQuery): GetLiveQuestionsResponse

    @POST("/graphql")
    suspend fun submitLiveExam(@Body query: GraphQlQuery): SubmitLiveExamResponse

    @POST("/graphql")
    suspend fun getLiveExamPerformance(@Body query: GraphQlQuery): GetLiveExamPerformanceAnalysisResponse

    @POST("/graphql")
    suspend fun getLiveExamSolutions(@Body query: GraphQlQuery): GetLiveExamSolutionsResponse

    @POST("/graphql")
    suspend fun listTaggableResources(@Body query: GraphQlQuery): TaggableResourcesResponse

    @POST("/graphql")
    suspend fun getResourceAttachmentsOfChapter(@Body query: GraphQlQuery): ResourceAttachmentsResponse

    @POST("/graphql")
    suspend fun getResourceAttachments(@Body query: GraphQlQuery): ResourceAttachmentsResponse

    @POST("/graphql")
    suspend fun startPracticeQuizMcqSession(@Body query: GraphQlQuery): StartPracticeQuizMcqSessionResponse

    @POST("/graphql")
    suspend fun getMcqSession(@Body query: GraphQlQuery): GetMcqSessionResponse

    @POST("/graphql")
    suspend fun submitPracticeQuizMcqSession(@Body query: GraphQlQuery): SubmitPracticeQuizResponse

    @POST("/graphql")
    suspend fun getQuizResultSummary(@Body query: GraphQlQuery): GetQuizResultSummaryResponse

    @POST("/graphql")
    suspend fun getMcqSessionFeedback(@Body query: GraphQlQuery): GetMcqSessionFeedbackResponse

    @POST("/graphql")
    suspend fun createSavedQuestion(@Body query: GraphQlQuery): CreateSavedQuestionResponse

    @GET("https://analytics.shikho.com/api/v1/results/quarterly/quarter/{programId}/{phaseId}")
    suspend fun getQuarterlyResults(
        @Path("programId") programId: String,
        @Path("phaseId") phaseId: String
    ): QuarterlyResultResponse

    @GET("https://analytics.shikho.com/api/v1/results/quarterly/quarter/{programId}/{phaseId}")
    suspend fun getQuarterlyReport(
        @Path("programId") programId: String,
        @Path("phaseId") phaseId: String
    ): QuarterlyReportResponse

    @GET("https://analytics.shikho.com/api/v1/results/quarterly/performance/trend/{phaseId}")
    suspend fun getPerformanceTrend(
        @Path("phaseId") phaseId: String,
        @Query("metric") metric: String = "class_completion",
        @Query("compare_phase_id") comparePhaseId: String = "no_compare"
    ): PerformanceTrendResponse

    @POST("https://analytics.shikho.com/api/v1/results/rankings")
    suspend fun getLeaderboardRankings(
        @Body request: LeaderboardRankingRequest
    ): LeaderboardRankingResponse

    companion object {
        private const val BASE_URL = "https://api.shikho.com"

        @Volatile
        private var cachedInstance: ShikhoApiService? = null

        fun create(sessionManager: SessionManager): ShikhoApiService {
            cachedInstance?.let { return it }

            return synchronized(this) {
                cachedInstance?.let { return it }

                val headerInterceptor = Interceptor { chain ->
                    val token = sessionManager.getAccessToken()
                    val reqBuilder = chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("X-User-Timezone", "Asia/Dhaka")
                        .header("Build-Version", "(607) 6.0.7")
                        .header("User-Agent", "Shikho/(607) 6.0.7 (Android 12; V2029; vivo 2027; en; WIFI; edac7970-6299-4e14-9b98-8c64d3ca4c09)")

                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("Authorization", "Bearer $token")
                    }

                    chain.proceed(reqBuilder.build())
                }

                val enrolmentMockInterceptor = Interceptor { chain ->
                    val origRequest = chain.request()
                    val response = chain.proceed(origRequest)
                    if (!response.isSuccessful) {
                        try {
                            val peek = response.peekBody(1024 * 32).string()
                            if (response.code == 401) {
                                android.util.Log.d("ShikhoApiService", "HTTP 401 unauthorized on ${origRequest.url}: $peek")
                            } else {
                                android.util.Log.e("ShikhoApiService", "HTTP ${response.code} error on ${origRequest.url}: $peek")
                            }
                        } catch (_: Exception) {}
                    }
                    try {
                        val body = response.body
                        if (response.isSuccessful && body != null) {
                            val contentType = body.contentType()
                            val jsonString = body.string()

                            val modifiedString = if (jsonString.contains("has_enrolment") || jsonString.contains("is_active") || 
                                jsonString.contains("is_locked") || jsonString.contains("is_enrolled") || jsonString.contains("is_purchased") ||
                                jsonString.contains("access_level") || jsonString.contains("is_expired") || jsonString.contains("show_trial") ||
                                jsonString.contains("is_free")) {
                                jsonString
                                    .replace(Regex("\"has_enrolment\"\\s*:\\s*false"), "\"has_enrolment\": true")
                                    .replace(Regex("\"has_free_trial_enrolment\"\\s*:\\s*false"), "\"has_free_trial_enrolment\": true")
                                    .replace(Regex("\"is_active\"\\s*:\\s*false"), "\"is_active\": true")
                                    .replace(Regex("\"is_enrolled\"\\s*:\\s*false"), "\"is_enrolled\": true")
                                    .replace(Regex("\"is_purchased\"\\s*:\\s*false"), "\"is_purchased\": true")
                                    .replace(Regex("\"is_locked\"\\s*:\\s*true"), "\"is_locked\": false")
                                    .replace(Regex("\"is_free\"\\s*:\\s*false"), "\"is_free\": true")
                                    .replace(Regex("\"is_expired\"\\s*:\\s*true"), "\"is_expired\": false")
                                    .replace(Regex("\"show_trial\"\\s*:\\s*true"), "\"show_trial\": false")
                                    .replace(Regex("\"access_level\"\\s*:\\s*\"[^\"]+\""), "\"access_level\": \"Full\"")
                                    .replace(Regex("\"type\"\\s*:\\s*\"FullApTrial\""), "\"type\": \"Paid\"")
                                    .replace(Regex("\"enroled_subscription_division\"\\s*:\\s*\"[^\"]+\""), "\"enroled_subscription_division\": \"full\"")
                            } else {
                                jsonString
                            }

                            val newBody = modifiedString.toResponseBody(contentType)
                            return@Interceptor response.newBuilder().body(newBody).build()
                        }
                    } catch (_: Exception) {}
                    response
                }

                val refreshLock = Any()

                fun performTokenRefresh(): String? {
                    synchronized(refreshLock) {
                        val refreshToken = sessionManager.getRefreshToken() ?: sessionManager.getAccessToken()
                        if (refreshToken.isNullOrBlank()) return null

                        try {
                            val refreshClient = OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(10, TimeUnit.SECONDS)
                                .build()

                            val jsonPayload = org.json.JSONObject().apply {
                                put("token", refreshToken)
                            }.toString()

                            val refreshReq = Request.Builder()
                                .url("https://api.shikho.com/auth/v2/token/refresh")
                                .header("Accept", "application/json")
                                .header("Content-Type", "application/json")
                                .header("User-Agent", "Shikho/(607) 6.0.7 (Android 12; V2029; vivo 2027; en; WIFI; edac7970-6299-4e14-9b98-8c64d3ca4c09)")
                                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                                .build()

                            val refreshRes = refreshClient.newCall(refreshReq).execute()
                            if (refreshRes.isSuccessful) {
                                val resBody = refreshRes.body?.string()
                                if (!resBody.isNullOrBlank()) {
                                    val jsonObj = org.json.JSONObject(resBody)
                                    val tokensObj = jsonObj.optJSONObject("tokens")
                                    val newAccessToken = tokensObj?.optString("access_token")
                                    val newRefreshToken = tokensObj?.optString("refresh_token")
                                    if (!newAccessToken.isNullOrBlank()) {
                                        sessionManager.updateAuthTokens(
                                            accessToken = newAccessToken,
                                            refreshToken = if (!newRefreshToken.isNullOrBlank()) newRefreshToken else refreshToken
                                        )
                                        android.util.Log.i("ShikhoApiService", "✅ Token successfully refreshed automatically!")
                                        return newAccessToken
                                    }
                                }
                            } else {
                                android.util.Log.w("ShikhoApiService", "Token refresh endpoint returned HTTP ${refreshRes.code}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ShikhoApiService", "Token refresh attempt encountered error: ${e.message}")
                        }
                        return null
                    }
                }

                val auth401Interceptor = Interceptor { chain ->
                    val request = chain.request()
                    var response = chain.proceed(request)

                    // NEVER treat 403 Forbidden as session expiry. 403 is permission-denied for a specific course or WAF check.
                    if (response.code == 401) {
                        val activeToken = sessionManager.getAccessToken()
                        val url = request.url.toString()
                        val isAuthEndpoint = url.contains("/check-user") || url.contains("/send-sms") ||
                            url.contains("/verify-otp") || url.contains("/verify-pin") ||
                            url.contains("/login") || url.contains("/token/refresh")

                        if (!isAuthEndpoint && !activeToken.isNullOrBlank()) {
                            // 1. Try automatic token refresh first
                            val newToken = performTokenRefresh()
                            if (!newToken.isNullOrBlank()) {
                                try {
                                    response.close()
                                } catch (_: Exception) {}
                                val retryReq = request.newBuilder()
                                    .header("Authorization", "Bearer $newToken")
                                    .build()
                                return@Interceptor chain.proceed(retryReq)
                            }

                            // 2. Only trigger session expired dialog if this is a primary authenticated GraphQL API endpoint
                            // Never trigger for analytics, FCM, telemetry, or external services
                            val isCoreGraphql = url.contains("api.shikho.com/graphql")
                            if (isCoreGraphql) {
                                android.util.Log.w("ShikhoApiService", "HTTP 401 Unauthorized confirmed on core endpoint $url after refresh failed. Triggering session expired notification.")
                                sessionManager.notifyUnauthorized()
                            } else {
                                android.util.Log.w("ShikhoApiService", "HTTP 401 on non-critical endpoint $url suppressed to prevent session loss.")
                            }
                        }
                    }
                    response
                }

                val dispatcher = okhttp3.Dispatcher().apply {
                    maxRequests = 128
                    maxRequestsPerHost = 64
                }

                val client = OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(okhttp3.ConnectionPool(64, 5, TimeUnit.MINUTES))
                    .addInterceptor(com.example.security.ActivationNetworkInterceptor(sessionManager.appContext))
                    .addInterceptor(headerInterceptor)
                    .addInterceptor(enrolmentMockInterceptor)
                    .addInterceptor(auth401Interceptor)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()

                val moshi = Moshi.Builder()
                    .add(FlexibleIntAdapter())
                    .add(FlexibleLongAdapter())
                    .add(object {
                        @FromJson
                        fun fromJson(reader: JsonReader): String? {
                            return when (reader.peek()) {
                                JsonReader.Token.NULL -> reader.nextNull()
                                JsonReader.Token.STRING,
                                JsonReader.Token.NUMBER -> reader.nextString()
                                JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
                                else -> {
                                    reader.skipValue()
                                    null
                                }
                            }
                        }

                        @ToJson
                        fun toJson(writer: JsonWriter, value: String?) {
                            writer.value(value)
                        }
                    })
                    .addLast(KotlinJsonAdapterFactory())
                    .build()

                val instance = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(ShikhoApiService::class.java)

                cachedInstance = instance
                instance
            }
        }
    }
}

class FlexibleIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.NUMBER -> reader.nextInt()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}

class FlexibleLongAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Long? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.NUMBER -> reader.nextLong()
            JsonReader.Token.STRING -> {
                val str = reader.nextString().trim()
                str.toLongOrNull() ?: str.toDoubleOrNull()?.toLong()
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Long?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
