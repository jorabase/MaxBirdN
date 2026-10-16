package com.example.utils

import android.util.Log

/**
 * Universal MCQ Answer Parsing, Normalization, and Extraction Utility.
 * 
 * Provides rock-solid, production-grade mapping and comparison between user selections,
 * correct answers, and solution/explanation texts across all Bangladeshi curriculum formats:
 * - Bengali letters: ক, খ, গ, ঘ
 * - English letters: A, B, C, D (case-insensitive)
 * - Digits: 1, 2, 3, 4 and ১, ২, ৩, ৪
 * - Zero-based index strings: "0", "1", "2", "3"
 * - Option naming: "OPTION_1", "OPTION_A", "OPTION 4", etc.
 * - Markdown & HTML formatted explanations: "**সঠিক উত্তর:** (ঘ)", "<strong>উত্তরঃ</strong> ঘ", etc.
 * - Bengali punctuation including Visarga (ঃ), Dari (।), Colons (:), and Dashes (-, –, —).
 */
object McqAnswerUtils {
    private const val TAG = "McqAnswerUtils"

    val BENGALI_LETTERS = listOf("ক", "খ", "গ", "ঘ")
    val ENGLISH_LETTERS = listOf("A", "B", "C", "D")

    /**
     * Converts any option representation to a 0-based index:
     * 0 -> ক / A / 1 / ১ / OPTION_A / OPTION_1 / 0
     * 1 -> খ / B / 2 / ২ / OPTION_B / OPTION_2 / 1
     * 2 -> গ / C / 3 / ৩ / OPTION_C / OPTION_3 / 2
     * 3 -> ঘ / D / 4 / ৪ / OPTION_D / OPTION_4 / 3
     */
    fun normalizeToOptionIndex(input: String?): Int? {
        if (input.isNullOrBlank()) return null

        val raw = input.trim()

        // Strip enclosing quotes, brackets, parentheses, asterisks, punctuation
        val clean = raw
            .replace(Regex("""^[\s*#_(\[{<`'"“‘]+|[\s*#_)\]}>`'"”’.।:\-–—\u0983\uFF1A]+$"""), "")
            .trim()
            .uppercase()

        when (clean) {
            "A", "ক", "1", "১", "OPTION_A", "OPTION_1", "OPTION_0", "OPTION A", "OPTION 1", "OPTION 0" -> return 0
            "B", "খ", "2", "২", "OPTION_B", "OPTION_2", "OPTION B", "OPTION 2" -> return 1
            "C", "গ", "3", "৩", "OPTION_C", "OPTION_3", "OPTION C", "OPTION 3" -> return 2
            "D", "ঘ", "4", "৪", "OPTION_D", "OPTION_4", "OPTION D", "OPTION 4" -> return 3
            "0" -> return 0
        }

        // Secondary check: search for standalone option letter inside brackets or delimiters
        val match = Regex("""(?:^|[\s(\[{<`'"“‘*_])([A-Da-dক-ঘ1-4১-৪])(?:[\s)\]}>`'"”’.।:\-–—\u0983\uFF1A*_]|$)""").find(raw)
        if (match != null) {
            return when (match.groupValues[1].uppercase()) {
                "A", "ক", "1", "১" -> 0
                "B", "খ", "2", "২" -> 1
                "C", "গ", "3", "৩" -> 2
                "D", "ঘ", "4", "৪" -> 3
                else -> null
            }
        }

        return null
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
     * Strips HTML tags and Markdown formatting from solution text for robust regex parsing.
     */
    private fun sanitizeText(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), " ") // Strip HTML tags
            .replace(Regex("""[*_`#~]"""), "") // Strip markdown bold, italics, headers
            .replace('\u00A0', ' ') // Replace non-breaking spaces
            .trim()
    }

    /**
     * Robust extractor of the correct option index from solution / explanation text.
     * 
     * Handles all patterns:
     * - "সঠিক উত্তর: (ঘ)", "সঠিক উত্তরঃ ঘ", "সঠিক উত্তরটি হলো ঘ", "**সঠিক উত্তর:** (ঘ)"
     * - "উত্তরঃ ঘ", "উত্তর: (ঘ)", "উত্তর ঃ ঘ", "**উত্তরঃ** ঘ"
     * - "(ঘ) সঠিক", "(ঘ) সঠিক উত্তর", "(ঘ) হলো সঠিক", "(ঘ) নং সঠিক"
     * - "Ans: D", "Ans. D", "Ans.- D", "Answer is (D)", "Correct option: (D)"
     * - Parenthesized letter at conclusion e.g. "... (ঘ)।"
     * - Unique option description match in explanation.
     */
    fun extractCorrectIndexFromSolution(
        solutionText: String?,
        optionsDescriptions: List<String?>? = null
    ): Int? {
        if (solutionText.isNullOrBlank()) return null

        val clean = sanitizeText(solutionText)

        // 1. Explicit answer statements with keywords (find the LAST occurrence, which represents the conclusion)
        val explicitPatterns = listOf(
            // e.g. "সঠিক উত্তর: (ঘ)", "সঠিক উত্তরঃ ঘ", "সঠিক উত্তরটি হলো ঘ", "সঠিক উত্তরটি (ঘ)", "সঠিক উত্তর হবে (ঘ)", "সঠিক উত্তর (ঘ)"
            Regex("""(?:সুতরাং|অতএব|তাই|কাজেই)?\s*(?:সঠিক|নির্ণেয়|প্রকৃত|আসল)?\s*(?:উত্তর|বিকল্প|অপশন|চয়েস)(?:টি)?(?:\s*(?:হলো|হল|হচ্ছে|হবে|দাঁড়ায়))?\s*[:\u0983\uFF1A=–—.\-]?\s*[\(（\[{<‘'“"]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]?""", RegexOption.IGNORE_CASE),

            // e.g. "উত্তরঃ (ঘ)", "উত্তর: ঘ", "উত্তরঃ ঘ", "উত্তর ঃ (ঘ)", "উত্তর (ঘ)", "উত্তর হলো ঘ", "উত্তর হবে (ঘ)"
            Regex("""(?:^|\n|\.\s+|;\s+|।\s+)(?:উত্তর|Ans(?:wer)?|Option)\s*[:\u0983\uFF1A=–—.\-]?\s*[\(（\[{<‘'“"]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]?""", RegexOption.IGNORE_CASE),

            // e.g. "(ঘ) সঠিক", "(ঘ) সঠিক উত্তর", "(ঘ) হলো সঠিক উত্তর", "(ঘ) নং সঠিক", "(ঘ) নম্বরটি সঠিক", "(ঘ) অপশনটি সঠিক", "ঘ হলো সঠিক উত্তর", "ঘ সঠিক"
            Regex("""[\(（\[{<‘'“"]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]?(?:\s*(?:নং|নম্বর|নম্বরটি|অপশনটি|বিকল্পটি))?\s*(?:হলো|হল|হচ্ছে|হবে)?\s*(?:সঠিক|is\s*correct|is\s*the\s*correct)""", RegexOption.IGNORE_CASE),

            // e.g. "Ans: D", "Ans. D", "Ans.- D", "Ans: (ঘ)", "Answer: D", "Answer is (D)", "Ans is D", "Correct answer is D"
            Regex("""\b(?:Ans(?:wer)?|Correct\s*Answer|Correct\s*Option)\s*(?:is)?\s*[:\u0983\uFF1A=–—.\-]?\s*[\(（\[{<‘'“"]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]?""", RegexOption.IGNORE_CASE),

            // e.g. "Option (D) is correct", "Option D is correct", "Option (ঘ) সঠিক"
            Regex("""\bOption\s*[\(（\[{<‘'“"]?\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]?\s*(?:is\s*correct|সঠিক)""", RegexOption.IGNORE_CASE),

            // Standalone letter in parentheses at the very end of the solution (e.g. "... = 40 m/s (ঘ)।")
            Regex("""[\(（\[{<‘'“"]\s*([A-Da-dক-ঘ1-4১-৪])\s*[\)）\]}>’'”"]\s*[.।\s]*$""", RegexOption.IGNORE_CASE)
        )

        for (pattern in explicitPatterns) {
            val matches = pattern.findAll(clean).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val letter = lastMatch.groupValues[1]
                val idx = normalizeToOptionIndex(letter)
                if (idx != null) {
                    Log.d(TAG, "extractCorrectIndexFromSolution: matched '$letter' via pattern '${pattern.pattern}' -> index $idx")
                    return idx
                }
            }
        }

        // 2. Check if the text explicitly states the description of one option after "সঠিক উত্তর" or "উত্তর"
        if (!optionsDescriptions.isNullOrEmpty()) {
            val answerPrefixRegex = Regex("""(?:সঠিক\s*উত্তর(?:টি)?(?:\s*(?:হলো|হল|হচ্ছে|হবে))?|উত্তর|নির্ণেয়\s*উত্তর)\s*[:\u0983\uFF1A=–—.\-]?\s*(.+)""", RegexOption.IGNORE_CASE)
            val prefixMatches = answerPrefixRegex.findAll(clean).toList()
            if (prefixMatches.isNotEmpty()) {
                val remainder = prefixMatches.last().groupValues[1].trim()
                optionsDescriptions.forEachIndexed { idx, desc ->
                    if (!desc.isNullOrBlank()) {
                        val cleanDesc = sanitizeText(desc)
                        if (cleanDesc.length >= 2 && remainder.contains(cleanDesc, ignoreCase = true)) {
                            Log.d(TAG, "extractCorrectIndexFromSolution: matched description after prefix -> index $idx ($cleanDesc)")
                            return idx
                        }
                    }
                }
            }

            // 3. Match option descriptions directly against the full clean solution text
            val matchingOptions = optionsDescriptions.mapIndexedNotNull { idx, desc ->
                if (desc.isNullOrBlank()) null
                else {
                    val cleanDesc = sanitizeText(desc)
                    if (cleanDesc.length >= 3 && clean.contains(cleanDesc, ignoreCase = true)) {
                        val lastPos = clean.lastIndexOf(cleanDesc, ignoreCase = true)
                        Pair(idx, lastPos)
                    } else null
                }
            }

            if (matchingOptions.size == 1) {
                val matchedIdx = matchingOptions.first().first
                Log.d(TAG, "extractCorrectIndexFromSolution: exactly one option matched description in text -> index $matchedIdx")
                return matchedIdx
            } else if (matchingOptions.size > 1) {
                val best = matchingOptions.maxByOrNull { it.second }
                if (best != null) {
                    Log.d(TAG, "extractCorrectIndexFromSolution: multiple options matched, taking closest to conclusion -> index ${best.first}")
                    return best.first
                }
            }
        }

        return null
    }

    /**
     * Resolves the canonical correct option index (0..3) using all available signals:
     * 1. Direct correct_option from question API
     * 2. Direct ans.correct_ans from submission API
     * 3. Server confirmation (isUserMarkedCorrectByServer == true)
     * 4. Extracted from solution explanation text
     * 5. Match between user's chosen option and solution text
     * 6. Unique option description matching
     * 7. Fallback to user choice if valid, or 0
     */
    fun resolveCorrectOptionIndex(
        directCorrectOption: String?,
        ansCorrectOption: String?,
        solutionText: String?,
        optionsDescriptions: List<String?>? = null,
        userSelectedOptionIndex: Int? = null,
        isUserMarkedCorrectByServer: Boolean? = null
    ): Int {
        // Priority 1: Direct question API field
        val idxFromDirect = normalizeToOptionIndex(directCorrectOption)
        if (idxFromDirect != null) {
            return idxFromDirect
        }

        // Priority 2: Submission question_answer API field
        val idxFromAns = normalizeToOptionIndex(ansCorrectOption)
        if (idxFromAns != null) {
            return idxFromAns
        }

        // Priority 3: Server marked user as correct!
        if (isUserMarkedCorrectByServer == true && userSelectedOptionIndex != null && userSelectedOptionIndex in 0..3) {
            Log.d(TAG, "resolveCorrectOptionIndex: server validated user correct -> index $userSelectedOptionIndex")
            return userSelectedOptionIndex
        }

        // Priority 4: Extract from explanation / solution text
        val idxFromSol = extractCorrectIndexFromSolution(solutionText, optionsDescriptions)
        if (idxFromSol != null) {
            return idxFromSol
        }

        // Priority 5: If user selected an option and its description appears in the solution,
        // it confirms the user's answer
        if (userSelectedOptionIndex != null && userSelectedOptionIndex in 0..3 && !solutionText.isNullOrBlank()) {
            val userDesc = optionsDescriptions?.getOrNull(userSelectedOptionIndex)
            if (!userDesc.isNullOrBlank()) {
                val cleanUserDesc = sanitizeText(userDesc)
                if (cleanUserDesc.length >= 3 && sanitizeText(solutionText).contains(cleanUserDesc, ignoreCase = true)) {
                    Log.d(TAG, "resolveCorrectOptionIndex: user selected option ($userSelectedOptionIndex) matches solution text")
                    return userSelectedOptionIndex
                }
            }
        }

        // Priority 6: Check options descriptions against solution
        if (!optionsDescriptions.isNullOrEmpty() && !solutionText.isNullOrBlank()) {
            val cleanSol = sanitizeText(solutionText)
            val matched = optionsDescriptions.mapIndexedNotNull { idx, desc ->
                if (!desc.isNullOrBlank() && cleanSol.contains(sanitizeText(desc), ignoreCase = true)) idx else null
            }
            if (matched.size == 1) {
                return matched.first()
            }
        }

        // Priority 7: If user answered and we have no other counter-evidence, never arbitrarily punish user with 0
        if (userSelectedOptionIndex != null && userSelectedOptionIndex in 0..3 && solutionText.isNullOrBlank()) {
            return userSelectedOptionIndex
        }

        return 0
    }

    /**
     * Resolves the canonical correct option letter ("ক", "খ", "গ", "ঘ") using all signals.
     */
    fun resolveCorrectOptionBengali(
        directCorrectOption: String?,
        ansCorrectOption: String?,
        solutionText: String?,
        optionsDescriptions: List<String?>? = null,
        userSelectedOptionIndex: Int? = null,
        isUserMarkedCorrectByServer: Boolean? = null
    ): String {
        val idx = resolveCorrectOptionIndex(
            directCorrectOption = directCorrectOption,
            ansCorrectOption = ansCorrectOption,
            solutionText = solutionText,
            optionsDescriptions = optionsDescriptions,
            userSelectedOptionIndex = userSelectedOptionIndex,
            isUserMarkedCorrectByServer = isUserMarkedCorrectByServer
        )
        return indexToBengaliLetter(idx)
    }
}
