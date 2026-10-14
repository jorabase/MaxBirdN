package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.ActivationResult
import com.example.security.ActivationSupportLink
import com.example.security.ActivationSupportRepository
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import com.example.security.DeviceStatusResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActivationScreen(
    onActivationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var accessCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isCopied by remember { mutableStateOf(false) }
    var isDeviceBanned by remember { mutableStateOf(false) }
    var banReasonMessage by remember { mutableStateOf("") }
    var copyResetJob by remember { mutableStateOf<Job?>(null) }

    val displayDeviceId = remember { DeviceSecurityManager.getDisplayDeviceId(context) }
    val isSupabaseConfigured = remember { DeviceActivationRepository.isConfigured() }

    // Dynamic support links fetched from Supabase with instant offline-first cache
    var supportLinks by remember {
        mutableStateOf<List<ActivationSupportLink>>(ActivationSupportRepository.getCachedLinks(context))
    }

    LaunchedEffect(Unit) {
        try {
            val freshLinks = ActivationSupportRepository.fetchSupportLinks(context)
            if (freshLinks.isNotEmpty()) {
                supportLinks = freshLinks
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("DeviceActivation", "Error loading fresh support links: ${t.message}")
        }

        // Check ban status on screen load & register device telemetry in database
        try {
            when (val status = DeviceActivationRepository.checkDeviceStatus(context)) {
                is DeviceStatusResult.Banned -> {
                    isDeviceBanned = true
                    banReasonMessage = status.reason
                    errorMessage = "এই ডিভাইসটি অ্যাডমিন দ্বারা ব্যান করা হয়েছে। কোনো এক্সেস কোড কাজ করবে না।"
                }
                else -> {
                    isDeviceBanned = false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("DeviceActivation", "Error checking device ban status: ${t.message}")
        }
    }

    // High security pulsing & rotating shield animations
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_shield_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val primaryAccent by animateColorAsState(
        targetValue = if (isDeviceBanned) Color(0xFFEF4444) else Color(0xFF38BDF8),
        animationSpec = tween(500),
        label = "primary_accent"
    )

    val secondaryAccent by animateColorAsState(
        targetValue = if (isDeviceBanned) Color(0xFF991B1B) else Color(0xFF2563EB),
        animationSpec = tween(500),
        label = "secondary_accent"
    )

    fun handleCopyId() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Device ID", displayDeviceId)
            clipboard.setPrimaryClip(clip)
            isCopied = true
            Toast.makeText(context, "ডিভাইস আইডি কপি হয়েছে!", Toast.LENGTH_SHORT).show()
            copyResetJob?.cancel()
            copyResetJob = coroutineScope.launch {
                delay(2200)
                isCopied = false
            }
        } catch (e: Exception) {
            Log.e("DeviceActivation", "Clipboard copy failed", e)
        }
    }

    fun handleShareId() {
        try {
            val shareText = "আমার MaxBird অ্যাপের ডিভাইস আইডি:\n$displayDeviceId\n\nঅনুগ্রহ করে আমাকে এক্সেস কোড প্রদান করুন।"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val chooserIntent = Intent.createChooser(sendIntent, "ডিভাইস আইডি শেয়ার করুন").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (_: Exception) {
            handleCopyId()
        }
    }

    fun handlePasteCode() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!item.isNullOrBlank()) {
                accessCodeInput = item.trim().uppercase()
                errorMessage = null
                Toast.makeText(context, "কোড পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "ক্লিপবোর্ডে কোনো কোড নেই", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "পেস্ট করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleOpenLink(link: ActivationSupportLink) {
        try {
            var targetUrl = link.url.trim()
            if (!targetUrl.startsWith("http://") &&
                !targetUrl.startsWith("https://") &&
                !targetUrl.startsWith("tel:") &&
                !targetUrl.startsWith("mailto:")
            ) {
                targetUrl = "https://$targetUrl"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceActivation", "Cannot open link: ${link.url}", e)
            handleCopyId()
            Toast.makeText(context, "লিংক ওপেন করা যায়নি। ডিভাইস আইডি কপি করা হয়েছে।", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleActivate() {
        if (isLoading) return

        if (isDeviceBanned) {
            errorMessage = "⚠️ এই ডিভাইসটি অ্যাডমিন দ্বারা ব্যান করা হয়েছে। কোনো এক্সেস কোড গ্রহণযোগ্য নয়।"
            return
        }

        val code = accessCodeInput.trim()
        if (code.isBlank()) {
            errorMessage = "দয়া করে এক্সেস কোডটি লিখুন।"
            return
        }

        try {
            keyboardController?.hide()
        } catch (_: Throwable) {}

        isLoading = true
        errorMessage = null
        successMessage = null

        coroutineScope.launch {
            try {
                if (!isSupabaseConfigured) {
                    isLoading = false
                    errorMessage = "Supabase কনফিগারেশন এখনো সংযুক্ত করা হয়নি। অনুগ্রহ করে GitHub Secrets এ SUPABASE_URL এবং SUPABASE_ANON_KEY যোগ করে নতুন বিল্ড করুন।"
                    return@launch
                }

                val result = DeviceActivationRepository.activateDevice(context, code)
                isLoading = false
                when (result) {
                    is ActivationResult.Success -> {
                        successMessage = result.message
                        delay(900)
                        try {
                            onActivationSuccess()
                        } catch (t: Throwable) {
                            Log.e("DeviceActivation", "Navigation error: ${t.message}", t)
                        }
                    }
                    is ActivationResult.Error -> {
                        errorMessage = result.message
                        if (result.isBanned) {
                            isDeviceBanned = true
                            banReasonMessage = result.message
                        }
                    }
                }
            } catch (e: CancellationException) {
                isLoading = false
                throw e
            } catch (t: Throwable) {
                Log.e("DeviceActivation", "Error in handleActivate: ${t.message}", t)
                isLoading = false
                errorMessage = "ত্রুটি ঘটেছে: ${t.localizedMessage ?: "দয়া করে আবার চেষ্টা করুন"}"
            }
        }
    }

    // Ultra-modern deep cyber-obsidian background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF040814),
            Color(0xFF091326),
            Color(0xFF050B16)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Ambient Background Glowing Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = 0.14f * glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(canvasWidth * 0.5f, canvasHeight * 0.12f),
                    radius = canvasWidth * 0.75f
                ),
                center = Offset(canvasWidth * 0.5f, canvasHeight * 0.12f),
                radius = canvasWidth * 0.75f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryAccent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(canvasWidth * 0.85f, canvasHeight * 0.75f),
                    radius = canvasWidth * 0.65f
                ),
                center = Offset(canvasWidth * 0.85f, canvasHeight * 0.75f),
                radius = canvasWidth * 0.65f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Cyber Security Shield Emblem with Rotating Arc & Multi-Layer Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(116.dp)
            ) {
                // Pulsing radial aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryAccent.copy(alpha = 0.32f),
                                    secondaryAccent.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Rotating futuristic cyber ring
                Canvas(
                    modifier = Modifier
                        .size(96.dp)
                        .rotate(ringRotation)
                ) {
                    val strokeWidth = 2.5.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryAccent.copy(alpha = 0.85f),
                                Color.Transparent,
                                secondaryAccent.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner Glassmorphic Shield Core
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0E1B33),
                    border = BorderStroke(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.9f),
                                secondaryAccent.copy(alpha = 0.4f)
                            )
                        )
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pulseScale * 0.98f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryAccent.copy(alpha = 0.2f),
                                    Color(0xFF0A1426)
                                )
                            )
                        )
                    ) {
                        Icon(
                            imageVector = if (isDeviceBanned) Icons.Filled.Lock else Icons.Filled.Shield,
                            contentDescription = "Security Shield",
                            tint = primaryAccent,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Security Status Badge Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDeviceBanned) Color(0xFF3B0D11).copy(alpha = 0.85f) else Color(0xFF10203B).copy(alpha = 0.85f),
                border = BorderStroke(
                    1.dp,
                    if (isDeviceBanned) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF38BDF8).copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isDeviceBanned) Color(0xFFEF4444) else Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDeviceBanned) "ডিভাইস অ্যাক্সেস ব্লকড" else "সুরক্ষিত ডিভাইস প্রোটেকশন",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDeviceBanned) Color(0xFFFCA5A5) else Color(0xFF7DD3FC),
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Title & Subtitle
            Text(
                text = "ডিভাইস সুরক্ষা ও এক্সেস কন্ট্রোল",
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "এই অ্যাপটি সম্পূর্ণ প্রাইভেট ও নিরাপদ। অনুমোদিত ডিভাইসে এক্সেস দেওয়ার জন্য অ্যাডমিনের দেওয়া সিক্রেট কোড প্রয়োজন।",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Quick 3-Step Visual Guide Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B1528).copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StepBadgeItem(number = "১", title = "আইডি কপি")
                    StepDividerLine()
                    StepBadgeItem(number = "২", title = "অ্যাডমিনকে সেন্ড")
                    StepDividerLine()
                    StepBadgeItem(number = "৩", title = "কোড অ্যাক্টিভ")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Unique Hardware Device ID Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0B1528).copy(alpha = 0.92f),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.4f),
                            Color(0xFF1E293B).copy(alpha = 0.8f),
                            Color(0xFF3B82F6).copy(alpha = 0.3f)
                        )
                    )
                ),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "আপনার অনন্য ডিভাইস আইডি",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = "HARDWARE ID",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Monospace ID Display Box with Interactive Copy Feedback
                    val idBoxBorderColor by animateColorAsState(
                        targetValue = if (isCopied) Color(0xFF10B981) else Color(0xFF253B5E),
                        label = "id_box_border"
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF050B16),
                        border = BorderStroke(1.2.dp, idBoxBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { handleCopyId() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayDeviceId,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCopied) Color(0xFF34D399) else Color(0xFF38BDF8),
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCopied) "ক্লিপবোর্ডে কপি করা হয়েছে" else "কপি করতে এখানে ট্যাপ করুন",
                                    fontSize = 10.sp,
                                    color = if (isCopied) Color(0xFF10B981) else Color(0xFF64748B)
                                )
                            }

                            // Animated copy status button (Preserving testTag)
                            IconButton(
                                onClick = { handleCopyId() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCopied) Color(0xFF065F46).copy(alpha = 0.5f)
                                        else Color(0xFF16243D)
                                    )
                                    .testTag("copy_device_id_button")
                            ) {
                                AnimatedContent(
                                    targetState = isCopied,
                                    transitionSpec = { scaleIn() + fadeIn() togetherWith fadeOut() },
                                    label = "copy_icon_anim"
                                ) { copied ->
                                    Icon(
                                        imageVector = if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                                        contentDescription = "Copy Device ID",
                                        tint = if (copied) Color(0xFF34D399) else Color(0xFF60A5FA),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row: Copy and Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { handleCopyId() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isCopied) Color(0xFF065F46) else Color(0xFF162540),
                                contentColor = if (isCopied) Color(0xFFA7F3D0) else Color(0xFF93C5FD)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCopied) "কপি হয়েছে!" else "আইডি কপি",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = { handleShareId() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF162540),
                                contentColor = Color(0xFF93C5FD)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "শেয়ার করুন",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "কোড সংগ্রহ করতে উপরের আইডিটি কপি করে অ্যাডমিনকে পাঠান",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3.5 Banned Device Alert Card (Shown if banned by admin)
            AnimatedVisibility(
                visible = isDeviceBanned,
                enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF23090C),
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFF991B1B))
                        )
                    ),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF7F1D1D).copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7F1D1D).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ডিভাইস ব্যান করা হয়েছে!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF87171)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (banReasonMessage.isNotBlank()) banReasonMessage else "এই ডিভাইসটি অ্যাডমিন প্যানেল থেকে স্থায়ীভাবে নিষিদ্ধ (Banned) করা হয়েছে। এই ফোনে কোনো ধরনের এক্সেস কোড চলবে না।",
                            fontSize = 13.sp,
                            color = Color(0xFFFECACA),
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF450A0A),
                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "স্ট্যাটাস: BANNED / স্থায়ীভাবে নিষিদ্ধ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF87171),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ব্যান তোলার জন্য নিচে অ্যাডমিনের সাথে যোগাযোগ করুন।",
                            fontSize = 11.5.sp,
                            color = Color(0xFFFCA5A5),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 4. Access Code Input Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0B1528).copy(alpha = 0.94f),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = if (isDeviceBanned) {
                            listOf(Color(0xFF991B1B), Color(0xFF450A0A))
                        } else {
                            listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.45f),
                                Color(0xFF1E293B),
                                Color(0xFF38BDF8).copy(alpha = 0.35f)
                            )
                        }
                    )
                ),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDeviceBanned) Color(0xFF7F1D1D).copy(alpha = 0.3f)
                                else Color(0xFF2563EB).copy(alpha = 0.2f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = if (isDeviceBanned) Color(0xFFF87171) else Color(0xFF60A5FA),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDeviceBanned) "এক্সেস কোড (ডিভাইস ব্যান থাকায় নিষ্ক্রিয়)" else "এক্সেস কোড লিখুন",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDeviceBanned) Color(0xFFF87171) else Color(0xFFE2E8F0)
                            )
                        }

                        // Quick Paste Chip Button
                        if (!isDeviceBanned) {
                            Surface(
                                onClick = { handlePasteCode() },
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF162540),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentPaste,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "পেস্ট করুন",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF93C5FD)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = accessCodeInput,
                        onValueChange = {
                            if (!isDeviceBanned) {
                                accessCodeInput = it.uppercase()
                                errorMessage = null
                            }
                        },
                        enabled = !isDeviceBanned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_code_input"),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        ),
                        placeholder = {
                            Text(
                                text = if (isDeviceBanned) "ডিভাইস ব্যান থাকায় কোনো কোড কাজ করবে না" else "যেমন: VIP-STUDENT-01",
                                color = if (isDeviceBanned) Color(0xFFEF4444) else Color(0xFF64748B),
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isDeviceBanned) Icons.Filled.Lock else Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (isDeviceBanned) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (accessCodeInput.isNotBlank() && !isDeviceBanned) {
                                IconButton(onClick = { accessCodeInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDeviceBanned) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            unfocusedBorderColor = if (isDeviceBanned) Color(0xFF7F1D1D) else Color(0xFF253755),
                            disabledBorderColor = Color(0xFF7F1D1D),
                            disabledTextColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF38BDF8),
                            focusedContainerColor = Color(0xFF050B16),
                            unfocusedContainerColor = Color(0xFF050B16),
                            disabledContainerColor = Color(0xFF1A0808)
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!isDeviceBanned) handleActivate() }
                        )
                    )

                    // Error Message Banner
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF3B1014),
                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFFECACA),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Success Message Banner
                    AnimatedVisibility(
                        visible = successMessage != null,
                        enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.95f),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF064E3B),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = successMessage.orEmpty(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Premium Gradient Activate Button (Preserving testTag)
                    val isButtonEnabled = !isLoading && !isDeviceBanned && successMessage == null
                    val buttonBrush = when {
                        isDeviceBanned -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF7F1D1D), Color(0xFF450A0A))
                        )
                        isButtonEnabled -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2563EB), Color(0xFF0284C7), Color(0xFF1D4ED8))
                        )
                        else -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1E3A8A), Color(0xFF172554))
                        )
                    }

                    Button(
                        onClick = { handleActivate() },
                        enabled = isButtonEnabled,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(
                                elevation = if (isButtonEnabled) 10.dp else 0.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Color(0xFF38BDF8),
                                spotColor = Color(0xFF2563EB)
                            )
                            .background(buttonBrush, RoundedCornerShape(14.dp))
                            .testTag("activate_device_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "যাচাই করা হচ্ছে...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else if (isDeviceBanned) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ডিভাইস ব্যান করা হয়েছে (নিষিদ্ধ)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "এক্সেস সক্রিয় করুন",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Contact Admin & Support Links Section
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0A1324).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF1E2E4A)),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF162540),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "কোড পেতে অ্যাডমিনের সাথে যোগাযোগ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF1F5F9)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "আপনার ডিভাইস আইডি পাঠিয়ে যেকোনো মাধ্যমে কোড নিন",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // List of dynamic contact links configured from Admin app
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        supportLinks.forEach { link ->
                            SupportLinkCard(
                                link = link,
                                onClick = { handleOpenLink(link) }
                            )
                        }
                    }
                }
            }

            // Extra space at bottom to prevent navigation bar clipping
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepBadgeItem(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E3A8A).copy(alpha = 0.6f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7DD3FC)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFCBD5E1)
        )
    }
}

@Composable
private fun StepDividerLine() {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(1.dp)
            .background(Color(0xFF334155))
    )
}

/**
 * Modern, un-squished support link card with custom brand accents and glassmorphic depth.
 */
@Composable
private fun SupportLinkCard(
    link: ActivationSupportLink,
    onClick: () -> Unit
) {
    val brandColor = remember(link.iconType, link.colorHex) {
        if (!link.colorHex.isNullOrBlank()) {
            try {
                val cleanHex = link.colorHex.trim().let {
                    if (it.startsWith("#")) it else "#$it"
                }
                Color(android.graphics.Color.parseColor(cleanHex))
            } catch (_: Exception) {
                getBrandColorForType(link.iconType)
            }
        } else {
            getBrandColorForType(link.iconType)
        }
    }

    val iconVector: ImageVector = remember(link.iconType) {
        getBrandIconForType(link.iconType)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF060E1C),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    brandColor.copy(alpha = 0.5f),
                    brandColor.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_link_${link.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            brandColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Brand Icon Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = brandColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, brandColor.copy(alpha = 0.45f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = link.title,
                            tint = brandColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = link.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!link.subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = link.subtitle,
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Action arrow icon
            Surface(
                shape = CircleShape,
                color = brandColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, brandColor.copy(alpha = 0.3f)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = brandColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private fun getBrandColorForType(type: String): Color {
    return when (type.lowercase().trim()) {
        "telegram" -> Color(0xFF229ED9)
        "whatsapp" -> Color(0xFF25D366)
        "facebook" -> Color(0xFF1877F2)
        "phone", "call" -> Color(0xFFF59E0B)
        "website", "web" -> Color(0xFF38BDF8)
        else -> Color(0xFF60A5FA)
    }
}

private fun getBrandIconForType(type: String): ImageVector {
    return when (type.lowercase().trim()) {
        "telegram" -> Icons.AutoMirrored.Filled.Send
        "whatsapp" -> Icons.Filled.OpenInNew
        "phone", "call" -> Icons.Filled.Phone
        "website", "web" -> Icons.Filled.Language
        else -> Icons.Filled.OpenInNew
    }
}
