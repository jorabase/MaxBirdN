package com.example.modeltest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.toBengaliDigits
import com.example.utils.tr

/**
 * Question Palette Bottom Sheet ('সবগুলো দেখো')
 * - Green (answered)
 * - White/Surface (unanswered)
 * - Blue (currently selected question)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionPaletteBottomSheet(
    totalQuestions: Int,
    currentIndex: Int,
    answeredIndices: Set<Int>,
    onSelectQuestion: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val isDark = com.example.ui.theme.isAppInDarkTheme()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("All Questions ($totalQuestions)", "সবগুলো প্রশ্ন (${toBengaliDigits(totalQuestions)})"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(color = Color(0xFF10B981), label = tr("Answered", "উত্তর দেওয়া"))
                LegendItem(color = Color(0xFF3B82F6), label = tr("Current", "বর্তমান"))
                LegendItem(
                    color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                    label = tr("Unanswered", "উত্তরহীন"),
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(totalQuestions) { index ->
                    val isCurrent = index == currentIndex
                    val isAnswered = answeredIndices.contains(index)

                    val bgColor = when {
                        isCurrent -> Color(0xFF3B82F6)
                        isAnswered -> Color(0xFF10B981)
                        else -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    }
                    val textColor = when {
                        isCurrent || isAnswered -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) Color(0xFF1D4ED8) else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onSelectQuestion(index)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("${index + 1}", toBengaliDigits(index + 1)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 12.sp, color = textColor)
    }
}
