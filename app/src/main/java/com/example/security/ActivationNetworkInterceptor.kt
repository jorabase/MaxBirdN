package com.example.security

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * ActivationNetworkInterceptor
 * 
 * Defense-in-depth network gate.
 * Intercepts all outbound application API calls (GraphQL, lessons, routine, user data).
 * If a smali modder uses MT Manager to bypass UI screens, this interceptor blocks
 * all network requests with HTTP 403, rendering the modded APK completely blank and unusable.
 */
class ActivationNetworkInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // Allow Supabase RPC activation requests and health endpoints
        val (supabaseUrl, _) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isNotBlank() && url.contains(supabaseUrl)) {
            return chain.proceed(request)
        }

        // If Supabase backend is configured, enforce strict token verification
        if (DeviceActivationRepository.isConfigured()) {
            val isActivated = DeviceActivationRepository.isDeviceActivated(context)
            if (!isActivated) {
                android.util.Log.e("ActivationInterceptor", "🚨 Blocked unauthorized request to $url (Device not activated)")
                return Response.Builder()
                    .code(403)
                    .protocol(Protocol.HTTP_1_1)
                    .message("Device Access Restricted - Unverified Client")
                    .request(request)
                    .body(
                        """{"errors":[{"message":"Forbidden: Device activation required to access this resource."}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
        }

        // Attach device integrity token header to legit outbound requests
        val rawToken = DeviceActivationRepository.getRawSessionToken(context)
        val modifiedRequest = if (!rawToken.isNullOrBlank()) {
            request.newBuilder()
                .header("X-Device-Hardware-Hash", DeviceSecurityManager.getDeviceHardwareHash(context).take(16))
                .header("X-Device-Session-Token", rawToken)
                .build()
        } else {
            request
        }

        return chain.proceed(modifiedRequest)
    }
}
