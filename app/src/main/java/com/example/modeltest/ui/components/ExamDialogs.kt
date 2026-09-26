package com.example.modeltest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.toBengaliDigits

/**
 * Score Popup Dialog: "MCQ টেস্টের স্কোর X/Y"
 */
@Composable
fun ScorePopupDialog(
    score: Double,
    totalMarks: Double,
    isPractice: Boolean,
    onProceed: () -> Unit
) {
    val formattedScore = if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(score)
    val formattedTotal = if (totalMarks % 1.0 == 0.0) totalMarks.toInt().toString() else "%.1f".format(totalMarks)

    AlertDialog(
        onDismissRequest = { /* Force explicit button press */ },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "MCQ টেস্টের স্কোর",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${toBengaliDigits(formattedScore)} / ${toBengaliDigits(formattedTotal)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPractice)
                        "তোমার প্র্যাকটিস MCQ টেস্ট সম্পন্ন হয়েছে! এবার CQ প্রশ্নগুলো দেখে নিতে পারো।"
                    else
                        "তোমার মেইন MCQ টেস্ট সফলভাবে জমা হয়েছে। এখন CQ উত্তর আপলোড করার জন্য প্রস্তুত হও।",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (isPractice) "CQ প্রশ্নগুলো পড়ো" else "CQ উত্তর আপলোড করো",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    )
}

/**
 * Confirmation dialog for back button press during examination
 */
@Composable
fun ExitExamConfirmDialog(
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "তুমি কি পরীক্ষা ছেড়ে যেতে চাও?",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "এখন পরীক্ষা থেকে বের হয়ে গেলে তোমার বর্তমান অগ্রগতি হারিয়ে যেতে পারে এবং টেস্ট অসম্পূর্ণ থাকবে।",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmExit,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("হ্যাঁ, বের হও", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("পরীক্ষায় থাকো")
            }
        }
    )
}
