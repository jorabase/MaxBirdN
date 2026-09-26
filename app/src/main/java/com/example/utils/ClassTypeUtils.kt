package com.example.utils

import androidx.compose.ui.graphics.Color
import com.example.api.StudentLessonItem

data class ClassTypeBadgeStyle(
    val label: String,
    val textColor: Color,
    val backgroundColor: Color
)

object ClassTypeUtils {

    /**
     * Translates a raw class type or tag identifier from the database into Bengali.
     * Maps:
     * - "Doubts" / "Doubt" → "ডাউট ক্লাস"
     * - "Lecture" → "লেকচার ক্লাস"
     * - "Orientation" → "ওরিয়েনটেশন ক্লাস"
     * - "Extra" → "এক্সট্রা ক্লাস"
     * - "Solving" → "সলভিং ক্লাস"
     * - "Concepts" → "কনসেপ্ট ক্লাস"
     * - "Analysis" → "অ্যানালাইসিস ক্লাস"
     */
    fun translateClassType(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("modeltest") || lower.contains("model_test") || lower.contains("model test") -> "মডেল টেস্ট"
            lower.contains("liveexam") || lower.contains("live_exam") || lower.contains("chapter_exam") -> "চ্যাপ্টার এক্সাম"
            lower.contains("doubt") -> "ডাউট  ক্লাস"
            lower.contains("orientation") -> "ওরিয়েনটেশন ক্লাস"
            lower.contains("solving") || lower.contains("solution") -> "সলভিং ক্লাস"
            lower.contains("concept") -> "কনসেপ্ট ক্লাস"
            lower.contains("analysis") || lower.contains("analytic") -> "অ্যানালাইসিস ক্লাস"
            lower.contains("extra") -> "এক্সট্রা ক্লাস"
            lower.contains("lecture") -> "লেকচার ক্লাস"
            lower.contains("practice") -> "অনুশীলন ক্লাস"
            lower.contains("revision") -> "রিভিশন ক্লাস"
            lower.contains("exam") || lower.contains("test") || lower.contains("quiz") -> "চ্যাপ্টার এক্সাম"
            else -> null
        }
    }

    /**
     * Determines the Bengali class type for a given lesson item.
     * Evaluates class_type, live_class type, content_type, and title.
     */
    fun getClassTypeBangla(lesson: StudentLessonItem): String {
        // 0. Highest priority: content_type check
        if (lesson.isModelTest || lesson.content_type.equals("ModelTest", ignoreCase = true)) {
            return "মডেল টেস্ট"
        }
        if (lesson.isLiveExam || lesson.content_type.equals("LiveExam", ignoreCase = true)) {
            return "চ্যাপ্টার এক্সাম"
        }

        // 1. Explicit class_type from root or live_class
        translateClassType(lesson.class_type)?.let { return it }
        translateClassType(lesson.live_class?.class_type)?.let { return it }
        translateClassType(lesson.live_class?.type)?.let { return it }
        translateClassType(lesson.type)?.let { return it }

        // 2. Detect keywords from lesson title
        val titleLower = (lesson.title ?: "").lowercase()
        when {
            titleLower.contains("model test") || titleLower.contains("মডেল টেস্ট") -> return "মডেল টেস্ট"
            titleLower.contains("doubt") -> return "ডাউট  ক্লাস"
            titleLower.contains("orientation") -> return "ওরিয়েনটেশন ক্লাস"
            titleLower.contains("solving") || titleLower.contains("solution") -> return "সলভিং ক্লাস"
            titleLower.contains("concept") -> return "কনসেপ্ট ক্লাস"
            titleLower.contains("analysis") || titleLower.contains("analytic") -> return "অ্যানালাইসিস ক্লাস"
            titleLower.contains("extra") -> return "এক্সট্রা ক্লাস"
            titleLower.contains("lecture") -> return "লেকচার ক্লাস"
            titleLower.contains("practice") -> return "অনুশীলন ক্লাস"
            titleLower.contains("revision") -> return "রিভিশন ক্লাস"
        }

        // 3. Fallback based on lesson characteristics
        return when {
            lesson.isModelTest -> "মডেল টেস্ট"
            lesson.isExam -> "চ্যাপ্টার এক্সাম"
            lesson.isLive -> "লাইভ ক্লাস"
            lesson.isRecorded -> "লেকচার ক্লাস"
            else -> "লেকচার ক্লাস"
        }
    }

    /**
     * Returns a styled badge configuration with attractive colors for UI display.
     */
    fun getClassTypeBadgeStyle(lesson: StudentLessonItem): ClassTypeBadgeStyle {
        val label = getClassTypeBangla(lesson)
        return when (label) {
            "মডেল টেস্ট" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "ডাউট  ক্লাস", "ডাউট ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "লেকচার ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF2563EB),
                backgroundColor = Color(0xFFEFF6FF)
            )
            "ওরিয়েনটেশন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0D9488),
                backgroundColor = Color(0xFFCCFBF1)
            )
            "এক্সট্রা ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFD97706),
                backgroundColor = Color(0xFFFEF3C7)
            )
            "সলভিং ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0284C7),
                backgroundColor = Color(0xFFE0F2FE)
            )
            "কনসেপ্ট ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFDB2777),
                backgroundColor = Color(0xFFFCE7F3)
            )
            "অ্যানালাইসিস ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF4F46E5),
                backgroundColor = Color(0xFFEEF2FF)
            )
            "অনুশীলন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF059669),
                backgroundColor = Color(0xFFD1FAE5)
            )
            "রিভিশন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF9333EA),
                backgroundColor = Color(0xFFF3E8FF)
            )
            "চ্যাপ্টার এক্সাম", "পরীক্ষা" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            "লাইভ ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFDC2626),
                backgroundColor = Color(0xFFFEE2E2)
            )
            else -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF475569),
                backgroundColor = Color(0xFFF1F5F9)
            )
        }
    }

    /**
     * Replaces English class type terms in lesson titles with Bengali translations:
     * - "Doubts" → "ডাউট" / "ডাউট ক্লাস"
     * - "Lecture" → "লেকচার" / "লেকচার ক্লাস"
     * - "Orientation" → "ওরিয়েনটেশন" / "ওরিয়েনটেশন ক্লাস"
     * - "Extra" → "এক্সট্রা" / "এক্সট্রা ক্লাস"
     * - "Solving" → "সলভিং" / "সলভিং ক্লাস"
     * - "Concepts" → "কনসেপ্ট" / "কনসেপ্ট ক্লাস"
     * - "Analysis" → "অ্যানালাইসিস" / "অ্যানালাইসিস ক্লাস"
     * Also converts digits attached to these labels into Bengali digits (e.g. "Lecture 01" -> "লেকচার ০১").
     */
    fun formatLessonTitle(title: String?): String {
        if (title.isNullOrBlank()) return "ক্লাস লেকচার"
        val trimmed = title.trim()

        // Handle exact single word cases
        when (trimmed.lowercase()) {
            "doubts", "doubt" -> return "ডাউট  ক্লাস"
            "lecture", "lectures" -> return "লেকচার ক্লাস"
            "orientation" -> return "ওরিয়েনটেশন ক্লাস"
            "extra" -> return "এক্সট্রা ক্লাস"
            "solving", "solution" -> return "সলভিং ক্লাস"
            "concepts", "concept" -> return "কনসেপ্ট ক্লাস"
            "analysis", "analytics" -> return "অ্যানালাইসিস ক্লাস"
        }

        var formatted = trimmed

        // 1. Two-word phrases first
        formatted = replaceWord(formatted, "Doubts Class", "ডাউট  ক্লাস")
        formatted = replaceWord(formatted, "Doubt Class", "ডাউট  ক্লাস")
        formatted = replaceWord(formatted, "Lecture Class", "লেকচার ক্লাস")
        formatted = replaceWord(formatted, "Orientation Class", "ওরিয়েনটেশন ক্লাস")
        formatted = replaceWord(formatted, "Extra Class", "এক্সট্রা ক্লাস")
        formatted = replaceWord(formatted, "Solving Class", "সলভিং ক্লাস")
        formatted = replaceWord(formatted, "Solution Class", "সলভিং ক্লাস")
        formatted = replaceWord(formatted, "Concepts Class", "কনসেপ্ট ক্লাস")
        formatted = replaceWord(formatted, "Concept Class", "কনসেপ্ট ক্লাস")
        formatted = replaceWord(formatted, "Analysis Class", "অ্যানালাইসিস ক্লাস")
        formatted = replaceWord(formatted, "Practice Class", "অনুশীলন ক্লাস")
        formatted = replaceWord(formatted, "Revision Class", "রিভিশন ক্লাস")
        formatted = replaceWord(formatted, "Live Class", "লাইভ ক্লাস")
        formatted = replaceWord(formatted, "Recorded Class", "রেকর্ডেড ক্লাস")

        // 2. Single words
        formatted = replaceWord(formatted, "Doubts", "ডাউট")
        formatted = replaceWord(formatted, "Doubt", "ডাউট")
        formatted = replaceWord(formatted, "Lectures", "লেকচার")
        formatted = replaceWord(formatted, "Lecture", "লেকচার")
        formatted = replaceWord(formatted, "Orientation", "ওরিয়েনটেশন")
        formatted = replaceWord(formatted, "Extra", "এক্সট্রা")
        formatted = replaceWord(formatted, "Solving", "সলভিং")
        formatted = replaceWord(formatted, "Solution", "সলভিং")
        formatted = replaceWord(formatted, "Concepts", "কনসেপ্ট")
        formatted = replaceWord(formatted, "Concept", "কনসেপ্ট")
        formatted = replaceWord(formatted, "Analysis", "অ্যানালাইসিস")
        formatted = replaceWord(formatted, "Class", "ক্লাস")
        formatted = replaceWord(formatted, "Part", "পর্ব")

        // 3. Convert numbers following class keywords to Bengali digits (e.g. "লেকচার 01" -> "লেকচার ০১")
        formatted = convertTrailingNumbersToBengali(formatted)

        return formatted.trim()
    }

    private fun replaceWord(text: String, target: String, replacement: String): String {
        val regex = Regex("(?i)\\b${Regex.escape(target)}\\b")
        return regex.replace(text, replacement)
    }

    private fun convertTrailingNumbersToBengali(text: String): String {
        val numberRegex = Regex("(?<=(?:লেকচার|ডাউট|ওরিয়েনটেশন|এক্সট্রা|সলভিং|কনসেপ্ট|অ্যানালাইসিস|ক্লাস|পর্ব)[\\s:-]{1,4})\\d+")
        return numberRegex.replace(text) { matchResult ->
            toBengaliDigits(matchResult.value)
        }
    }

    private fun toBengaliDigits(numStr: String): String {
        val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return buildString {
            for (ch in numStr) {
                if (ch in '0'..'9') {
                    append(bengaliDigits[ch - '0'])
                } else {
                    append(ch)
                }
            }
        }
    }
}
