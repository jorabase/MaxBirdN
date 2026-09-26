package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.EnrolledProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// LOGIC: badge states হুবহু সেম
data class ProgramBadgeInfo(val text: String, val containerColor: Color, val textColor: Color)

fun getProgramBadge(program: EnrolledProgram): ProgramBadgeInfo {
    val details = program.enrollment_details
    val type = details?.type
    val isActive = details?.is_active == true
    val isTrial = type == "FullApTrial" || (program.trial_enabled == true && program.is_free != true)
    val isFree = program.is_free == true || type == "FREE"

    val isTrialExpired = if (isTrial && !details?.trial_end_date.isNullOrBlank()) {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = format.parse(details!!.trial_end_date!!)
            date != null && date.before(Date())
        } catch (_: Exception) {
            !isActive
        }
    } else {
        !isActive && isTrial
    }

    return when {
        isFree -> ProgramBadgeInfo("সম্পূর্ণ ফ্রি", Color(0xFFDCFCE7), Color(0xFF15803D))
        isTrial && (isTrialExpired || !isActive) -> ProgramBadgeInfo("ফ্রিতে শেখা শেষ", Color(0xFFFFF3E0), Color(0xFFE65100))
        isActive && !isTrial -> ProgramBadgeInfo("ভর্তি হয়েছো", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        isActive && isTrial -> ProgramBadgeInfo("ফ্রি ট্রায়াল", Color(0xFFE3F2FD), Color(0xFF1565C0))
        else -> ProgramBadgeInfo("প্রোগ্রাম", Color(0xFFF5F5F5), Color(0xFF616161))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSwitcherBottomSheet(
    enrolledPrograms: List<EnrolledProgram>,
    activeProgram: EnrolledProgram?,
    onSelectProgram: (EnrolledProgram) -> Unit,
    onDismiss: () -> Unit,
    onChangeSyllabusClick: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header — সেম
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("কোর্স সুইচ করো", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "তোমার শ্রেণির উপলব্ধ সকল কোর্সসমূহ:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.Close, "Close", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (enrolledPrograms.isEmpty()) {
                // Empty state — সেম structure, gradient CTA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }

                    Text("কোনো সক্রিয় কোর্স পাওয়া যায়নি", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    Text(
                        text = "তোমার বর্তমান শ্রেণি বা গ্রুপের জন্য কোর্সগুলো দেখতে সিলেবাস চেক করো অথবা সিলেবাস পরিবর্তন করো।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    if (onChangeSyllabusClick != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onChangeSyllabusClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("সিলেবাস বা শ্রেণি পরিবর্তন করো")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(enrolledPrograms, key = { it.id }) { program ->
                        val isSelected = activeProgram?.id == program.id
                        val badge = getProgramBadge(program)

                        // LOGIC: animated colors সেম
                        val animatedCardBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(200), label = "cardBg"
                        )
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            animationSpec = tween(200), label = "borderColor"
                        )

                        // NEW: press scale + spring check
                        val interaction = remember { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val cardScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.975f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "cardScale"
                        )
                        val checkScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "checkScale"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(interactionSource = interaction, indication = null) {
                                    onSelectProgram(program)
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = animatedCardBg,
                            border = BorderStroke(width = if (isSelected) 1.5.dp else 1.dp, color = animatedBorderColor)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Icon Box — NEW: gradient when selected
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.School, null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = badge.containerColor) {
                                        Text(
                                            text = badge.text,
                                            color = badge.textColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = program.title_bn ?: "একাডেমিক প্রোগ্রাম",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Selection Indicator — NEW: spring animated
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { scaleX = if (checkScale > 0.05f) 1f else 1f }
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, "Selected",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .graphicsLayer {
                                                    scaleX = checkScale
                                                    scaleY = checkScale
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
