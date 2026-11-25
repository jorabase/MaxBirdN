package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.utils.AppLang

sealed class NavigationItem(
    val route: String,
    val titleEn: String,
    val titleBn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val descriptionEn: String,
    val descriptionBn: String
) {
    val title: String get() = AppLang.tr(titleEn, titleBn)
    val description: String get() = AppLang.tr(descriptionEn, descriptionBn)

    object Home : NavigationItem(
        route = "tab_home",
        titleEn = "Home",
        titleBn = "হোম",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        descriptionEn = "Home dashboard & classroom",
        descriptionBn = "হোম ড্যাশবোর্ড ও ক্লাসরুম"
    )

    object Courses : NavigationItem(
        route = "tab_courses",
        titleEn = "Courses",
        titleBn = "পাঠক্রম",
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories,
        descriptionEn = "Enrolled courses & subjects",
        descriptionBn = "সকল এনরোল্ড ও অফার করা পাঠক্রম"
    )

    object Library : NavigationItem(
        route = "tab_library",
        titleEn = "Library",
        titleBn = "লাইব্রেরি",
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook,
        descriptionEn = "Digital books & offline audio reader",
        descriptionBn = "ডিজিটাল বই, অধ্যায় ও অফলাইন অডিও রিডার"
    )

    object Downloads : NavigationItem(
        route = "tab_downloads",
        titleEn = "Downloads",
        titleBn = "ডাউনলোড",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        descriptionEn = "Offline videos, books & study materials",
        descriptionBn = "অফলাইন ভিডিও, বই ও স্টাডি মেটেরিয়াল"
    )

    object Settings : NavigationItem(
        route = "tab_settings",
        titleEn = "Settings",
        titleBn = "সেটিংস",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        descriptionEn = "Profile, preferences & app settings",
        descriptionBn = "প্রোফাইল, নোটিফিকেশন ও অ্যাপ সেটিংস"
    )

    companion object {
        val items = listOf(Home, Courses, Library, Downloads, Settings)
    }
}
