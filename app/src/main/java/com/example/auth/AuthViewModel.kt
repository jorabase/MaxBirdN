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
    data class NavigateToOtp(val phone: String, val authType: String) : AuthState()
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

    fun formatPhone(input: String): String {
        var clean = input.filter { it.isDigit() }
        if (clean.startsWith("01")) {
            clean = "88$clean"
        }
        return clean
    }

    fun checkUser(phoneInput: String) {
        val phone = formatPhone(phoneInput)
        if (phone.length != 13 || !phone.startsWith("8801")) {
            _authState.value = AuthState.Error("Please enter a valid phone number (e.g., 017...)")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = apiService.checkUser(UserCheckRequest(phone = phone))
                if (response.code == 200 && response.pin_exist == true) {
                    _authState.value = AuthState.NavigateToPin(phone)
                } else {
                    sendSmsInternal(phone, "signup")
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // Profile not found -> New user signup flow
                    sendSmsInternal(phone, "signup")
                } else {
                    _authState.value = AuthState.Error("API Error: ${e.code()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Network error occurred")
            }
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
                val req = SendSmsRequest(phone = phone, auth_type = authType, google_ads_id = adsId)
                val response = apiService.sendSms(req)
                if (response.code == 200) {
                    _authState.value = AuthState.Idle 
                } else {
                    _authState.value = AuthState.Error(response.message ?: response.error ?: "Failed to resend OTP")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to resend OTP: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun sendSmsInternal(phone: String, authType: String) {
        try {
            val adsId = sessionManager.getGoogleAdsId()
            val req = SendSmsRequest(phone = phone, auth_type = authType, google_ads_id = adsId)
            val response = apiService.sendSms(req)
            if (response.code == 200) {
                _authState.value = AuthState.NavigateToOtp(phone, authType)
            } else {
                _authState.value = AuthState.Error(response.message ?: response.error ?: "Failed to send SMS")
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to send SMS: ${e.localizedMessage}")
        }
    }

    fun submitOtp(phone: String, otp: String, authType: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val verifyRes = apiService.verifyOtp(VerifyOtpRequest(phone = phone, otp = otp))
                if (verifyRes.code == 200 || verifyRes.code == 201) {
                    // Login to receive temporary access token
                    val deviceId = sessionManager.getFcmToken() ?: sessionManager.getDeviceId()
                    val adsId = sessionManager.getGoogleAdsId()
                    val loginReq = LoginRequest(
                        phone = phone,
                        otp = otp,
                        profile = ProfileDevice(device_id = deviceId),
                        google_ads_id = adsId
                    )
                    android.util.Log.d("AuthViewModel", "Logging in with device_id (FCM token): $deviceId")
                    val loginRes = apiService.login(loginReq)
                    sessionManager.saveTokens(
                        accessToken = loginRes.tokens.access_token,
                        refreshToken = loginRes.tokens.refresh_token,
                        userId = loginRes.tokens.user_id
                    )
                    
                    val isSignup = authType.equals("signup", ignoreCase = true)
                    if (isSignup) {
                        sessionManager.setJustSignedUp(true)
                    }
                    // Route to SetPinScreen for both Signup & Forgot Password Reset
                    _authState.value = AuthState.NavigateToSetPin(phone, isSignup = isSignup)
                } else {
                    _authState.value = AuthState.Error(verifyRes.message ?: "Invalid OTP")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Invalid OTP. Verification failed.")
            }
        }
    }

    fun setPin(phone: String, pin: String, isSignup: Boolean = false) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val queryBody = GraphQlQuery(
                    operationName = "SetPin",
                    query = "mutation SetPin(\$pin: String!) { setUserPin(pin:\$pin) { message } }",
                    variables = mapOf("pin" to pin)
                )
                
                val response = apiService.setPin(queryBody)
                val msg = response.data?.setUserPin?.message
                if (msg != null && msg.contains("successfully", ignoreCase = true)) {
                    val wasJustSignedUp = isSignup || sessionManager.getJustSignedUp()
                    // Auto-logout & Clear session
                    try {
                        apiService.logout()
                    } catch (_: Exception) { }
                    sessionManager.clearSession()
                    if (wasJustSignedUp) {
                        sessionManager.setJustSignedUp(true)
                    }
                    
                    _authState.value = AuthState.PinSetSuccess(phone, isSignup = wasJustSignedUp)
                } else {
                    _authState.value = AuthState.Error(msg ?: "Failed to set PIN. Please try again.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to set PIN")
            }
        }
    }

    fun submitPin(phone: String, pin: String) {
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
                
                sessionManager.saveTokens(
                    accessToken = response.tokens.access_token,
                    refreshToken = response.tokens.refresh_token,
                    userId = response.tokens.user_id
                )
                
                _authState.value = AuthState.LoginSuccess
                try {
                    com.example.notification.ShikhoNotificationManager.syncAllTopicSubscriptions(com.example.ShikhoApp.instance)
                } catch (_: Throwable) {}
                fetchProfile()
            } catch (e: HttpException) {
                _authState.value = AuthState.Error("Incorrect PIN or OTP.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to login")
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
        viewModelScope.launch {
            try {
                com.example.notification.ShikhoNotificationManager.unsubscribeAll(com.example.ShikhoApp.instance)
            } catch (_: Throwable) {}
            try {
                apiService.logout()
            } catch (_: Exception) { }
            sessionManager.clearSession()
            _authState.value = AuthState.Idle
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
