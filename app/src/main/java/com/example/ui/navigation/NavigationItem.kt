package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val description: String
) {
    object Home : NavigationItem(
        route = "tab_home",
        title = "হোম",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        description = "হোম ড্যাশবোর্ড ও ক্লাসরুম"
    )

    object Courses : NavigationItem(
        route = "tab_courses",
        title = "পাঠক্রম",
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories,
        description = "সকল এনরোল্ড ও অফার করা পাঠক্রম"
    )

    object Downloads : NavigationItem(
        route = "tab_downloads",
        title = "ডাউনলোড",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        description = "অফলাইন ভিডিও ও স্টাডি মেটেরিয়াল"
    )

    object Settings : NavigationItem(
        route = "tab_settings",
        title = "সেটিংস",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        description = "প্রোফাইল, নোটিফিকেশন ও অ্যাপ সেটিংস"
    )

    companion object {
        val items = listOf(Home, Courses, Downloads, Settings)
    }
}
