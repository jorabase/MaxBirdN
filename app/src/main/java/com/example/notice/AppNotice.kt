package com.example.notice

/**
 * Data model for dynamic in-app announcements & banners published by Admin.
 */
data class AppNotice(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val imageUrl: String = "",
    val actionUrl: String? = null,
    val actionButtonText: String = "বিস্তারিত দেখুন",
    val priority: Int = 0,
    val isActive: Boolean = true,
    val showAsPopup: Boolean = true,
    val createdAt: String? = null
)
