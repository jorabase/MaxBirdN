package com.example.notification

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Dispatches deep links originating from push notifications to AppNavigation.
 */
object NotificationDeepLinkDispatcher {
    private const val TAG = "DeepLinkDispatcher"

    private val _deepLinkEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val deepLinkEvents: SharedFlow<String> = _deepLinkEvents.asSharedFlow()

    @Volatile
    private var pendingDeepLink: String? = null

    fun setPendingDeepLink(deeplink: String) {
        if (deeplink.isBlank()) return
        Log.d(TAG, "Queued deep link: $deeplink")
        pendingDeepLink = deeplink
        _deepLinkEvents.tryEmit(deeplink)
    }

    fun consumePendingDeepLink(): String? {
        val link = pendingDeepLink
        pendingDeepLink = null
        return link
    }
}
