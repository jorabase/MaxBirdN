package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class NavigateToPin(val phone: String) : AuthState()
    data class NavigateToOtp(val phone: String, val authType: String, val nextOtpInSeconds: Int = 120) : AuthState()
    data class NavigateToSetPin(val phone: String, val isSignup: Boolean = false) : AuthState()
    data class PinSetSuccess(val phone: String, val isSignup: Boolean = false) : AuthState()
    object LoginSuccess : AuthState()
    data class ProfileLoaded(val profile: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _otpRemainingSeconds = MutableStateFlow(120)
    val otpRemainingSeconds: StateFlow<Int> = _otpRemainingSeconds.asStateFlow()

    private val _otpTimerResetTrigger = MutableStateFlow(0L)
    val otpTimerResetTrigger: StateFlow<Long> = _otpTimerResetTrigger.asStateFlow()

    private val _hasRateLimitPendingOtp = MutableStateFlow(false)
    val hasRateLimitPendingOtp: StateFlow<Boolean> = _hasRateLimitPendingOtp.asStateFlow()

    private var lastOtpPhone: String = ""
    private var lastOtpAuthType: String = "signup"
    private var lastVerifiedOtp: String = ""

    private fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '০'..'৯' -> sb.append(('0'.code + (ch.code - '০'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatPhone(input: String): String {
        var clean = normalizeDigits(input).filter { it.isDigit() }
        if (clean.startsWith("01")) {
            clean = "88$clean"
        } else if (clean.length == 10 && clean.startsWith("1")) {
            clean = "880$clean"
        }
        return clean
    }

    private fun updateOtpTimer(seconds: Int?) {
        if (seconds != null && seconds >= 0) {
            _otpRemainingSeconds.value = seconds
            _otpTimerResetTrigger.value = System.currentTimeMillis()
        }
    }

    private fun extractServerError(e: HttpException): Pair<String?, Int?> {
        return try {
            val rawBody = e.response()?.errorBody()?.string()
            if (rawBody.isNullOrBlank()) return Pair(null, null)
            val json = org.json.JSONObject(rawBody)
            val errMsg = json.optString("error").ifBlank { json.optString("message") }.takeIf { it.isNotBlank() }
            val nextOtp = if (json.has("next_otp_in_seconds")) {
                json.optInt("next_otp_in_seconds", -1).takeIf { it >= 0 }
            } else {
                null
            }
            Pair(errMsg, nextOtp)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    fun checkUser(phoneInput: String) {
        val phone = formatPhone(phoneInput)
        if (phone.length != 13 || !phone.startsWith("8801")) {
            _authState.value = AuthState.Error("সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 017...)")
            return
        }

        _hasRateLimitPendingOtp.value = false
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = apiService.checkUser(UserCheckRequest(phone = phone, type = "student", vendor = "shikho"))
                when {
                    response.code == 200 && response.pin_exist == true -> {
                        _authState.value = AuthState.NavigateToPin(phone)
                    }
                    response.code == 200 && response.pin_exist != true -> {
                        // Existing user without PIN -> auth_type = "login"
                        sendSmsInternal(phone, "login")
                    }
                    response.code == 404 || response.message?.contains("not found", ignoreCase = true) == true -> {
                        // New user -> auth_type = "signup"
                        sendSmsInternal(phone, "signup")
                    }
                    else -> {
                        sendSmsInternal(phone, "signup")
                    }
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // Profile not found -> New user signup flow
                    sendSmsInternal(phone, "signup")
                } else {
                    val (serverMsg, _) = extractServerError(e)
                    _authState.value = AuthState.Error(serverMsg ?: "API Error: ${e.code()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "নেটওয়ার্ক সমস্যা হয়েছে, আবার চেষ্টা করুন।")
            }
        }
    }

    fun proceedToOtpDuringCooldown(phoneInput: String) {
        val phone = formatPhone(phoneInput).ifBlank { lastOtpPhone }
        if (phone.length == 13 && phone.startsWith("8801")) {
            val authType = if (lastOtpPhone == phone) lastOtpAuthType else "signup"
            _hasRateLimitPendingOtp.value = false
            _authState.value = AuthState.NavigateToOtp(phone, authType, _otpRemainingSeconds.value)
        }
    }

    fun triggerForgotPassword(phone: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            sendSmsInternal(phone, "login")
        }
    }

    fun triggerResendOtp(phone: String, authType: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val adsId = sessionManager.getGoogleAdsId()
                val req = SendSmsRequest(
                    phone = phone,
                    type = "student",
                    auth_type = authType,
                    vendor = "shikho",
                    google_ads_id = adsId.takeIf { it.isNotBlank() }
                )
                val response = apiService.sendSms(req)
                if (response.code == 200 || response.code == 201) {
                    updateOtpTimer(response.next_otp_in_seconds ?: 120)
                    _authState.value = AuthState.Idle
                } else {
                    updateOtpTimer(response.next_otp_in_seconds)
                    _authState.value = AuthState.Error(response.error ?: response.message ?: "ওটিপি পাঠানো যায়নি। একটু পর আবার চেষ্টা করুন।")
                }
            } catch (e: HttpException) {
                val (serverMsg, nextSec) = extractServerError(e)
                updateOtpTimer(nextSec)
                _authState.value = AuthState.Error(serverMsg ?: "ওটিপি পাঠানো যায়নি (HTTP ${e.code()})")
            } catch (e: Exception) {
                _authState.value = AuthState.Error("ওটিপি পাঠানো যায়নি: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun sendSmsInternal(phone: String, authType: String) {
        lastOtpPhone = phone
        lastOtpAuthType = authType
        try {
            val adsId = sessionManager.getGoogleAdsId()
            val req = SendSmsRequest(
                phone = phone,
                type = "student",
                auth_type = authType,
                vendor = "shikho",
                google_ads_id = adsId.takeIf { it.isNotBlank() }
            )
            val response = apiService.sendSms(req)
            if (response.code == 200 || response.code == 201) {
                val waitSec = response.next_otp_in_seconds ?: 120
                updateOtpTimer(waitSec)
                _hasRateLimitPendingOtp.value = false
                _authState.value = AuthState.NavigateToOtp(phone, authType, waitSec)
            } else {
                updateOtpTimer(response.next_otp_in_seconds)
                if (response.code == 429 || (response.next_otp_in_seconds ?: 0) > 0) {
                    _hasRateLimitPendingOtp.value = true
                }
                _authState.value = AuthState.Error(response.error ?: response.message ?: "ওটিপি পাঠানো যায়নি। একটু পর আবার চেষ্টা করুন।")
            }
        } catch (e: HttpException) {
            val (serverMsg, nextSec) = extractServerError(e)
            updateOtpTimer(nextSec)
            if (e.code() == 429 || (nextSec ?: 0) > 0) {
                _hasRateLimitPendingOtp.value = true
            }
            _authState.value = AuthState.Error(serverMsg ?: "ওটিপি পাঠানো যায়নি (HTTP ${e.code()})")
        } catch (e: Exception) {
            _authState.value = AuthState.Error("ওটিপি পাঠানো যায়নি: ${e.localizedMessage}")
        }
    }

    fun submitOtp(phone: String, otpInput: String, authType: String) {
        val otp = normalizeDigits(otpInput).filter { it.isDigit() }
        if (otp.length != 4) {
            _authState.value = AuthState.Error("৪ সংখ্যার সঠিক OTP দিন")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val verifyRes = apiService.verifyOtp(VerifyOtpRequest(phone = phone, otp = otp, type = "student"))
                if (verifyRes.code == 200 || verifyRes.code == 201) {
                    lastVerifiedOtp = otp
                    val isSignup = authType.equals("signup", ignoreCase = true)
                    val deviceId = sessionManager.getFcmToken() ?: sessionManager.getDeviceId()
                    val adsId = sessionManager.getGoogleAdsId()

                    var obtainedTokens: LoginTokens? = null

                    if (isSignup) {
                        // Create the new account on Shikho immediately upon OTP verification (matching shikho.com easySellingSignUpApiCall)
                        try {
                            val signupReq = SignupRequest(
                                type = "student",
                                auth_type = "signup",
                                phone = phone,
                                otp = otp,
                                source = "website",
                                profile = SignupProfilePayload(
                                    first_name = "",
                                    `class` = SignupClassCode("C11"),
                                    gender = "",
                                    avatar = "",
                                    study_group = "HUM",
                                    type = "student"
                                ),
                                vendor = "shikho"
                            )
                            val signupRes = apiService.signup(signupReq)
                            obtainedTokens = signupRes.tokens
                        } catch (signupEx: Exception) {
                            android.util.Log.w("AuthViewModel", "Signup call after OTP verify failed, attempting login fallback: ${signupEx.message}")
                        }

                        // Fallback: if user already existed without PIN, try login with OTP
                        if (obtainedTokens == null) {
                            try {
                                val loginRes = apiService.login(
                                    LoginRequest(
                                        phone = phone,
                                        otp = otp,
                                        profile = ProfileDevice(device_id = deviceId),
                                        google_ads_id = adsId
                                    )
                                )
                                obtainedTokens = loginRes.tokens
                            } catch (loginEx: Exception) {
                                android.util.Log.w("AuthViewModel", "Login fallback after signup also failed: ${loginEx.message}")
                            }
                        }
                    } else {
                        // Existing user (e.g. Forgot Password / Reset PIN or existing user without PIN)
                        try {
                            val loginRes = apiService.login(
                                LoginRequest(
                                    phone = phone,
                                    otp = otp,
                                    profile = ProfileDevice(device_id = deviceId),
                                    google_ads_id = adsId
                                )
                            )
                            obtainedTokens = loginRes.tokens
                        } catch (loginEx: Exception) {
                            android.util.Log.w("AuthViewModel", "Login with OTP failed, attempting signup fallback: ${loginEx.message}")
                        }

                        if (obtainedTokens == null) {
                            try {
                                val signupRes = apiService.signup(
                                    SignupRequest(
                                        type = "student",
                                        auth_type = "signup",
                                        phone = phone,
                                        otp = otp,
                                        source = "website",
                                        profile = SignupProfilePayload(),
                                        vendor = "shikho"
                                    )
                                )
                                obtainedTokens = signupRes.tokens
                            } catch (_: Exception) {}
                        }
                    }

                    if (obtainedTokens != null) {
                        sessionManager.saveTokens(
                            accessToken = obtainedTokens.access_token,
                            refreshToken = obtainedTokens.refresh_token,
                            userId = obtainedTokens.user_id
                        )
                    }
                    sessionManager.saveUserPhone(phone)

                    if (isSignup) {
                        sessionManager.setJustSignedUp(true)
                    }
                    // Route to SetPinScreen for both Signup & Forgot Password Reset
                    _authState.value = AuthState.NavigateToSetPin(phone, isSignup = isSignup)
                } else {
                    _authState.value = AuthState.Error(verifyRes.error ?: verifyRes.message ?: "সঠিক OTP প্রদান করুন")
                }
            } catch (e: HttpException) {
                val (serverMsg, _) = extractServerError(e)
                _authState.value = AuthState.Error(serverMsg ?: "OTP ভেরিফিকেশন ব্যর্থ হয়েছে। সঠিক OTP দিন।")
            } catch (e: Exception) {
                _authState.value = AuthState.Error("OTP ভেরিফিকেশন ব্যর্থ হয়েছে। আবার চেষ্টা করুন।")
            }
        }
    }

    fun setPin(phone: String, pinInput: String, isSignup: Boolean = false) {
        val pin = normalizeDigits(pinInput).filter { it.isDigit() }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // If access token wasn't obtained during submitOtp, create account with password = pin via /auth/v2/signup
                if (sessionManager.getAccessToken().isNullOrBlank() && lastVerifiedOtp.isNotBlank()) {
                    try {
                        val signupRes = apiService.signup(
                            SignupRequest(
                                type = "student",
                                auth_type = "signup",
                                phone = phone,
                                otp = lastVerifiedOtp,
                                source = "website",
                                profile = SignupProfilePayload(
                                    first_name = "শিক্ষার্থী",
                                    `class` = SignupClassCode("C11"),
                                    gender = "M",
                                    avatar = "",
                                    study_group = "HUM",
                                    type = "student"
                                ),
                                vendor = "shikho",
                                password = pin
                            )
                        )
                        signupRes.tokens?.let { tokens ->
                            sessionManager.saveTokens(
                                accessToken = tokens.access_token,
                                refreshToken = tokens.refresh_token,
                                userId = tokens.user_id
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AuthViewModel", "Deferred signup with PIN failed: ${e.message}")
                    }
                }

                val queryBody = GraphQlQuery(
                    operationName = "SetPin",
                    query = "mutation SetPin(\$pin: String!) { setUserPin(pin:\$pin) { message } }",
                    variables = mapOf("pin" to pin)
                )

                val response = apiService.setPin(queryBody)
                val msg = response.data?.setUserPin?.message
                if ((msg != null && msg.contains("successfully", ignoreCase = true)) || !sessionManager.getAccessToken().isNullOrBlank()) {
                    val wasJustSignedUp = isSignup || sessionManager.getJustSignedUp()
                    // Auto-logout & Clear session so user logs in cleanly with their new PIN
                    try {
                        apiService.logout()
                    } catch (_: Exception) { }
                    sessionManager.clearSession()
                    if (wasJustSignedUp) {
                        sessionManager.setJustSignedUp(true)
                    }

                    _authState.value = AuthState.PinSetSuccess(phone, isSignup = wasJustSignedUp)
                } else {
                    _authState.value = AuthState.Error(msg ?: "পিন সেট করা যায়নি। আবার চেষ্টা করুন।")
                }
            } catch (e: HttpException) {
                val (serverMsg, _) = extractServerError(e)
                _authState.value = AuthState.Error(serverMsg ?: "পিন সেট করা যায়নি (HTTP ${e.code()})")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "পিন সেট করা যায়নি")
            }
        }
    }

    fun submitPin(phone: String, pinInput: String) {
        val pin = normalizeDigits(pinInput).filter { it.isDigit() }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val deviceId = sessionManager.getFcmToken() ?: sessionManager.getDeviceId()
                val adsId = sessionManager.getGoogleAdsId()

                val request = LoginRequest(
                    phone = phone,
                    otp = pin,
                    profile = ProfileDevice(device_id = deviceId),
                    google_ads_id = adsId
                )
                android.util.Log.d("AuthViewModel", "Logging in with PIN, device_id (FCM token): $deviceId")

                val response = apiService.login(request)
                val tokens = response.tokens
                if (tokens != null) {
                    sessionManager.saveTokens(
                        accessToken = tokens.access_token,
                        refreshToken = tokens.refresh_token,
                        userId = tokens.user_id
                    )
                    sessionManager.saveUserPhone(phone)

                    _authState.value = AuthState.LoginSuccess
                    try {
                        com.example.notification.ShikhoNotificationManager.syncAllTopicSubscriptions(com.example.ShikhoApp.instance)
                    } catch (_: Throwable) {}
                    fetchProfile()
                } else {
                    _authState.value = AuthState.Error(response.error ?: response.message ?: "ভুল পিন দিয়েছেন, আবার চেষ্টা করুন।")
                }
            } catch (e: HttpException) {
                val (serverMsg, _) = extractServerError(e)
                _authState.value = AuthState.Error(serverMsg ?: "ভুল পিন দিয়েছেন, আবার চেষ্টা করুন।")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "লগইন ব্যর্থ হয়েছে")
            }
        }
    }

    fun fetchProfile() {
        val accessToken = sessionManager.getAccessToken()
        val userId = sessionManager.getUserId()
        
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
            _authState.value = AuthState.Error("No active session found. Please login again.")
            return
        }

        viewModelScope.launch {
            try {
                val queryBody = GraphQlQuery(
                    operationName = "GetProfile",
                    query = "query GetProfile(\$user_id: String,\$type: String!) { profile(user_id: \$user_id, type:\$type) { id first_name last_name avatar gender dob study_group passing_year class { code display } school { id name } user { phone email } } }",
                    variables = mapOf(
                        "user_id" to userId,
                        "type" to "student"
                    )
                )
                
                val response = apiService.getProfile(queryBody)
                val profile = response.data?.profile
                if (profile != null) {
                    val firstName = profile.first_name?.trim() ?: ""
                    val lastName = profile.last_name?.trim() ?: ""
                    val fullName = when {
                        firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                        firstName.isNotBlank() -> firstName
                        lastName.isNotBlank() -> lastName
                        else -> ""
                    }
                    val first = when {
                        firstName.isNotBlank() -> firstName
                        fullName.isNotBlank() -> fullName.split(" ").firstOrNull() ?: fullName
                        else -> "শিক্ষার্থী"
                    }

                    sessionManager.saveUserProfile(
                        firstName = firstName.ifBlank { first },
                        lastName = lastName,
                        avatar = profile.avatar,
                        schoolName = profile.school?.name,
                        classDisplay = profile.`class`?.display ?: profile.`class`?.code,
                        phone = profile.user?.phone
                    )
                    sessionManager.saveUserAcademicInfo(
                        batchId = profile.passing_year ?: sessionManager.getUserBatchId(),
                        className = profile.`class`?.code ?: "C11",
                        group = profile.study_group ?: "Humanities",
                        vendor = "BD"
                    )

                    _authState.value = AuthState.ProfileLoaded(profile)
                } else {
                    _authState.value = AuthState.Error("Failed to parse profile data.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to fetch profile")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        try {
            com.example.course.CourseCommerceRepository.clearCache(sessionManager.appContext)
        } catch (_: Throwable) {}
        sessionManager.clearSession()
        _authState.value = AuthState.Idle
        viewModelScope.launch {
            try {
                com.example.notification.ShikhoNotificationManager.unsubscribeAll(com.example.ShikhoApp.instance)
            } catch (_: Throwable) {}
            try {
                apiService.logout()
            } catch (_: Exception) { }
        }
    }
}

class AuthViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
