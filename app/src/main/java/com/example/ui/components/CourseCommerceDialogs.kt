package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.course.AdminPaymentSettings
import com.example.course.CourseAccessState
import com.example.course.CourseCommerceItemState
import com.example.course.InstallmentPlanOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePurchasePaymentBottomSheet(
    courseTitle: String,
    commerceState: CourseCommerceItemState,
    paymentSettings: AdminPaymentSettings,
    defaultUserPhone: String = "",
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitPayment: (
        paymentMethod: String,
        senderMobile: String,
        amount: Double,
        transactionId: String,
        selectedPlan: InstallmentPlanOption
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSubsequentInstallment = commerceState.completedInstallments > 0 && commerceState.dueAmount > 0.0
    val availablePlans = remember(commerceState) {
        if (commerceState.installmentPlans.isNotEmpty()) {
            commerceState.installmentPlans
        } else {
            val price = if (commerceState.totalPrice > 0) commerceState.totalPrice else 1000.0
            listOf(
                InstallmentPlanOption(
                    planId = "full",
                    titleBn = "এককালীন সম্পূর্ণ পেমেন্ট (${CourseCommerceItemState.formatTakaBn(price)})",
                    installmentsCount = 1,
                    intervalDays = 0,
                    firstAmount = price,
                    perInstallmentAmount = price
                )
            )
        }
    }

    var selectedPlan by remember(availablePlans, commerceState.activePlanId) {
        mutableStateOf(
            availablePlans.find { it.planId == commerceState.activePlanId }
                ?: availablePlans.first()
        )
    }

    val initialSuggestedAmount = remember(selectedPlan, isSubsequentInstallment, commerceState) {
        if (isSubsequentInstallment) {
            if (commerceState.nextInstallmentAmount > 0.0) {
                commerceState.nextInstallmentAmount.coerceAtMost(commerceState.dueAmount)
            } else {
                commerceState.dueAmount
            }
        } else {
            selectedPlan.firstAmount
        }
    }

    var selectedMethod by remember { mutableStateOf("bKash") }
    var senderMobile by remember {
        mutableStateOf(
            commerceState.lastSenderMobile.ifBlank { defaultUserPhone }
        )
    }
    var amountInput by remember(initialSuggestedAmount) {
        mutableStateOf(
            if (initialSuggestedAmount > 0.0) initialSuggestedAmount.toLong().toString() else ""
        )
    }
    var transactionId by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val targetAdminNumber = when (selectedMethod) {
        "bKash" -> paymentSettings.bkashNumber.ifBlank { "01700000000" }
        "Nagad" -> paymentSettings.nagadNumber.ifBlank { paymentSettings.bkashNumber.ifBlank { "01700000000" } }
        "Rocket" -> paymentSettings.rocketNumber.ifBlank { paymentSettings.bkashNumber.ifBlank { "01700000000" } }
        else -> paymentSettings.bkashNumber
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = "Payment",
                            tint = Color(0xFF5648F9),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isSubsequentInstallment) "পরবর্তী কিস্তি পরিশোধ করুন" else "কোর্স কিনুন / কিস্তিতে ভর্তি হোন",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = courseTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "বন্ধ করুন",
                        tint = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // If previous payment was rejected, show exact rejection reason from Admin
            if (commerceState.rejectionReason.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "পূর্ববর্তী পেমেন্ট রিজেক্ট হয়েছে!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "কারণ: ${commerceState.rejectionReason}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // If installment is overdue, show warning banner
            if (commerceState.effectiveState == CourseAccessState.INSTALLMENT_OVERDUE) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "কিস্তির সময়সীমা উত্তীর্ণ হয়েছে!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "নির্ধারিত সময়ের মধ্যে কিস্তি পরিশোধ না করায় কোর্স এক্সেস সাময়িক বন্ধ রয়েছে। এখন কিস্তি পরিশোধ করলে আবার সাথে সাথে আনলক হয়ে যাবে।",
                                fontSize = 12.5.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Course Price & Installment Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "কোর্সের নির্ধারিত মূল্য",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = CourseCommerceItemState.formatTakaBn(commerceState.totalPrice),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF5648F9)
                        )
                    }

                    if (isSubsequentInstallment) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("পরিশোধিত", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    text = CourseCommerceItemState.formatTakaBn(commerceState.paidAmount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("বাকি বকেয়া", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    text = CourseCommerceItemState.formatTakaBn(commerceState.dueAmount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("এবারের কিস্তি (#${CourseCommerceItemState.toBanglaDigits((commerceState.completedInstallments + 1).toString())})", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    text = CourseCommerceItemState.formatTakaBn(initialSuggestedAmount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5648F9)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: Choose Full Payment vs Installment Plan (only on initial purchase)
            if (!isSubsequentInstallment && availablePlans.size > 1) {
                Text(
                    text = "১. পেমেন্ট বা কিস্তি প্ল্যান নির্বাচন করুন",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(8.dp))

                availablePlans.forEach { plan ->
                    val isSelected = selectedPlan.planId == plan.planId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFFF5F3FF) else Color.White,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF5648F9) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedPlan = plan
                                amountInput = plan.firstAmount.toLong().toString()
                            }
                            .testTag("plan_option_${plan.planId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPlan = plan
                                        amountInput = plan.firstAmount.toLong().toString()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF5648F9))
                                )
                                Column {
                                    Text(
                                        text = plan.titleBn,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    if (plan.installmentsCount > 1) {
                                        Text(
                                            text = "মোট ${CourseCommerceItemState.toBanglaDigits(plan.installmentsCount.toString())} কিস্তি • প্রতি ${CourseCommerceItemState.toBanglaDigits(plan.intervalDays.toString())} দিন পরপর • ১ম কিস্তি ${CourseCommerceItemState.formatTakaBn(plan.firstAmount)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    } else {
                                        Text(
                                            text = "একবারে সম্পূর্ণ কোর্স ফি পরিশোধ (কোনো বকেয়া থাকবে না)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = CourseCommerceItemState.formatTakaBn(plan.firstAmount),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color(0xFF5648F9) else Color(0xFF334155)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 2: Payment Method & Admin Send Money Number
            Text(
                text = "২. পেমেন্ট মাধ্যম নির্বাচন ও টাকা পাঠান",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val methods = buildList {
                add(Triple("bKash", "বিকাশ", Color(0xFFE2136E)))
                add(Triple("Nagad", "নগদ", Color(0xFFF7941D)))
                if (paymentSettings.rocketNumber.isNotBlank()) {
                    add(Triple("Rocket", "রকেট", Color(0xFF8C3494)))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                methods.forEach { (code, nameBn, brandColor) ->
                    val isSelected = selectedMethod == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) brandColor.copy(alpha = 0.12f) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) brandColor else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMethod = code }
                            .testTag("payment_method_$code")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = nameBn,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) brandColor else Color(0xFF334155)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Admin Number Copy Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$selectedMethod নম্বর (Send Money করুন)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF166534)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = targetAdminNumber,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF14532D)
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Payment Number", targetAdminNumber))
                                Toast.makeText(context, "নম্বর কপি হয়েছে: $targetAdminNumber", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFDCFCE7),
                                contentColor = Color(0xFF15803D)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "কপি",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("কপি করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (paymentSettings.instructionBn.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = paymentSettings.instructionBn,
                            fontSize = 12.sp,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Sender Mobile, Amount Sent, Transaction ID
            Text(
                text = "৩. আপনার পেমেন্টের তথ্য দিন",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = senderMobile,
                onValueChange = {
                    senderMobile = it
                    validationError = null
                },
                label = { Text("যে মোবাইল নম্বর থেকে টাকা পাঠিয়েছেন") },
                placeholder = { Text("017XXXXXXXX") },
                leadingIcon = {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF5648F9))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_sender_mobile_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    validationError = null
                },
                label = { Text("কত টাকা পাঠিয়েছেন (টাকার পরিমাণ)") },
                placeholder = { Text("যেমন: 200 বা 500 বা 1000") },
                leadingIcon = {
                    Text(
                        text = "৳",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5648F9),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_amount_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = transactionId,
                onValueChange = {
                    transactionId = it
                    validationError = null
                },
                label = { Text("ট্রানজেকশন আইডি (Transaction ID / TrxID)") },
                placeholder = { Text("যেমন: BKS84920X") },
                leadingIcon = {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF5648F9))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_trx_id_input")
            )

            if (!validationError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = validationError!!,
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cleanDigits = amountInput.map { ch ->
                        val idx = "০১২৩৪৫৬৭৮৯".indexOf(ch)
                        if (idx >= 0) ('0' + idx) else ch
                    }.joinToString("").trim()
                    val parsedAmt = cleanDigits.toDoubleOrNull() ?: 0.0
                    val cleanPhone = senderMobile.trim()
                    val cleanTrx = transactionId.trim()

                    when {
                        cleanPhone.length < 10 -> {
                            validationError = "অনুগ্রহ করে সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন।"
                        }
                        parsedAmt <= 0.0 -> {
                            validationError = "অনুগ্রহ করে কত টাকা পাঠিয়েছেন তা লিখুন।"
                        }
                        cleanTrx.length < 4 -> {
                            validationError = "অনুগ্রহ করে সঠিক ট্রানজেকশন আইডি (TrxID) লিখুন।"
                        }
                        else -> {
                            onSubmitPayment(
                                selectedMethod,
                                cleanPhone,
                                parsedAmt,
                                cleanTrx,
                                selectedPlan
                            )
                        }
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5648F9)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_course_payment_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("পেমেন্ট জমা হচ্ছে...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSubsequentInstallment) "কিস্তির পেমেন্ট জমা দিন" else "পেমেন্ট তথ্য জমা দিন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
