package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.api.AcademicSubjectItem
import com.example.utils.SubjectColorUtils
import com.example.utils.SubjectIconBadge
import com.example.utils.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectFilterDialog(
    courseTitle: String,
    subjects: List<AcademicSubjectItem>,
    selectedSubjectCodes: Set<String>,
    isLoading: Boolean,
    isSaving: Boolean,
    onToggleSubject: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalCount = subjects.size
    val selectedCount = selectedSubjectCodes.size
    val primary = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 1. Header — NEW: gradient Tune badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(primary, MaterialTheme.colorScheme.secondary))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Filter Subjects", "সাবজেক্ট সাজাও"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = courseTitle.ifBlank { tr("Selected Course", "সিলেক্টেড কোর্স") },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Close", "বন্ধ করুন"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(
                                "Only classes from selected subjects will appear in your routine.",
                                "যে সাবজেক্টগুলো সিলেক্ট করবেন শুধু সেগুলোর রুটিন দেখাবে।"
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Quick Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCount == 0) {
                            tr("All subjects (Default)", "সব বিষয় সিলেক্টেড (ডিফল্ট)")
                        } else {
                            tr(
                                "${selectedCount.toString().toBengaliDigits()}/${totalCount.toString().toBengaliDigits()} selected",
                                "${selectedCount.toString().toBengaliDigits()}/${totalCount.toString().toBengaliDigits()} টি বিষয় নির্বাচিত"
                            )
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onSelectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FilterList, null, tint = primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("All", "সবগুলো"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
                        }

                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(tr("Reset", "রিসেট"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // 3. Subject Grid Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(36.dp), color = primary)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(tr("Loading subjects...", "সাবজেক্ট লোড হচ্ছে..."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        subjects.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("No subjects found for this course.", "এই কোর্সের জন্য কোনো সাবজেক্ট পাওয়া যায়নি।"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(subjects, key = { it.code ?: it.display_bn ?: "" }) { subject ->
                                    val code = subject.code ?: subject.display_bn ?: ""
                                    val isSelected = selectedSubjectCodes.contains(code)
                                    val subjectName = subject.display_bn ?: subject.code ?: tr("Subject", "বিষয়")
                                    val colors = SubjectColorUtils.getColorScheme(subjectName)

                                    // NEW: spring checkmark animation
                                    val checkScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "checkScale$code"
                                    )

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onToggleSubject(code) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) colors.backgroundColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) colors.textColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                SubjectIconBadge(
                                                    iconUrl = subject.icon,
                                                    subjectName = subjectName,
                                                    subjectCode = code,
                                                    color = colors.textColor,
                                                    size = 32.dp,
                                                    iconSize = 18.dp
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Text(
                                                    text = subjectName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) colors.textColor else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                                if (!isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = "Unselected",
                                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                // Animated checkmark
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colors.textColor,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .graphicsLayer {
                                                            scaleX = checkScale
                                                            scaleY = checkScale
                                                            alpha = checkScale
                                                        }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(3.dp)
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

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Bottom Buttons — NEW: gradient save button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isSaving) onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(tr("Cancel", "বাতিল"), fontSize = 14.sp)
                    }

                    // Gradient Save
                    val saveInteraction = remember { MutableInteractionSource() }
                    val savePressed by saveInteraction.collectIsPressedAsState()
                    val saveScale by animateFloatAsState(
                        targetValue = if (savePressed) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "saveScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .graphicsLayer { scaleX = saveScale; scaleY = saveScale }
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSaving) Brush.horizontalGradient(
                                    listOf(primary.copy(alpha = 0.5f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                ) else Brush.horizontalGradient(listOf(primary, MaterialTheme.colorScheme.secondary))
                            )
                            .clickable(
                                interactionSource = saveInteraction,
                                indication = null,
                                enabled = !isSaving
                            ) { onSave() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("Saving...", "সেভ হচ্ছে..."), fontSize = 14.sp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Save", "সেভ করুন"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
