package com.example.syllabus

import com.example.api.BatchOptionItem
import com.example.api.ClassItem
import com.example.api.StudyGroupItem

/**
 * Loose match: user-এর stored study_group value (যেমন "HUM") আর StudyGroupItem (code="Humanities")।
 * Case-insensitive prefix / substring matching — কোনো hardcoded map নেই।
 *
 * কেন দরকার: JWT meta-তে "HUM" আসে, profile-এ "HUM" আসে, কিন্তু API enum-এ "Humanities"।
 */
fun StudyGroupItem.matchesCodeOrName(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val normalized = raw.trim().lowercase()
    if (normalized.isEmpty()) return false
    return listOfNotNull(code, name_en, title_en, name_bn)
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .any { field ->
            field == normalized ||
                    field.startsWith(normalized) ||
                    normalized.startsWith(field) ||
                    field.contains(normalized)
        }
}

/**
 * Settings screen-এর সাথে consistent বাংলা ordinal class নাম।
 * Unknown code হলে API-র name_bn / title_bn / name_en use করবে — hardcoded fallback ছাড়া।
 */
fun ClassItem.bengaliClassName(): String {
    return when (code.trim().uppercase()) {
        "C5", "C05" -> "পঞ্চম শ্রেণি"
        "C6", "C06" -> "ষষ্ঠ শ্রেণি"
        "C7", "C07" -> "সপ্তম শ্রেণি"
        "C8", "C08" -> "অষ্টম শ্রেণি"
        "C9", "C09" -> "নবম শ্রেণি"
        "C10" -> "দশম শ্রেণি"
        "C11" -> "একাদশ শ্রেণি"
        "C12" -> "দ্বাদশ শ্রেণি"
        else -> name_bn?.takeIf { it.isNotBlank() }
            ?: title_bn?.takeIf { it.isNotBlank() }
            ?: name_en
            ?: code
    }
}

/**
 * API-প্রদত্ত batch options থেকে সবচেয়ে likely "current" batch বেছে নেয়।
 *
 * Priority (কোনো hardcoded year/label ছাড়া, শুধু API data):
 *  1. year == stored passing year (যেমন "2027")
 *  2. label == stored batch label (যেমন "New C11 Batch")
 *  3. label-এ "Running" / "New" আছে কিন্তু "Old" নেই
 *  4. প্রথম option
 */
fun pickDefaultBatch(
    options: List<BatchOptionItem>,
    storedPassingYear: String?,
    storedBatchLabel: String?
): BatchOptionItem? {
    if (options.isEmpty()) return null

    if (!storedPassingYear.isNullOrBlank()) {
        options.find { it.yearString.equals(storedPassingYear.trim(), ignoreCase = true) }
            ?.let { return it }
    }

    if (!storedBatchLabel.isNullOrBlank()) {
        options.find { it.label.equals(storedBatchLabel.trim(), ignoreCase = true) }
            ?.let { return it }
    }

    options.firstOrNull { b ->
        val label = b.label.orEmpty()
        (label.contains("Running", true) || label.contains("New", true)) &&
                !label.contains("Old", true)
    }?.let { return it }

    return options.firstOrNull()
}
