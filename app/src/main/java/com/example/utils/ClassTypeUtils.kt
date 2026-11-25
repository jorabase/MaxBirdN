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
            lower.contains("modeltest") || lower.contains("model_test") || lower.contains("model test") -> AppLang.tr("Model Test", "মডেল টেস্ট")
            lower.contains("liveexam") || lower.contains("live_exam") || lower.contains("chapter_exam") -> AppLang.tr("Chapter Exam", "চ্যাপ্টার এক্সাম")
            lower.contains("doubt") -> AppLang.tr("Doubt Class", "ডাউট ক্লাস")
            lower.contains("orientation") -> AppLang.tr("Orientation", "ওরিয়েনটেশন ক্লাস")
            lower.contains("solving") || lower.contains("solution") -> AppLang.tr("Solving Class", "সলভিং ক্লাস")
            lower.contains("concept") -> AppLang.tr("Concept Class", "কনসেপ্ট ক্লাস")
            lower.contains("analysis") || lower.contains("analytic") -> AppLang.tr("Analysis Class", "অ্যানালাইসিস ক্লাস")
            lower.contains("extra") -> AppLang.tr("Extra Class", "এক্সট্রা ক্লাস")
            lower.contains("lecture") -> AppLang.tr("Lecture", "লেকচার ক্লাস")
            lower.contains("practice") -> AppLang.tr("Practice Class", "অনুশীলন ক্লাস")
            lower.contains("revision") -> AppLang.tr("Revision Class", "রিভিশন ক্লাস")
            lower.contains("exam") || lower.contains("test") || lower.contains("quiz") -> AppLang.tr("Chapter Exam", "চ্যাপ্টার এক্সাম")
            else -> null
        }
    }

    fun getClassTypeBangla(lesson: StudentLessonItem): String {
        if (lesson.isModelTest || lesson.content_type.equals("ModelTest", ignoreCase = true)) {
            return AppLang.tr("Model Test", "মডেল টেস্ট")
        }
        if (lesson.isLiveExam || lesson.content_type.equals("LiveExam", ignoreCase = true)) {
            return AppLang.tr("Chapter Exam", "চ্যাপ্টার এক্সাম")
        }

        translateClassType(lesson.class_type)?.let { return it }
        translateClassType(lesson.live_class?.class_type)?.let { return it }
        translateClassType(lesson.live_class?.type)?.let { return it }
        translateClassType(lesson.type)?.let { return it }

        val titleLower = (lesson.title ?: "").lowercase()
        when {
            titleLower.contains("model test") || titleLower.contains("মডেল টেস্ট") -> return AppLang.tr("Model Test", "মডেল টেস্ট")
            titleLower.contains("doubt") -> return AppLang.tr("Doubt Class", "ডাউট ক্লাস")
            titleLower.contains("orientation") -> return AppLang.tr("Orientation", "ওরিয়েনটেশন ক্লাস")
            titleLower.contains("solving") || titleLower.contains("solution") -> return AppLang.tr("Solving Class", "সলভিং ক্লাস")
            titleLower.contains("concept") -> return AppLang.tr("Concept Class", "কনসেপ্ট ক্লাস")
            titleLower.contains("analysis") || titleLower.contains("analytic") -> return AppLang.tr("Analysis Class", "অ্যানালাইসিস ক্লাস")
            titleLower.contains("extra") -> return AppLang.tr("Extra Class", "এক্সট্রা ক্লাস")
            titleLower.contains("lecture") -> return AppLang.tr("Lecture", "লেকচার ক্লাস")
            titleLower.contains("practice") -> return AppLang.tr("Practice Class", "অনুশীলন ক্লাস")
            titleLower.contains("revision") -> return AppLang.tr("Revision Class", "রিভিশন ক্লাস")
        }

        return when {
            lesson.isModelTest -> AppLang.tr("Model Test", "মডেল টেস্ট")
            lesson.isExam -> AppLang.tr("Chapter Exam", "চ্যাপ্টার এক্সাম")
            lesson.isLive -> AppLang.tr("Live Class", "লাইভ ক্লাস")
            lesson.isRecorded -> AppLang.tr("Lecture", "লেকচার ক্লাস")
            else -> AppLang.tr("Lecture", "লেকচার ক্লাস")
        }
    }

    fun getClassTypeBadgeStyle(lesson: StudentLessonItem): ClassTypeBadgeStyle {
        val label = getClassTypeBangla(lesson)
        return when (label) {
            "Model Test", "মডেল টেস্ট" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "Doubt Class", "ডাউট  ক্লাস", "ডাউট ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "Lecture", "লেকচার ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF2563EB),
                backgroundColor = Color(0xFFEFF6FF)
            )
            "Orientation", "ওরিয়েনটেশন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0D9488),
                backgroundColor = Color(0xFFCCFBF1)
            )
            "Extra Class", "এক্সট্রা ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFD97706),
                backgroundColor = Color(0xFFFEF3C7)
            )
            "Solving Class", "সলভিং ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0284C7),
                backgroundColor = Color(0xFFE0F2FE)
            )
            "Concept Class", "কনসেপ্ট ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFDB2777),
                backgroundColor = Color(0xFFFCE7F3)
            )
            "Analysis Class", "অ্যানালাইসিস ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF4F46E5),
                backgroundColor = Color(0xFFEEF2FF)
            )
            "Practice Class", "অনুশীলন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF059669),
                backgroundColor = Color(0xFFD1FAE5)
            )
            "Revision Class", "রিভিশন ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF9333EA),
                backgroundColor = Color(0xFFF3E8FF)
            )
            "Chapter Exam", "চ্যাপ্টার এক্সাম", "পরীক্ষা" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            "Live Class", "লাইভ ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFDC2626),
                backgroundColor = Color(0xFFFEE2E2)
            )
            "Upcoming Class", "আপকামিং ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0284C7),
                backgroundColor = Color(0xFFE0F2FE)
            )
            "Recorded Class", "রেকর্ড ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF2563EB),
                backgroundColor = Color(0xFFEFF6FF)
            )
            "Live Exam", "লাইভ এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            "Upcoming Exam", "আপকামিং এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "Recorded Exam", "রেকর্ড এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFD97706),
                backgroundColor = Color(0xFFFEF3C7)
            )
            else -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF475569),
                backgroundColor = Color(0xFFF1F5F9)
            )
        }
    }

    fun getClassPositionBadgeStyle(lesson: StudentLessonItem): ClassTypeBadgeStyle {
        val rawLabel = lesson.classPositionLabel
        val label = when (rawLabel) {
            "লাইভ ক্লাস" -> AppLang.tr("Live Class", "লাইভ ক্লাস")
            "আপকামিং ক্লাস" -> AppLang.tr("Upcoming", "আপকামিং ক্লাস")
            "রেকর্ড ক্লাস" -> AppLang.tr("Recorded", "রেকর্ড ক্লাস")
            "লাইভ এক্সাম" -> AppLang.tr("Live Exam", "লাইভ এক্সাম")
            "আপকামিং এক্সাম" -> AppLang.tr("Upcoming Exam", "আপকামিং এক্সাম")
            "রেকর্ড এক্সাম" -> AppLang.tr("Recorded Exam", "রেকর্ড এক্সাম")
            else -> rawLabel
        }
        return when (rawLabel) {
            "লাইভ ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFDC2626),
                backgroundColor = Color(0xFFFEE2E2)
            )
            "আপকামিং ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF0284C7),
                backgroundColor = Color(0xFFE0F2FE)
            )
            "রেকর্ড ক্লাস" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF2563EB),
                backgroundColor = Color(0xFFEFF6FF)
            )
            "লাইভ এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFEA580C),
                backgroundColor = Color(0xFFFFEDD5)
            )
            "আপকামিং এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF7C3AED),
                backgroundColor = Color(0xFFEDE9FE)
            )
            "রেকর্ড এক্সাম" -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFFD97706),
                backgroundColor = Color(0xFFFEF3C7)
            )
            else -> ClassTypeBadgeStyle(
                label = label,
                textColor = Color(0xFF475569),
                backgroundColor = Color(0xFFF1F5F9)
            )
        }
    }

    fun formatLessonTitle(title: String?): String {
        if (title.isNullOrBlank()) return AppLang.tr("Class Lecture", "ক্লাস লেকচার")
        val trimmed = title.trim()
        if (!AppLang.isBn()) {
            return trimmed
        }

        when (trimmed.lowercase()) {
            "doubts", "doubt" -> return "ডাউট ক্লাস"
            "lecture", "lectures" -> return "লেকচার ক্লাস"
            "orientation" -> return "ওরিয়েনটেশন ক্লাস"
            "extra" -> return "এক্সট্রা ক্লাস"
            "solving", "solution" -> return "সলভিং ক্লাস"
            "concepts", "concept" -> return "কনসেপ্ট ক্লাস"
            "analysis", "analytics" -> return "অ্যানালাইসিস ক্লাস"
        }

        var formatted = trimmed

        formatted = replaceWord(formatted, "Doubts Class", "ডাউট ক্লাস")
        formatted = replaceWord(formatted, "Doubt Class", "ডাউট ক্লাস")
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

    private fun toBengaliDigits(numStr: String): String = com.example.utils.toBengaliDigits(numStr)
}
