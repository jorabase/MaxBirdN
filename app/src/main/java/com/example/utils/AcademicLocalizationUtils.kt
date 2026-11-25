package com.example.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

object AcademicLocalizationUtils {

    // 1. Empty questions state based on ordinal (0: Subject level, 1: Chapter level)
    fun getEmptyQuestionsTitle(ordinal: Int): String {
        return if (ordinal == 0) {
            AppLang.tr("Not enough questions for this subject yet", "এই বিষয়ের জন্য এখনো পর্যাপ্ত প্রশ্ন নেই")
        } else {
            AppLang.tr("Not enough questions for this chapter yet", "এই চ্যাপ্টারের জন্য এখনো পর্যাপ্ত প্রশ্ন নেই")
        }
    }

    fun getEmptyQuestionsSubtitle(ordinal: Int): String {
        return if (ordinal == 0) {
            AppLang.tr("More questions are being added for this subject. Please try again later.", "এই বিষয়ের জন্য আরও প্রশ্ন যোগ করা হচ্ছে। কিছুদিন পর আবার চেষ্টা করো।")
        } else {
            AppLang.tr("More questions are being added for this chapter. Please try again later.", "এই চ্যাপ্টারের জন্য আরও প্রশ্ন যোগ করা হচ্ছে। কিছুদিন পর আবার চেষ্টা করো।")
        }
    }

    // 2. Answer review state
    fun getAnswerStatus(answered: Boolean, yourAnswer: String? = null): String {
        return if (!answered) {
            AppLang.tr("You did not answer!", "তুমি কোন উত্তর দাও নি!")
        } else {
            val prefix = AppLang.tr("Your Answer: ", "তোমার উত্তর : ")
            if (!yourAnswer.isNullOrBlank()) "$prefix$yourAnswer" else prefix
        }
    }

    // Localized getters (source-compatible with existing property usages)
    val CORRECT_ANSWER: String get() = AppLang.tr("Correct Answer: ", "সঠিক উত্তর : ")
    val ACTION_OK: String get() = AppLang.tr("OK", "ঠিক আছে")
    val ACTION_ALL_CHAPTERS: String get() = AppLang.tr("All Chapters", "সকল অধ্যায়")
    val TIME_UNIT: String get() = AppLang.tr(" min", " মিনিট")
    val CHAPTER_PREFIX: String get() = AppLang.tr("Chapter ", "অধ্যায় ")

    // 3. Content type mapping: "Live", "Video", "SmartNotes", "Exam"
    fun translateContentType(type: String?): String {
        if (type.isNullOrBlank()) return AppLang.tr("Live Class", "লাইভ ক্লাস")
        val lower = type.trim().lowercase()
        return when {
            lower == "live" || lower == "liveclass" || lower.contains("live") -> AppLang.tr("Live Class", "লাইভ ক্লাস")
            lower == "video" || lower.contains("video") -> AppLang.tr("Video Lecture", "ভিডিও লেকচার")
            lower == "smartnotes" || lower == "smart_notes" || lower == "ebook" || lower.contains("note") -> AppLang.tr("E-Book", "ই-বুক")
            lower == "exam" || lower == "liveexam" || lower == "quiz" || lower == "modeltest" || lower.contains("exam") || lower.contains("quiz") -> AppLang.tr("Quiz & Exam", "কুইজ এবং এক্সাম")
            else -> AppLang.tr("Live Class", "লাইভ ক্লাস")
        }
    }

    // 4. Class types mapping
    fun translateClassType(raw: String?): String {
        if (raw.isNullOrBlank()) return AppLang.tr("Lecture", "লেকচার ক্লাস")
        val lower = raw.trim().lowercase()
        return when {
            lower.contains("doubt") -> AppLang.tr("Doubt Class", "ডাউট ক্লাস")
            lower.contains("orientation") -> AppLang.tr("Orientation", "ওরিয়েনটেশন ক্লাস")
            lower.contains("solving") || lower.contains("solution") -> AppLang.tr("Solving Class", "সলভিং ক্লাস")
            lower.contains("concept") -> AppLang.tr("Concept Class", "কনসেপ্ট ক্লাস")
            lower.contains("analysis") || lower.contains("analytic") -> AppLang.tr("Analysis Class", "অ্যানালাইসিস ক্লাস")
            lower.contains("extra") -> AppLang.tr("Extra Class", "এক্সট্রা ক্লাস")
            lower.contains("lecture") -> AppLang.tr("Lecture", "লেকচার ক্লাস")
            else -> AppLang.tr("Lecture", "লেকচার ক্লাস")
        }
    }
}

@Composable
fun EmptyQuestionsCard(
    ordinal: Int,
    onOkClick: () -> Unit,
    onAllChaptersClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF10B981)
) {
    // Subscribe to LocalAppLanguage changes for instant recomposition
    val isBn = isBangla()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                accentColor.copy(alpha = 0.2f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AssignmentLate,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isBn) AcademicLocalizationUtils.getEmptyQuestionsTitle(ordinal)
                else AcademicLocalizationUtils.getEmptyQuestionsTitle(ordinal),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = AcademicLocalizationUtils.getEmptyQuestionsSubtitle(ordinal),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAllChaptersClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accentColor
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("All Chapters", "সকল অধ্যায়"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onOkClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("OK", "ঠিক আছে"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionAnswerReviewItem(
    questionText: String,
    answered: Boolean,
    userAnswer: String? = null,
    correctAnswer: String? = null,
    explanation: String? = null,
    timeSpentSeconds: Int? = null,
    modifier: Modifier = Modifier
) {
    val isBn = isBangla()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (!answered) Color(0xFFEF4444).copy(alpha = 0.12f)
                    else if (userAnswer == correctAnswer) Color(0xFF10B981).copy(alpha = 0.12f)
                    else Color(0xFFF59E0B).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (!answered) AcademicLocalizationUtils.getAnswerStatus(false)
                        else if (userAnswer == correctAnswer) tr("Correct", "সঠিক")
                        else tr("Wrong", "ভুল"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!answered) Color(0xFFEF4444)
                        else if (userAnswer == correctAnswer) Color(0xFF059669)
                        else Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (timeSpentSeconds != null && timeSpentSeconds > 0) {
                    val minutes = (timeSpentSeconds / 60).coerceAtLeast(1)
                    Text(
                        text = "${toBengaliDigits(minutes)}${if (isBn) " মিনিট" else " min"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = questionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AcademicLocalizationUtils.getAnswerStatus(answered, userAnswer),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (!answered) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!correctAnswer.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${tr("Correct Answer: ", "সঠিক উত্তর : ")}$correctAnswer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
            }

            if (!explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${tr("Explanation: ", "ব্যাখ্যা: ")}$explanation",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
