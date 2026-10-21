package com.example.gamification

import android.content.Context
import androidx.compose.runtime.*
import com.example.database.AppDatabase
import com.example.database.CompletedItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DailyQuestItem(
    val id: String,
    val titleBn: String,
    val subtitleBn: String,
    val xpReward: Int,
    val isCompleted: Boolean,
    val progressTextBn: String
)

data class GamificationStats(
    val totalXp: Int = 120,
    val level: Int = 1,
    val rankTitleBn: String = "নবীন অভিযাত্রী",
    val currentLevelXp: Int = 120,
    val nextLevelXpThreshold: Int = 250,
    val levelProgressFraction: Float = 0.48f,
    val streakDays: Int = 1,
    val completedLessonsCount: Int = 0,
    val watchedAnimatedCount: Int = 0,
    val savedItemsCount: Int = 0,
    val dailyQuests: List<DailyQuestItem> = emptyList()
)

object StudentGamificationManager {
    private const val PREFS_NAME = "maxbird_gamification_prefs"
    private const val KEY_STREAK_DAYS = "streak_days"
    private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
    private const val KEY_WATCHED_ANIMATED_URLS = "watched_animated_urls"
    private const val KEY_TODAY_ANIMATED_DATE = "today_animated_date"
    private const val KEY_TODAY_LESSON_DATE = "today_lesson_date"
    private const val KEY_TODAY_SAVED_DATE = "today_saved_date"
    private const val KEY_BONUS_XP = "bonus_xp"

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        return sdf.format(Date(System.currentTimeMillis() - 86_400_000L))
    }

    fun checkAndUpdateDailyStreak(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_ACTIVE_DATE, null)
        var streak = prefs.getInt(KEY_STREAK_DAYS, 1).coerceAtLeast(1)

        if (lastDate != today) {
            val yesterday = getYesterdayDateString()
            streak = if (lastDate == yesterday) {
                streak + 1
            } else if (lastDate == null) {
                1
            } else {
                1
            }
            val currentBonus = prefs.getInt(KEY_BONUS_XP, 50)
            prefs.edit()
                .putString(KEY_LAST_ACTIVE_DATE, today)
                .putInt(KEY_STREAK_DAYS, streak)
                .putInt(KEY_BONUS_XP, currentBonus + 20)
                .apply()
            _refreshTrigger.value++
        }
        return streak
    }

    fun recordAnimatedVideoWatched(context: Context, videoUrl: String) {
        if (videoUrl.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_WATCHED_ANIMATED_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isNew = existing.add(videoUrl)
        val today = getTodayDateString()
        val editor = prefs.edit()
            .putStringSet(KEY_WATCHED_ANIMATED_URLS, existing)
            .putString(KEY_TODAY_ANIMATED_DATE, today)
        if (isNew) {
            val currentBonus = prefs.getInt(KEY_BONUS_XP, 50)
            editor.putInt(KEY_BONUS_XP, currentBonus + 15)
        }
        editor.apply()
        _refreshTrigger.value++
    }

    fun isAnimatedVideoWatched(context: Context, videoUrl: String): Boolean {
        if (videoUrl.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_WATCHED_ANIMATED_URLS, emptySet()) ?: emptySet()
        return existing.contains(videoUrl)
    }

    fun getWatchedAnimatedUrls(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_WATCHED_ANIMATED_URLS, emptySet()) ?: emptySet()
    }

    fun recordLessonActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TODAY_LESSON_DATE, getTodayDateString()).apply()
        _refreshTrigger.value++
    }

    fun recordSavedOrQuizActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TODAY_SAVED_DATE, getTodayDateString()).apply()
        _refreshTrigger.value++
    }

    fun getRankTitleForLevel(level: Int): String {
        return when {
            level <= 1 -> "নবীন অভিযাত্রী"
            level == 2 -> "জ্ঞান সন্ধানী"
            level == 3 -> "মেধাবী যোদ্ধা"
            level == 4 -> "অধ্যায় বিজেতা"
            level == 5 -> "কুইজ চ্যাম্পিয়ন"
            level in 6..8 -> "এলিট স্কলার"
            else -> "গ্র্যান্ডমাস্টার লিজেন্ড"
        }
    }

    fun computeStats(
        context: Context,
        completedLessonsCount: Int,
        savedItemsCount: Int
    ): GamificationStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val streak = checkAndUpdateDailyStreak(context)
        val watchedAnimatedSet = prefs.getStringSet(KEY_WATCHED_ANIMATED_URLS, emptySet()) ?: emptySet()
        val watchedAnimatedCount = watchedAnimatedSet.size
        val bonusXp = prefs.getInt(KEY_BONUS_XP, 50)
        val today = getTodayDateString()

        val lessonQuestDone = completedLessonsCount > 0 || prefs.getString(KEY_TODAY_LESSON_DATE, "") == today
        val animatedQuestDone = watchedAnimatedCount > 0 || prefs.getString(KEY_TODAY_ANIMATED_DATE, "") == today
        val savedQuestDone = savedItemsCount > 0 || prefs.getString(KEY_TODAY_SAVED_DATE, "") == today

        val questBonusXp = (if (lessonQuestDone) 40 else 0) +
                (if (animatedQuestDone) 30 else 0) +
                (if (savedQuestDone) 30 else 0)

        val totalXp = (completedLessonsCount * 100) +
                (watchedAnimatedCount * 50) +
                (savedItemsCount * 25) +
                (streak * 20) +
                bonusXp +
                questBonusXp

        val xpPerLevel = 250
        val level = (totalXp / xpPerLevel) + 1
        val currentLevelXp = totalXp % xpPerLevel
        val progress = (currentLevelXp.toFloat() / xpPerLevel.toFloat()).coerceIn(0.05f, 1f)

        val quests = listOf(
            DailyQuestItem(
                id = "quest_class",
                titleBn = "ক্লাস মিশন: লেকচার সম্পন্ন করো",
                subtitleBn = "যেকোনো ১টি রেকর্ডেড বা লাইভ ক্লাসে অংশ নাও",
                xpReward = 100,
                isCompleted = lessonQuestDone,
                progressTextBn = if (lessonQuestDone) "সম্পন্ন ✓" else "০/১"
            ),
            DailyQuestItem(
                id = "quest_anim",
                titleBn = "অ্যানিমেশন মিশন: থ্রিডি লেসন দেখো",
                subtitleBn = "অধ্যায়ের যেকোনো ১টি অ্যানিমেটেড ভিডিও দেখো",
                xpReward = 50,
                isCompleted = animatedQuestDone,
                progressTextBn = if (animatedQuestDone) "সম্পন্ন ✓" else "০/১"
            ),
            DailyQuestItem(
                id = "quest_quiz_save",
                titleBn = "স্কলার মিশন: কুইজ বা নোট সেভ করো",
                subtitleBn = "গুরুত্বপূর্ণ প্রশ্ন বা ক্লাস নোট বুকমার্ক করো",
                xpReward = 30,
                isCompleted = savedQuestDone,
                progressTextBn = if (savedQuestDone) "সম্পন্ন ✓" else "০/১"
            )
        )

        return GamificationStats(
            totalXp = totalXp,
            level = level,
            rankTitleBn = getRankTitleForLevel(level),
            currentLevelXp = currentLevelXp,
            nextLevelXpThreshold = xpPerLevel,
            levelProgressFraction = progress,
            streakDays = streak,
            completedLessonsCount = completedLessonsCount,
            watchedAnimatedCount = watchedAnimatedCount,
            savedItemsCount = savedItemsCount,
            dailyQuests = quests
        )
    }
}

@Composable
fun rememberGamificationStats(context: Context): GamificationStats {
    val db = remember(context) { AppDatabase.getInstance(context) }
    val sessionManager = remember(context) { com.example.auth.SessionManager(context) }
    val completedRepo = remember(db, sessionManager) { CompletedItemRepository(db.completedItemDao(), sessionManager) }
    val completedIds by completedRepo.completedIdsState.collectAsState()
    val savedIds by db.savedItemDao().getAllSavedItemIds().collectAsState(initial = emptyList())
    val trigger by StudentGamificationManager.refreshTrigger.collectAsState()

    return remember(completedIds.size, savedIds.size, trigger) {
        StudentGamificationManager.computeStats(
            context = context,
            completedLessonsCount = completedIds.size,
            savedItemsCount = savedIds.size
        )
    }
}

