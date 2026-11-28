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
    val isBn = com.example.utils.AppLang.isBn()
    return when (code.trim().uppercase()) {
        "C5", "C05" -> com.example.utils.AppLang.tr("Class 5", "পঞ্চম শ্রেণি")
        "C6", "C06" -> com.example.utils.AppLang.tr("Class 6", "ষষ্ঠ শ্রেণি")
        "C7", "C07" -> com.example.utils.AppLang.tr("Class 7", "সপ্তম শ্রেণি")
        "C8", "C08" -> com.example.utils.AppLang.tr("Class 8", "অষ্টম শ্রেণি")
        "C9", "C09" -> com.example.utils.AppLang.tr("Class 9", "নবম শ্রেণি")
        "C10" -> com.example.utils.AppLang.tr("Class 10", "দশম শ্রেণি")
        "C11" -> com.example.utils.AppLang.tr("Class 11", "একাদশ শ্রেণি")
        "C12" -> com.example.utils.AppLang.tr("Class 12", "দ্বাদশ শ্রেণি")
        else -> if (isBn) {
            name_bn?.takeIf { it.isNotBlank() }
                ?: title_bn?.takeIf { it.isNotBlank() }
                ?: name_en
                ?: code
        } else {
            name_en?.takeIf { it.isNotBlank() }
                ?: name_bn?.takeIf { it.isNotBlank() }
                ?: title_bn?.takeIf { it.isNotBlank() }
                ?: code
        }
    }
}

/**
 * Extracts a 4-digit exam/passing year (e.g. "2028") from raw strings like "2028", "HSC 2028", "SSC 2026".
 */
fun extractPassingYear(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("\\b(20\\d{2})\\b").find(raw)
    return match?.groupValues?.getOrNull(1)
}

/**
 * Dynamically formats the canonical syllabus batch_id sent to listAcademicProgramByEnrollment
 * (e.g. "HSC 2028" for C11/C12, "SSC 2027" for C9/C10) with zero hardcoded years.
 */
fun formatSyllabusBatchId(
    classCode: String?,
    passingYear: String?,
    fallbackBatchLabel: String? = null
): String? {
    val cleanYear = extractPassingYear(passingYear) ?: extractPassingYear(fallbackBatchLabel)
    val cls = classCode?.trim()?.uppercase()
    return when {
        cleanYear != null && cls in listOf("C11", "C12", "HSC") -> "HSC $cleanYear"
        cleanYear != null && cls in listOf("C9", "C09", "C10", "SSC") -> "SSC $cleanYear"
        !fallbackBatchLabel.isNullOrBlank() -> fallbackBatchLabel.trim()
        cleanYear != null -> cleanYear
        else -> null
    }
}

/**
 * Normalizes any stored/API study group string (English, short code, or Bengali) into Shikho's
 * StudyGroupTypeEnum ("Science", "Humanities", "BusinessStudies", or "None").
 * Never silently defaults an unknown/empty group to "Humanities" or "Science".
 */
fun normalizeStudyGroupEnum(classCode: String?, rawGroup: String?): String {
    val cls = classCode?.trim()?.uppercase()
    if (cls in listOf("C5", "C05", "C6", "C06", "C7", "C07", "C8", "C08")) {
        return "None"
    }
    if (rawGroup.isNullOrBlank()) return "None"
    return when (rawGroup.trim().lowercase()) {
        "humanities", "humanities_group", "arts", "hum", "মানবিক", "মানবিক বিভাগ" -> "Humanities"
        "science", "science_group", "sci", "বিজ্ঞান", "বিজ্ঞান বিভাগ" -> "Science"
        "business_studies", "businessstudies", "business_studies_group", "business", "commerce", "bus", "bs", "bba", "ব্যবসায় শিক্ষা", "ব্যবসায় শিক্ষা", "ব্যবসায়", "ব্যবসায়" -> "BusinessStudies"
        "none", "null", "n/a" -> "None"
        else -> rawGroup.trim()
    }
}

/**
 * Localized display name for a class code with zero hardcoded "Class 11" / "HSC" fallbacks.
 */
fun formatClassDisplayName(code: String?): String {
    val clean = code?.trim().orEmpty()
    if (clean.isBlank()) return ""
    return when (clean.uppercase()) {
        "C5", "C05", "CLASS 5", "5" -> com.example.utils.AppLang.tr("Class 5", "পঞ্চম শ্রেণি")
        "C6", "C06", "CLASS 6", "6" -> com.example.utils.AppLang.tr("Class 6", "ষষ্ঠ শ্রেণি")
        "C7", "C07", "CLASS 7", "7" -> com.example.utils.AppLang.tr("Class 7", "সপ্তম শ্রেণি")
        "C8", "C08", "CLASS 8", "8" -> com.example.utils.AppLang.tr("Class 8", "অষ্টম শ্রেণি")
        "C9", "C09", "CLASS 9", "9", "IX" -> com.example.utils.AppLang.tr("Class 9", "নবম শ্রেণি")
        "C10", "CLASS 10", "10", "X" -> com.example.utils.AppLang.tr("Class 10", "দশম শ্রেণি")
        "C11", "CLASS 11", "11", "XI" -> com.example.utils.AppLang.tr("Class 11", "একাদশ শ্রেণি")
        "C12", "CLASS 12", "12", "XII" -> com.example.utils.AppLang.tr("Class 12", "দ্বাদশ শ্রেণি")
        "HSC" -> com.example.utils.AppLang.tr("HSC", "এইচএসসি")
        "SSC" -> com.example.utils.AppLang.tr("SSC", "এসএসসি")
        else -> clean
    }
}

/**
 * Localized display name for a study group with zero hardcoded group fallbacks.
 */
fun formatStudyGroupDisplayName(rawGroup: String?, includeDepartmentSuffix: Boolean = false): String {
    val clean = rawGroup?.trim().orEmpty()
    if (clean.isBlank()) return ""
    return when (clean.lowercase()) {
        "science", "science_group", "sci", "বিজ্ঞান", "বিজ্ঞান বিভাগ" ->
            if (includeDepartmentSuffix) com.example.utils.AppLang.tr("Science", "বিজ্ঞান বিভাগ")
            else com.example.utils.AppLang.tr("Science", "বিজ্ঞান")
        "humanities", "humanities_group", "arts", "hum", "মানবিক", "মানবিক বিভাগ" ->
            if (includeDepartmentSuffix) com.example.utils.AppLang.tr("Humanities", "মানবিক বিভাগ")
            else com.example.utils.AppLang.tr("Humanities", "মানবিক")
        "business_studies", "businessstudies", "business_studies_group", "business", "commerce", "bus", "bs", "bba", "ব্যবসায় শিক্ষা", "ব্যবসায় শিক্ষা", "ব্যবসায়", "ব্যবসায়" ->
            if (includeDepartmentSuffix) com.example.utils.AppLang.tr("Business Studies", "ব্যবসায় শিক্ষা বিভাগ")
            else com.example.utils.AppLang.tr("Business Studies", "ব্যবসায় শিক্ষা")
        "none", "null" -> ""
        else -> clean
    }
}

/**
 * API-প্রদত্ত batch options থেকে সবচেয়ে likely "current" batch বেছে নেয়।
 *
 * Priority (কোনো hardcoded year/label ছাড়া, শুধু API data):
 *  1. year == stored passing year (বা storedBatchLabel থেকে extracted year)
 *  2. label == stored batch label
 *  3. label-এ "Running" / "New" আছে কিন্তু "Old" নেই
 *  4. প্রথম option
 */
fun pickDefaultBatch(
    options: List<BatchOptionItem>,
    storedPassingYear: String?,
    storedBatchLabel: String?
): BatchOptionItem? {
    if (options.isEmpty()) return null

    val effectiveYear = extractPassingYear(storedPassingYear) ?: extractPassingYear(storedBatchLabel)
    if (!effectiveYear.isNullOrBlank()) {
        options.find { it.yearString.equals(effectiveYear, ignoreCase = true) }
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
