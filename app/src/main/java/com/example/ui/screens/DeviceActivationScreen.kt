package com.example.ui.screens

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.ActivationSupportLink
import com.example.security.ActivationSupportRepository
import com.example.security.DeviceActivationRepository
import com.example.security.StudentVerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class FireflyParticle(
    val baseX: Float,
    val baseY: Float,
    val radiusDp: Float,
    val speedFactor: Float,
    val phaseOffset: Float,
    val driftAmplitudeX: Float,
    val isGolden: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActivationScreen(
    onActivationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var studentNameInput by remember {
        mutableStateOf(DeviceActivationRepository.getSavedStudentName(context))
    }
    var isInitialChecking by remember { mutableStateOf(true) }
    var isSubmittingOrRefreshing by remember { mutableStateOf(false) }
    var verificationStatus by remember {
        mutableStateOf<StudentVerificationStatus>(
            if (DeviceActivationRepository.isPendingRequestedLocally(context)) {
                StudentVerificationStatus.Pending(
                    studentName = DeviceActivationRepository.getSavedStudentName(context)
                )
            } else {
                StudentVerificationStatus.NotRequested
            }
        )
    }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }

    val isSupabaseConfigured = remember { DeviceActivationRepository.isConfigured() }

    // Dynamic support links fetched from Supabase with instant offline-first cache
    var supportLinks by remember {
        mutableStateOf<List<ActivationSupportLink>>(ActivationSupportRepository.getCachedLinks(context))
    }

    fun performStatusCheck(showFeedbackToast: Boolean = false) {
        if (isSubmittingOrRefreshing) return
        coroutineScope.launch {
            try {
                if (showFeedbackToast) {
                    isSubmittingOrRefreshing = true
                    statusBannerMessage = null
                }
                val result = DeviceActivationRepository.checkStudentVerificationStatus(context)
                verificationStatus = result
                isInitialChecking = false
                isSubmittingOrRefreshing = false

                if (showFeedbackToast) {
                    when (result) {
                        is StudentVerificationStatus.Approved -> {
                            statusBannerMessage = "অভিনন্দন! আপনার স্টুডেন্ট ভেরিফিকেশন সম্পন্ন হয়েছে।"
                        }
                        is StudentVerificationStatus.Pending -> {
                            statusBannerMessage = "আপনার ভেরিফিকেশন এখনো প্রক্রিয়াধীন রয়েছে। অনুগ্রহ করে অপেক্ষা করুন।"
                        }
                        is StudentVerificationStatus.Blocked -> {
                            statusBannerMessage = null
                        }
                        is StudentVerificationStatus.Error -> {
                            statusBannerMessage = result.message
                        }
                        is StudentVerificationStatus.NotRequested -> {
                            statusBannerMessage = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("StudentVerification", "Error checking status: ${t.message}")
                isInitialChecking = false
                isSubmittingOrRefreshing = false
            }
        }
    }

    fun handleRequestVerification() {
        if (isSubmittingOrRefreshing) return
        try {
            keyboardController?.hide()
        } catch (_: Throwable) {}

        if (!isSupabaseConfigured) {
            verificationStatus = StudentVerificationStatus.Error("সার্ভার সংযোগ কনফিগার করা হয়নি।")
            return
        }

        isSubmittingOrRefreshing = true
        statusBannerMessage = null

        coroutineScope.launch {
            try {
                val result = DeviceActivationRepository.submitStudentVerificationRequest(
                    context = context,
                    studentName = studentNameInput.trim()
                )
                isSubmittingOrRefreshing = false
                verificationStatus = result
                if (result is StudentVerificationStatus.Pending) {
                    statusBannerMessage = "স্বাগতম! আপনার ভেরিফিকেশন আবেদন সফলভাবে গৃহীত হয়েছে।"
                }
            } catch (e: CancellationException) {
                isSubmittingOrRefreshing = false
                throw e
            } catch (t: Throwable) {
                isSubmittingOrRefreshing = false
                verificationStatus = StudentVerificationStatus.Error("সংযোগ সমস্যা হয়েছে, অনুগ্রহ করে আবার চেষ্টা করুন।")
            }
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
            Log.e("StudentVerification", "Cannot open link: ${link.url}", e)
            Toast.makeText(context, "লিংক ওপেন করা যায়নি।", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        // Load support links silently
        launch {
            try {
                val freshLinks = ActivationSupportRepository.fetchSupportLinks(context)
                if (freshLinks.isNotEmpty()) {
                    supportLinks = freshLinks
                }
            } catch (_: Throwable) {}
        }
        // Check device verification status once on screen open (handles reinstall auto-restore & pending check)
        performStatusCheck(showFeedbackToast = false)
    }

    val isBlocked = verificationStatus is StudentVerificationStatus.Blocked
    val isApproved = verificationStatus is StudentVerificationStatus.Approved
    val isPending = verificationStatus is StudentVerificationStatus.Pending

    // Infinite Nature Animations (Wind sway, fireflies, sunbeams, emblem pulse)
    val infiniteTransition = rememberInfiniteTransition(label = "enchanted_forest_transition")

    val natureTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nature_time"
    )

    val fireflyProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "firefly_progress"
    )

    val sunbeamBreath by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sunbeam_breath"
    )

    val emblemPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emblem_pulse"
    )

    val ringRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotate"
    )

    val primaryForestAccent by animateColorAsState(
        targetValue = when {
            isBlocked -> Color(0xFFF87171)
            isApproved -> Color(0xFF4ADE80)
            isPending -> Color(0xFFFACC15)
            else -> Color(0xFF34D399)
        },
        animationSpec = tween(500),
        label = "primary_forest_accent"
    )

    val secondaryForestAccent by animateColorAsState(
        targetValue = when {
            isBlocked -> Color(0xFFDC2626)
            isApproved -> Color(0xFF10B981)
            isPending -> Color(0xFF34D399)
            else -> Color(0xFFA3E635)
        },
        animationSpec = tween(500),
        label = "secondary_forest_accent"
    )

    val fireflies = remember {
        List(26) { idx ->
            FireflyParticle(
                baseX = ((idx * 37 + 13) % 96) / 100f + 0.02f,
                baseY = ((idx * 53 + 29) % 90) / 100f + 0.05f,
                radiusDp = 2.2f + (idx % 4) * 1.1f,
                speedFactor = 0.6f + (idx % 5) * 0.25f,
                phaseOffset = (idx * 0.75f),
                driftAmplitudeX = 0.025f + (idx % 3) * 0.015f,
                isGolden = idx % 2 == 0
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF031812))
    ) {
        // 1. Full-Bleed Animated Enchanted Forest Scene Canvas
        EnchantedForestBackdrop(
            natureTime = natureTime,
            fireflyProgress = fireflyProgress,
            sunbeamAlpha = sunbeamBreath,
            fireflies = fireflies,
            isBlocked = isBlocked,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Foreground Scrollable Content with Safe Insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Animated Enchanted Forest Emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(124.dp)
            ) {
                // Soft bioluminescent aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(emblemPulse)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryForestAccent.copy(alpha = 0.34f),
                                    secondaryForestAccent.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Rotating botanical golden-emerald halo ring
                Canvas(
                    modifier = Modifier
                        .size(104.dp)
                        .rotate(ringRotate)
                ) {
                    val strokeWidth = 2.5.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryForestAccent.copy(alpha = 0.9f),
                                secondaryForestAccent.copy(alpha = 0.8f),
                                Color.Transparent,
                                primaryForestAccent.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner Forest Glass Orb
                Surface(
                    shape = CircleShape,
                    color = if (isBlocked) Color(0xFF2A0F12) else Color(0xFF07291E),
                    border = BorderStroke(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryForestAccent.copy(alpha = 0.95f),
                                secondaryForestAccent.copy(alpha = 0.45f)
                            )
                        )
                    ),
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .size(82.dp)
                        .scale(emblemPulse * 0.98f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryForestAccent.copy(alpha = 0.25f),
                                    if (isBlocked) Color(0xFF1F090C) else Color(0xFF041D15)
                                )
                            )
                        )
                    ) {
                        Icon(
                            imageVector = when {
                                isBlocked -> Icons.Filled.Lock
                                isApproved -> Icons.Filled.VerifiedUser
                                isPending -> Icons.Filled.Spa
                                else -> Icons.Filled.Park
                            },
                            contentDescription = "Forest Emblem",
                            tint = primaryForestAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Serene Forest Pill Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isBlocked) Color(0xFF3B0D11).copy(alpha = 0.85f) else Color(0xFF082E22).copy(alpha = 0.85f),
                border = BorderStroke(
                    1.dp,
                    primaryForestAccent.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Filled.ErrorOutline else Icons.Filled.Eco,
                        contentDescription = null,
                        tint = primaryForestAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = when {
                            isBlocked -> "প্রবেশাধিকার স্থগিত"
                            isApproved -> "ভেরিফাইড স্টুডেন্ট পোর্টাল"
                            isPending -> "ভেরিফিকেশন প্রক্রিয়াধীন"
                            else -> "শান্ত ও আনন্দময় শিক্ষার ভুবন"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Welcoming Title & Subtitle
            Text(
                text = when {
                    isBlocked -> "প্রবেশাধিকার সাময়িকভাবে বন্ধ"
                    isApproved -> "স্বাগতম! ভেরিফিকেশন সম্পন্ন"
                    isPending -> "স্বাগতম! একটু অপেক্ষা করুন"
                    else -> "স্বাগতম! শিক্ষার সবুজ ভুবনে"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 0.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    isBlocked -> "এই ডিভাইস থেকে বর্তমানে অ্যাপে প্রবেশের অনুমতি স্থগিত রয়েছে।"
                    isApproved -> "আপনার ডিভাইসটি ইতিমধ্যে স্টুডেন্ট হিসেবে ভেরিফাইড রয়েছে। নিচের বাটনে ট্যাপ করে সরাসরি প্রবেশ করুন।"
                    isPending -> "আপনার স্টুডেন্ট ভেরিফিকেশন আবেদন গ্রহণ করা হয়েছে। ভেরিফিকেশন সম্পন্ন হলেই আপনি অ্যাপে প্রবেশ করতে পারবেন।"
                    else -> "আমাদের বিশেষ লার্নিং প্ল্যাটফর্মে যুক্ত হতে নিচে এক ক্লিকে আপনার স্টুডেন্ট ভেরিফিকেশন সম্পন্ন করুন।"
                },
                fontSize = 13.5.sp,
                color = Color(0xFFD1FAE5).copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Main Interactive Card (Animated Transitions between Checking / NotRequested / Pending / Approved / Blocked)
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = if (isBlocked) Color(0xFF1F0A0D).copy(alpha = 0.92f) else Color(0xFF06231A).copy(alpha = 0.90f),
                border = BorderStroke(
                    width = 1.4.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryForestAccent.copy(alpha = 0.55f),
                            Color(0xFF0F3D2E).copy(alpha = 0.7f),
                            secondaryForestAccent.copy(alpha = 0.4f)
                        )
                    )
                ),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryForestAccent.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = Pair(isInitialChecking, verificationStatus),
                        transitionSpec = {
                            (fadeIn(tween(320)) + scaleIn(initialScale = 0.96f)) togetherWith
                                fadeOut(tween(220))
                        },
                        label = "verification_state_card"
                    ) { (checking, status) ->
                        when {
                            checking -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF34D399),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "স্টুডেন্ট ভেরিফিকেশন যাচাই করা হচ্ছে...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFA7F3D0),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            status is StudentVerificationStatus.Approved -> {
                                // Already Verified / Reinstalled Device -> Immediate "প্রবেশ করুন" Button!
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF065F46).copy(alpha = 0.6f),
                                        border = BorderStroke(1.5.dp, Color(0xFF34D399)),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4ADE80),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "স্টুডেন্ট ভেরিফিকেশন সক্রিয় আছে!",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFECFDF5),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "আপনার এই ডিভাইসটি ভেরিফাইড রয়েছে। নিচের বাটনে ট্যাপ করে সরাসরি আপনার লার্নিং ড্যাশবোর্ডে প্রবেশ করুন।",
                                        fontSize = 13.5.sp,
                                        color = Color(0xFFA7F3D0),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    val enterBrush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF10B981),
                                            Color(0xFF059669),
                                            Color(0xFF16A34A)
                                        )
                                    )

                                    Button(
                                        onClick = { onActivationSuccess() },
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 12.dp,
                                                shape = RoundedCornerShape(16.dp),
                                                ambientColor = Color(0xFF34D399),
                                                spotColor = Color(0xFF10B981)
                                            )
                                            .background(enterBrush, RoundedCornerShape(16.dp))
                                            .testTag("activate_device_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Login,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "প্রবেশ করুন",
                                                fontSize = 16.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            status is StudentVerificationStatus.Pending -> {
                                // Pending Verification State -> Welcomes student & shows 1-5 mins / up to 24 hours message
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF064E3B).copy(alpha = 0.7f),
                                        border = BorderStroke(1.5.dp, Color(0xFFFACC15).copy(alpha = 0.8f)),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.HourglassTop,
                                                contentDescription = null,
                                                tint = Color(0xFFFDE047),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "স্বাগতম! আপনার আবেদন গৃহীত হয়েছে 🌿",
                                        fontSize = 18.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Highlighted Wait Time Notice Card (1-5 minutes, sometimes 24 hours)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF041A13),
                                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AccessTime,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFDE047),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "১–৫ মিনিট অপেক্ষা করুন",
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFFDE047)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = "সাধারণত ১ থেকে ৫ মিনিটের মধ্যেই স্টুডেন্ট ভেরিফিকেশন সম্পন্ন হয়ে যায়। তবে কখনো কখনো সর্বোচ্চ ২৪ ঘণ্টা সময় লাগতে পারে।",
                                                fontSize = 13.sp,
                                                color = Color(0xFFD1FAE5),
                                                textAlign = TextAlign.Center,
                                                lineHeight = 19.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "ভেরিফিকেশন সম্পন্ন হলে নিচের বাটনে ট্যাপ করুন অথবা অ্যাপটি পুনরায় ওপেন করলেই সরাসরি প্রবেশ করতে পারবেন।",
                                        fontSize = 12.sp,
                                        color = Color(0xFFA7F3D0).copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )

                                    if (statusBannerMessage != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF064E3B),
                                            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = statusBannerMessage.orEmpty(),
                                                fontSize = 12.5.sp,
                                                color = Color(0xFFECFDF5),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    val refreshBrush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF059669),
                                            Color(0xFF10B981),
                                            Color(0xFF0D9488)
                                        )
                                    )

                                    Button(
                                        onClick = { performStatusCheck(showFeedbackToast = true) },
                                        enabled = !isSubmittingOrRefreshing,
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .shadow(
                                                elevation = 10.dp,
                                                shape = RoundedCornerShape(16.dp),
                                                ambientColor = Color(0xFF34D399)
                                            )
                                            .background(refreshBrush, RoundedCornerShape(16.dp))
                                            .testTag("activate_device_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isSubmittingOrRefreshing) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "যাচাই করা হচ্ছে...",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "স্ট্যাটাস রিফ্রেশ করুন",
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            status is StudentVerificationStatus.Blocked -> {
                                // Blocked Device State
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF7F1D1D).copy(alpha = 0.6f),
                                        border = BorderStroke(1.5.dp, Color(0xFFF87171)),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFFCA5A5),
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "ডিভাইসটির প্রবেশাধিকার বন্ধ রয়েছে",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFCA5A5),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = status.reason.ifBlank {
                                            "নিরাপত্তা বা শর্তাবলী ভঙ্গের কারণে এই ডিভাইসে অ্যাপ ব্যবহারের অনুমতি স্থগিত করা হয়েছে।"
                                        },
                                        fontSize = 13.sp,
                                        color = Color(0xFFFECACA),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 19.sp
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    OutlinedButton(
                                        onClick = { performStatusCheck(showFeedbackToast = true) },
                                        enabled = !isSubmittingOrRefreshing,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "পুনরায় যাচাই করুন",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                }
                            }

                            else -> {
                                // Default New User State -> One-Click "স্টুডেন্ট ভেরিফিকেশন করুন"
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFFDE047),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "এক ক্লিকেই স্টুডেন্ট ভেরিফিকেশন",
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFECFDF5)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "কোনো কোড বা ঝামেলার প্রয়োজন নেই! নিচে আপনার নাম লিখে বা সরাসরি বাটনে ক্লিক করে স্টুডেন্ট ভেরিফিকেশন সম্পন্ন করুন।",
                                        fontSize = 12.8.sp,
                                        color = Color(0xFFA7F3D0),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.5.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Optional Student Name Input
                                    OutlinedTextField(
                                        value = studentNameInput,
                                        onValueChange = { studentNameInput = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("activation_code_input"),
                                        placeholder = {
                                            Text(
                                                text = "আপনার নাম লিখুন (ঐচ্ছিক)",
                                                color = Color(0xFF6EE7B7).copy(alpha = 0.55f),
                                                fontSize = 13.5.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(15.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF34D399),
                                            unfocusedBorderColor = Color(0xFF065F46),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color(0xFF34D399),
                                            focusedContainerColor = Color(0xFF031710),
                                            unfocusedContainerColor = Color(0xFF031710)
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { handleRequestVerification() }
                                        )
                                    )

                                    if (status is StudentVerificationStatus.Error) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF3B1014),
                                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF87171),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = status.message,
                                                    fontSize = 12.5.sp,
                                                    color = Color(0xFFFECACA)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    val verifyButtonBrush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF059669),
                                            Color(0xFF10B981),
                                            Color(0xFF16A34A)
                                        )
                                    )

                                    Button(
                                        onClick = { handleRequestVerification() },
                                        enabled = !isSubmittingOrRefreshing,
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 12.dp,
                                                shape = RoundedCornerShape(16.dp),
                                                ambientColor = Color(0xFF34D399),
                                                spotColor = Color(0xFF10B981)
                                            )
                                            .background(verifyButtonBrush, RoundedCornerShape(16.dp))
                                            .testTag("activate_device_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isSubmittingOrRefreshing) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "ভেরিফিকেশন পাঠানো হচ্ছে...",
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.VerifiedUser,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "স্টুডেন্ট ভেরিফিকেশন করুন",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
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

            // Friendly Help / Support Section (only if support links are configured)
            AnimatedVisibility(
                visible = supportLinks.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF051E16).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color(0xFF065F46).copy(alpha = 0.65f)),
                        shadowElevation = 8.dp,
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
                                    color = Color(0xFF064E3B),
                                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f)),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.SupportAgent,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "সহযোগিতা ও স্টুডেন্ট সাপোর্ট",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFECFDF5)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "যেকোনো প্রয়োজনে আমাদের হেল্পডেস্কে বার্তা পাঠাতে পারেন",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFA7F3D0).copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                supportLinks.forEach { link ->
                                    ForestSupportLinkCard(
                                        link = link,
                                        onClick = { handleOpenLink(link) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Full-screen procedural animated Enchanted Forest Canvas:
 * - Twilight emerald sky with golden sunbeams filtering through the canopy
 * - Glowing moon/sun orb over distant misty mountains
 * - Multi-layered rolling forest hills & swaying pine trees
 * - Peaceful gliding birds & 26 glowing golden-emerald fireflies
 */
@Composable
private fun EnchantedForestBackdrop(
    natureTime: Float,
    fireflyProgress: Float,
    sunbeamAlpha: Float,
    fireflies: List<FireflyParticle>,
    isBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val skyColors = if (isBlocked) {
        listOf(
            Color(0xFF1A0608),
            Color(0xFF2B0C10),
            Color(0xFF140507)
        )
    } else {
        listOf(
            Color(0xFF021610),
            Color(0xFF062C20),
            Color(0xFF0B3B2B),
            Color(0xFF031912)
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Sky Background Gradient
        drawRect(
            brush = Brush.verticalGradient(colors = skyColors),
            size = size
        )

        // 2. Soft Golden-Emerald Sunbeams / Canopy Light Rays from Top-Center
        val rayOrigin = Offset(w * 0.5f, -h * 0.08f)
        val rayColor = if (isBlocked) Color(0xFFF87171) else Color(0xFFA7F3D0)
        for (i in -2..2) {
            val spread = i * 0.24f
            val rayPath = Path().apply {
                moveTo(rayOrigin.x, rayOrigin.y)
                lineTo(w * (0.35f + spread), h * 0.72f)
                lineTo(w * (0.65f + spread), h * 0.72f)
                close()
            }
            drawPath(
                path = rayPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        rayColor.copy(alpha = 0.07f * sunbeamAlpha),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.72f
                )
            )
        }

        // 3. Luminous Forest Moon / Sun Halo in Upper Sky
        val orbCenter = Offset(w * 0.82f, h * 0.14f)
        val orbColor = if (isBlocked) Color(0xFFEF4444) else Color(0xFFFDE047)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    orbColor.copy(alpha = 0.22f * sunbeamAlpha),
                    orbColor.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = orbCenter,
                radius = w * 0.35f
            ),
            center = orbCenter,
            radius = w * 0.35f
        )
        drawCircle(
            color = orbColor.copy(alpha = 0.28f),
            center = orbCenter,
            radius = w * 0.06f
        )

        // 4. Distant Misty Mountains & Layered Rolling Forest Hills
        drawRollingHillLayer(
            w = w,
            h = h,
            baseYRatio = 0.68f,
            amplitudeRatio = 0.055f,
            phase = 0.4f,
            color = if (isBlocked) Color(0xFF280B0E).copy(alpha = 0.7f) else Color(0xFF083526).copy(alpha = 0.75f)
        )
        drawRollingHillLayer(
            w = w,
            h = h,
            baseYRatio = 0.76f,
            amplitudeRatio = 0.045f,
            phase = 2.1f,
            color = if (isBlocked) Color(0xFF1F080A).copy(alpha = 0.85f) else Color(0xFF05271C).copy(alpha = 0.88f)
        )
        drawRollingHillLayer(
            w = w,
            h = h,
            baseYRatio = 0.85f,
            amplitudeRatio = 0.035f,
            phase = 4.2f,
            color = if (isBlocked) Color(0xFF140406) else Color(0xFF031A12)
        )

        // 5. Swaying Pine & Forest Trees along Left & Right Edges
        val swayOffset = sin(natureTime) * 6.dp.toPx()
        val treePositions = listOf(
            Triple(0.06f, 0.78f, 0.16f),
            Triple(0.15f, 0.82f, 0.13f),
            Triple(0.24f, 0.85f, 0.10f),
            Triple(0.76f, 0.84f, 0.11f),
            Triple(0.86f, 0.81f, 0.14f),
            Triple(0.94f, 0.77f, 0.17f)
        )
        val treeColorBack = if (isBlocked) Color(0xFF24080B) else Color(0xFF042218)
        val treeColorFront = if (isBlocked) Color(0xFF170406) else Color(0xFF02140E)

        treePositions.forEachIndexed { index, (xRatio, baseYRatio, heightRatio) ->
            val treeColor = if (index % 2 == 0) treeColorFront else treeColorBack
            drawForestPineTree(
                centerX = w * xRatio + swayOffset * (if (index % 2 == 0) 1f else 0.6f),
                baseY = h * baseYRatio,
                treeHeight = h * heightRatio,
                treeWidth = w * 0.14f,
                sway = swayOffset * 0.5f,
                color = treeColor
            )
        }

        // 6. Peaceful Gliding Birds in Upper Canopy
        if (!isBlocked) {
            for (b in 0..3) {
                val birdProgress = (fireflyProgress * 0.8f + b * 0.18f) % 1f
                val bx = w * (0.1f + birdProgress * 0.8f)
                val by = h * (0.16f + (b % 2) * 0.035f + sin(natureTime * 2f + b) * 0.008f)
                val wingFlap = sin(natureTime * 4f + b * 1.3f) * 4.dp.toPx()
                val birdSpan = 7.dp.toPx()

                val birdPath = Path().apply {
                    moveTo(bx - birdSpan, by - wingFlap)
                    quadraticBezierTo(bx - birdSpan * 0.4f, by - 3.dp.toPx(), bx, by)
                    quadraticBezierTo(bx + birdSpan * 0.4f, by - 3.dp.toPx(), bx + birdSpan, by - wingFlap)
                }
                drawPath(
                    path = birdPath,
                    color = Color(0xFFA7F3D0).copy(alpha = 0.45f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 7. Magical Glowing Fireflies (জোনাকি পোকা) Drifting Across the Forest
        fireflies.forEach { fly ->
            val currentYRatio = (fly.baseY - fireflyProgress * fly.speedFactor).let { raw ->
                val mod = raw % 1f
                if (mod < 0f) mod + 1f else mod
            }
            val waveX = sin(natureTime * fly.speedFactor + fly.phaseOffset) * fly.driftAmplitudeX
            val fx = (fly.baseX + waveX).coerceIn(0.02f, 0.98f) * w
            val fy = currentYRatio * h

            val twinkle = ((sin(natureTime * 2f + fly.phaseOffset) + 1f) * 0.5f).coerceIn(0.2f, 1f)
            val coreColor = when {
                isBlocked -> Color(0xFFF87171)
                fly.isGolden -> Color(0xFFFDE047)
                else -> Color(0xFF6EE7B7)
            }

            val rPx = fly.radiusDp.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.55f * twinkle),
                        coreColor.copy(alpha = 0.15f * twinkle),
                        Color.Transparent
                    ),
                    center = Offset(fx, fy),
                    radius = rPx * 4.2f
                ),
                center = Offset(fx, fy),
                radius = rPx * 4.2f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * twinkle),
                center = Offset(fx, fy),
                radius = rPx * 0.7f
            )
        }
    }
}

private fun DrawScope.drawRollingHillLayer(
    w: Float,
    h: Float,
    baseYRatio: Float,
    amplitudeRatio: Float,
    phase: Float,
    color: Color
) {
    val path = Path().apply {
        val startY = h * baseYRatio
        moveTo(0f, h)
        lineTo(0f, startY)
        val steps = 32
        for (i in 1..steps) {
            val x = (w * i) / steps
            val angle = (i.toFloat() / steps) * PI.toFloat() * 2f + phase
            val y = startY + sin(angle) * (h * amplitudeRatio)
            lineTo(x, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawForestPineTree(
    centerX: Float,
    baseY: Float,
    treeHeight: Float,
    treeWidth: Float,
    sway: Float,
    color: Color
) {
    // 3 tiered pine canopy triangles
    for (tier in 0..2) {
        val tierTopY = baseY - treeHeight * (1f - tier * 0.24f)
        val tierBottomY = baseY - treeHeight * (0.55f - tier * 0.25f)
        val tierHalfWidth = (treeWidth * 0.5f) * (0.65f + tier * 0.22f)
        val tierSway = sway * (1f - tier * 0.25f)

        val tierPath = Path().apply {
            moveTo(centerX + tierSway, tierTopY)
            lineTo(centerX - tierHalfWidth, tierBottomY)
            lineTo(centerX + tierHalfWidth, tierBottomY)
            close()
        }
        drawPath(path = tierPath, color = color)
    }
}

@Composable
private fun ForestSupportLinkCard(
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
                getForestBrandColorForType(link.iconType)
            }
        } else {
            getForestBrandColorForType(link.iconType)
        }
    }

    val iconVector: ImageVector = remember(link.iconType) {
        getForestBrandIconForType(link.iconType)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF03150F),
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
                            brandColor.copy(alpha = 0.10f),
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = brandColor.copy(alpha = 0.18f),
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
                            color = Color(0xFFA7F3D0).copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

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

private fun getForestBrandColorForType(type: String): Color {
    return when (type.lowercase().trim()) {
        "telegram" -> Color(0xFF38BDF8)
        "whatsapp" -> Color(0xFF4ADE80)
        "facebook" -> Color(0xFF60A5FA)
        "phone", "call" -> Color(0xFFFBBF24)
        "website", "web" -> Color(0xFF34D399)
        else -> Color(0xFF34D399)
    }
}

private fun getForestBrandIconForType(type: String): ImageVector {
    return when (type.lowercase().trim()) {
        "telegram" -> Icons.AutoMirrored.Filled.Send
        "whatsapp" -> Icons.Filled.OpenInNew
        "phone", "call" -> Icons.Filled.Phone
        "website", "web" -> Icons.Filled.Language
        else -> Icons.Filled.OpenInNew
    }
}
