package com.example.utils

import android.util.Log

/**
 * Universal MCQ Answer Parsing and Normalization Utility.
 * 
 * Accurately maps and compares user selections, correct answers, and solution text
 * across all formats (Bengali ক/খ/গ/ঘ, English A/B/C/D, Numerals 1/2/3/4 or ১/২/৩/৪,
 * zero-indexed 0/1/2/3, and explicit text statements like "সঠিক উত্তর: ঘ").
 */
object McqAnswerUtils {
    private const val TAG = "McqAnswerUtils"

    val BENGALI_LETTERS = listOf("ক", "খ", "গ", "ঘ")
    val ENGLISH_LETTERS = listOf("A", "B", "C", "D")

    /**
     * Converts any option representation to a 0-based index:
     * 0 -> ক / A / 1 / ১
     * 1 -> খ / B / 2 / ২
     * 2 -> গ / C / 3 / ৩
     * 3 -> ঘ / D / 4 / ৪
     */
    fun normalizeToOptionIndex(input: String?): Int? {
        if (input.isNullOrBlank()) return null
        val clean = input.trim()
            .replace(Regex("""^[(\[{<]|[)\]}>.:\-–]$"""), "")
            .trim()
            .uppercase()

        return when (clean) {
            "A", "ক", "1", "১", "OPTION_A", "OPTION_1", "OPTION_0" -> 0
            "B", "খ", "2", "২", "OPTION_B", "OPTION_2" -> 1
            "C", "গ", "3", "৩", "OPTION_C", "OPTION_3" -> 2
            "D", "ঘ", "4", "৪", "OPTION_D", "OPTION_4" -> 3
            "0" -> 0
            else -> {
                // Check if string contains any of the Bengali or English letters explicitly
                val match = Regex("""(?:^|[\s(\[{<])([A-Dক-ঘ1-4১-৪])(?:[\s)\]}>.:\-–]|$)""").find(input)
                if (match != null) {
                    when (match.groupValues[1].uppercase()) {
                        "A", "ক", "1", "১" -> 0
                        "B", "খ", "2", "২" -> 1
                        "C", "গ", "3", "৩" -> 2
                        "D", "ঘ", "4", "৪" -> 3
                        else -> null
                    }
                } else null
            }
        }
    }

    /**
     * Converts a 0-based index to Bengali letter (0 -> "ক", 1 -> "খ", 2 -> "গ", 3 -> "ঘ").
     */
    fun indexToBengaliLetter(index: Int?): String {
        if (index == null || index < 0) return ""
        return BENGALI_LETTERS.getOrElse(index) { "${index + 1}" }
    }

    /**
     * Converts a 0-based index to English letter (0 -> "A", 1 -> "B", 2 -> "C", 3 -> "D").
     */
    fun indexToEnglishLetter(index: Int?): String {
        if (index == null || index < 0) return ""
        return ENGLISH_LETTERS.getOrElse(index) { ('A' + index).toString() }
    }

    /**
     * Converts any raw string (e.g. "D", "4", "ঘ", "Option D") to a clean Bengali letter (ক, খ, গ, ঘ).
     */
    fun toBengaliLetter(input: String?): String {
        val idx = normalizeToOptionIndex(input)
        return if (idx != null) indexToBengaliLetter(idx) else input?.trim().orEmpty()
    }

    /**
     * Converts any raw string to a clean English letter (A, B, C, D).
     */
    fun toEnglishLetter(input: String?): String {
        val idx = normalizeToOptionIndex(input)
        return if (idx != null) indexToEnglishLetter(idx) else input?.trim().orEmpty()
    }

    /**
     * Checks if user given answer matches the correct answer, comparing across
     * all representations (canonical index, letter, and raw string match).
     */
    fun isAnswerCorrect(givenAns: String?, correctAns: String?): Boolean {
        if (givenAns.isNullOrBlank() || correctAns.isNullOrBlank()) return false
        val cleanGiven = givenAns.trim()
        val cleanCorrect = correctAns.trim()

        if (cleanGiven.equals(cleanCorrect, ignoreCase = true)) return true

        val givenIdx = normalizeToOptionIndex(cleanGiven)
        val correctIdx = normalizeToOptionIndex(cleanCorrect)

        if (givenIdx != null && correctIdx != null) {
            return givenIdx == correctIdx
        }

        return false
    }

    /**
     * Robust extractor of the correct option index from solution / explanation text.
     * High priority is given to explicit statements like "সঠিক উত্তর: ঘ", "Ans: D", etc.
     */
    fun extractCorrectIndexFromSolution(
        solutionText: String?,
        optionsDescriptions: List<String?>? = null
    ): Int? {
        if (solutionText.isNullOrBlank()) return null

        // 1. Highest priority: Explicit answer statements in Bengali / English / Markdown
        val explicitPatterns = listOf(
            // e.g. "সঠিক উত্তর: ঘ", "সঠিক উত্তরটি হলো (ঘ)", "সঠিক উত্তর : D", "সঠিক উত্তর-ঘ"
            Regex("""(?:সঠিক\s*উত্তর(?:টি)?(?:\s*হলো|\s*হচ্ছে)?|সঠিক\s*বিকল্প)\s*[:=–-]?\s*[\(（\[]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]]?""", RegexOption.IGNORE_CASE),
            // e.g. "**সঠিক উত্তর:** (ঘ)", "**উত্তর:** D"
            Regex("""\*\*(?:সঠিক\s*উত্তর|উত্তর|Ans(?:wer)?)\*\*\s*[:=–-]?\s*[\(（\[]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]]?""", RegexOption.IGNORE_CASE),
            // e.g. "উত্তর: ঘ", "উত্তর : (ঘ)", "উত্তর হলো ঘ"
            Regex("""(?:^|\n|\.\s+)(?:উত্তর|Ans(?:wer)?|Option)\s*[:=–-]?\s*[\(（\[]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]]?""", RegexOption.IGNORE_CASE),
            // e.g. "(ঘ) সঠিক", "(D) is correct", "ঘ নং সঠিক", "(ঘ) হলো সঠিক উত্তর"
            Regex("""[\(（\[]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]]?(?:\s*নং|\s*নম্বর)?\s*(?:সঠিক|হলো\s*সঠিক|is\s*correct)""", RegexOption.IGNORE_CASE),
            // Standalone e.g. "Ans: D" anywhere
            Regex("""\bAns(?:wer)?\s*[:=–-]?\s*[\(（\[]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]]?""", RegexOption.IGNORE_CASE)
        )

        for (pattern in explicitPatterns) {
            val match = pattern.find(solutionText)
            if (match != null) {
                val letter = match.groupValues[1]
                val idx = normalizeToOptionIndex(letter)
                if (idx != null) {
                    Log.d(TAG, "extractCorrectIndexFromSolution: found explicit match '$letter' -> index $idx")
                    return idx
                }
            }
        }

        // 2. Secondary check: If solution explicitly states the full description of a single option after "সঠিক উত্তর:"
        if (!optionsDescriptions.isNullOrEmpty()) {
            val answerPrefixRegex = Regex("""(?:সঠিক\s*উত্তর(?:টি)?(?:\s*হলো|\s*হচ্ছে)?|উত্তর)\s*[:=–-]?\s*(.+)""", RegexOption.IGNORE_CASE)
            val prefixMatch = answerPrefixRegex.find(solutionText)
            if (prefixMatch != null) {
                val remainder = prefixMatch.groupValues[1].trim()
                optionsDescriptions.forEachIndexed { idx, desc ->
                    if (!desc.isNullOrBlank() && desc.trim().length >= 2) {
                        if (remainder.startsWith(desc.trim(), ignoreCase = true)) {
                            Log.d(TAG, "extractCorrectIndexFromSolution: matched description after prefix -> index $idx")
                            return idx
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Resolves the canonical correct option letter ("ক", "খ", "গ", "ঘ") using all available sources:
     * 1. Direct correct_option from API if present
     * 2. Direct ans.correct_ans if present
     * 3. Extracted from solution text
     * 4. Fallback (safe, does not arbitrarily bias to 'ক')
     */
    fun resolveCorrectOptionBengali(
        directCorrectOption: String?,
        ansCorrectOption: String?,
        solutionText: String?,
        optionsDescriptions: List<String?>? = null,
        fallbackIndex: Int = 0
    ): String {
        // Priority 1: Direct field from API
        val idxFromDirect = normalizeToOptionIndex(directCorrectOption)
        if (idxFromDirect != null) {
            return indexToBengaliLetter(idxFromDirect)
        }

        // Priority 2: Ans object correct option
        val idxFromAns = normalizeToOptionIndex(ansCorrectOption)
        if (idxFromAns != null) {
            return indexToBengaliLetter(idxFromAns)
        }

        // Priority 3: Extract from solution explanation text
        val idxFromSol = extractCorrectIndexFromSolution(solutionText, optionsDescriptions)
        if (idxFromSol != null) {
            return indexToBengaliLetter(idxFromSol)
        }

        return indexToBengaliLetter(fallbackIndex)
    }
}
