package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.api.AddressItem
import com.example.api.SchoolItem
import com.example.profile.EditProfileViewModel
import com.example.utils.AvatarUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onAvatarSelected(it.toString()) }
    }

    // ===== Date picker — current value নিয়ে খোলা =====
    val openDatePicker: () -> Unit = remember(uiState.dobIso) {
        {
            val cal = Calendar.getInstance()
            if (uiState.dobIso.isNotBlank()) {
                try {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    fmt.parse(uiState.dobIso.take(19))?.let { cal.time = it }
                } catch (_: Exception) {
                    cal.add(Calendar.YEAR, -16)
                }
            } else {
                cal.add(Calendar.YEAR, -16)
            }
            val dlg = DatePickerDialog(
                context,
                { _, y, m, d -> viewModel.onDobSelected(y, m, d) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dlg.datePicker.maxDate = System.currentTimeMillis()
            dlg.show()
        }
    }

    // ===== Back with unsaved guard =====
    val handleBack: () -> Unit = {
        if (uiState.hasUnsavedChanges && !uiState.isSaving) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("প্রোফাইল এডিট", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(elevation = 8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveProfile(onSuccess = onBack) },
                        enabled = !uiState.isSaving && !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("সংরক্ষণ হচ্ছে...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.hasUnsavedChanges) "পরিবর্তন সংরক্ষণ করো" else "সংরক্ষণ করো",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ===== Banners =====
                uiState.errorMessage?.let { err ->
                    BannerCard(
                        text = err,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        icon = Icons.Default.ErrorOutline
                    )
                }
                uiState.successMessage?.let { msg ->
                    BannerCard(
                        text = msg,
                        containerColor = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32),
                        icon = Icons.Default.CheckCircle
                    )
                }
                uiState.nameChangeWarning?.let { warn ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF7E6),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "নাম পরিবর্তন হয়নি",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    warn,
                                    fontSize = 12.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 17.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearNameChangeWarning() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Dismiss",
                                    tint = Color(0xFF92400E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ===== Avatar =====
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .clickable { showAvatarPickerSheet = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    val req = remember(uiState.avatarUrl, uiState.firstName, context) {
                                        AvatarUtils.buildImageRequest(
                                            context, uiState.avatarUrl, uiState.firstName
                                        )
                                    }
                                    SubcomposeAsyncImage(
                                        model = req,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = {
                                            Box(
                                                Modifier.fillMaxSize().background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.secondary
                                                        )
                                                    )
                                                ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    uiState.firstName.trim()
                                                        .firstOrNull()?.toString()?.uppercase() ?: "S",
                                                    color = Color.White,
                                                    fontSize = 36.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(32.dp).align(Alignment.BottomEnd)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CameraAlt, "Change",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showAvatarPickerSheet = true }) {
                            Icon(
                                Icons.Default.PhotoCamera, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "প্রোফাইল ছবি পরিবর্তন করো",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ===== 1. ব্যক্তিগত তথ্য =====
                FormSectionCard(
                    title = "ব্যক্তিগত তথ্য",
                    icon = Icons.Default.Person,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    OutlinedTextField(
                        value = uiState.firstName,
                        onValueChange = { viewModel.onFirstNameChange(it) },
                        label = { Text("পূর্ণ নাম") },
                        placeholder = { Text("তোমার নাম লিখো") },
                        leadingIcon = { Icon(Icons.Default.Badge, null) },
                        isError = uiState.fieldErrors.containsKey("firstName"),
                        supportingText = uiState.fieldErrors["firstName"]?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (uiState.isNameChanged) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "নাম পরিবর্তন ৬০ দিনে একবার করা যায়",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.phone,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("ফোন নম্বর (অ্যাকাউন্ট)") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Lock, "Locked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(14.dp))
                    FieldLabel("লিঙ্গ")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("Male" to "পুরুষ", "Female" to "মহিলা").forEach { (k, l) ->
                            ChoiceChipButton(
                                text = l,
                                isSelected = uiState.gender.equals(k, true),
                                onClick = { viewModel.onGenderChange(k) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    FieldLabel("জন্মতারিখ")
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openDatePicker() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarToday, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = uiState.dobDisplay.ifBlank { "জন্মতারিখ নির্বাচন করো" },
                                    fontSize = 14.sp,
                                    color = if (uiState.dobDisplay.isNotBlank())
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.EditCalendar, "Pick",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ===== 2. অভিভাবকের তথ্য =====
                FormSectionCard(
                    title = "অভিভাবকের তথ্য",
                    icon = Icons.Default.FamilyRestroom,
                    iconTint = Color(0xFF0284C7)
                ) {
                    OutlinedTextField(
                        value = uiState.guardianName,
                        onValueChange = { viewModel.onGuardianNameChange(it) },
                        label = { Text("অভিভাবকের নাম") },
                        placeholder = { Text("মাতা / পিতার নাম") },
                        leadingIcon = { Icon(Icons.Default.PersonOutline, null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.guardianMobile,
                        onValueChange = { viewModel.onGuardianMobileChange(it.filter { c -> c.isDigit() }.take(11)) },
                        label = { Text("অভিভাবকের মোবাইল নম্বর") },
                        placeholder = { Text("01XXXXXXXXX") },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = uiState.fieldErrors.containsKey("guardianMobile"),
                        supportingText = uiState.fieldErrors["guardianMobile"]?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // ===== 3. শিক্ষাপ্রতিষ্ঠান =====
                FormSectionCard(
                    title = "শিক্ষাপ্রতিষ্ঠান",
                    icon = Icons.Default.School,
                    iconTint = Color(0xFF059669)
                ) {
                    // Class/Group preview (read-only info)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "শ্রেণি: ${
                                    uiState.userClassDisplay.ifBlank { uiState.userClassCode }
                                }  |  বিভাগ: ${
                                    bengaliGroupName(uiState.userGroup)
                                }",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "শ্রেণি/বিভাগ পরিবর্তন করতে 'সিলেবাস পরিবর্তন' অপশন ব্যবহার করো।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    FieldLabel("শিফট")
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Morning" to "মর্নিং",
                            "Day" to "ডে",
                            "NA" to "প্রযোজ্য নয়"
                        ).forEach { (k, l) ->
                            ChoiceChipButton(
                                text = l,
                                isSelected = uiState.shift.equals(k, true),
                                onClick = { viewModel.onShiftChange(k) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    DropdownSelectorField(
                        label = "বিভাগ (Division)",
                        selectedValue = uiState.selectedDivisionName.ifBlank { "বিভাগ নির্বাচন করো" },
                        items = uiState.divisions.map { it.display ?: "" },
                        onItemSelected = { name ->
                            uiState.divisions.find { it.display == name }
                                ?.let { viewModel.onSelectDivision(it) }
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    DropdownSelectorField(
                        label = "জেলা (District)",
                        selectedValue = when {
                            uiState.isDistrictsLoading -> "লোড হচ্ছে..."
                            uiState.selectedDistrictName.isNotBlank() -> uiState.selectedDistrictName
                            else -> "জেলা নির্বাচন করো"
                        },
                        items = uiState.districts.map { it.display ?: "" },
                        enabled = uiState.districts.isNotEmpty() && !uiState.isDistrictsLoading,
                        onItemSelected = { name ->
                            uiState.districts.find { it.display == name }
                                ?.let { viewModel.onSelectDistrict(it) }
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    FieldLabel("স্কুল / কলেজ")
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setSchoolSearchDialogVisible(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.selectedSchoolName.ifBlank {
                                    "স্কুল / কলেজ খুঁজে বেছে নাও"
                                },
                                fontSize = 14.sp,
                                fontWeight = if (uiState.selectedSchoolName.isNotBlank())
                                    FontWeight.Medium else FontWeight.Normal,
                                color = if (uiState.selectedSchoolName.isNotBlank())
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.Search, "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ===== 4. বোর্ড পরীক্ষার তথ্য =====
                FormSectionCard(
                    title = "বোর্ড পরীক্ষার তথ্য",
                    icon = Icons.Default.Assignment,
                    iconTint = Color(0xFFD97706)
                ) {
                    DropdownSelectorField(
                        label = "এসএসসি বোর্ড (SSC Board)",
                        selectedValue = uiState.sscBoardName.ifBlank { "নির্বাচন করো" },
                        items = viewModel.educationBoards,
                        onItemSelected = { viewModel.onSscBoardChange(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.sscRollNumber,
                        onValueChange = { viewModel.onSscRollChange(it.filter { c -> c.isDigit() }.take(10)) },
                        label = { Text("এসএসসি রোল নম্বর") },
                        placeholder = { Text("যেমন: 762180") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = uiState.fieldErrors.containsKey("sscRoll"),
                        supportingText = uiState.fieldErrors["sscRoll"]?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.boardRegNumber,
                        onValueChange = { viewModel.onBoardRegChange(it.filter { c -> c.isDigit() }.take(12)) },
                        label = { Text("বোর্ড রেজিস্ট্রেশন নম্বর") },
                        placeholder = { Text("যেমন: 2213531789") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = uiState.fieldErrors.containsKey("regNo"),
                        supportingText = uiState.fieldErrors["regNo"]?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (uiState.isHscRelevant) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(14.dp))

                        DropdownSelectorField(
                            label = "এইচএসসি বোর্ড",
                            selectedValue = uiState.hscBoardName.ifBlank { "নির্বাচন করো" },
                            items = viewModel.educationBoards,
                            onItemSelected = { viewModel.onHscBoardChange(it) }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.hscRollNumber,
                            onValueChange = { viewModel.onHscRollChange(it.filter { c -> c.isDigit() }.take(10)) },
                            label = { Text("এইচএসসি রোল নম্বর (যদি থাকে)") },
                            placeholder = { Text("যেমন: 880000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = uiState.fieldErrors.containsKey("hscRoll"),
                            supportingText = uiState.fieldErrors["hscRoll"]?.let {
                                { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // ===== School search dialog =====
    if (uiState.showSchoolSearchDialog) {
        SchoolSearchDialog(
            searchResults = uiState.schoolSearchResults,
            isLoading = uiState.isSchoolSearching,
            selectedDistrict = uiState.selectedDistrictName,
            onSearchQuery = { viewModel.searchSchools(it) },
            onSelectSchool = { viewModel.onSelectSchool(it) },
            onDismiss = { viewModel.setSchoolSearchDialogVisible(false) }
        )
    }

    // ===== Avatar sheet =====
    if (showAvatarPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "প্রোফাইল ছবি পরিবর্তন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showAvatarPickerSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate, null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "গ্যালারি থেকে ছবি বেছে নাও",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "তোমার ডিভাইস থেকে নতুন ছবি",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    "অথবা একটি অবতার বেছে নাও",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val presetAvatars = listOf(
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Felix" to "এডভেঞ্চারার ১",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Aneka" to "এডভেঞ্চারার ২",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Sam" to "এডভেঞ্চারার ৩",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Milo" to "এডভেঞ্চারার ৪",
                    "https://api.dicebear.com/7.x/bottts/png?seed=Robot1" to "রোবট ১",
                    "https://api.dicebear.com/7.x/bottts/png?seed=Robot2" to "রোবট ২"
                )

                // 3-column grid, 2 rows
                presetAvatars.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (url, _) ->
                            val isSelected = uiState.avatarUrl == url
                            Surface(
                                shape = CircleShape,
                                border = BorderStroke(
                                    2.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.onAvatarSelected(url)
                                        showAvatarPickerSheet = false
                                    }
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) { Spacer(Modifier.size(64.dp)) }
                        }
                    }
                }

                if (!uiState.avatarUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.onAvatarRemoved()
                            showAvatarPickerSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("বর্তমান ছবি মুছে ফেলো", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ===== Discard changes dialog =====
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFD97706)) },
            title = { Text("পরিবর্তন বাতিল করবে?", fontWeight = FontWeight.Bold) },
            text = { Text("তুমি কিছু তথ্য পরিবর্তন করেছ কিন্তু সংরক্ষণ করনি। বের হলে পরিবর্তন হারিয়ে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("হ্যাঁ, বাতিল করো", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("না, থাক")
                }
            }
        )
    }
}

// ============================================================
//  Small helper composables
// ============================================================

private fun bengaliGroupName(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "hum", "humanities", "humanities_group" -> "মানবিক"
        "sci", "science", "science_group" -> "বিজ্ঞান"
        "bs", "bsc", "business", "business_studies", "commerce" -> "ব্যবসায় শিক্ষা"
        else -> raw?.ifBlank { "সাধারণ" } ?: "সাধারণ"
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BannerCard(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    Modifier.size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun ChoiceChipButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelectorField(
    label: String,
    selectedValue: String,
    items: List<String>,
    onItemSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                items.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 14.sp) },
                        onClick = {
                            onItemSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolSearchDialog(
    searchResults: List<SchoolItem>,
    isLoading: Boolean,
    selectedDistrict: String,
    onSearchQuery: (String) -> Unit,
    onSelectSchool: (SchoolItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "স্কুল / কলেজ খুঁজুন",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedDistrict.isNotBlank()) {
                            Text(
                                selectedDistrict,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchQuery(it)
                    },
                    placeholder = { Text("প্রতিষ্ঠানের নাম লিখো...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    searchResults.isEmpty() -> Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (query.isBlank()) "স্কুল খুঁজতে নাম লিখো"
                            else "কোনো প্রতিষ্ঠান পাওয়া যায়নি",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    else -> LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.id ?: it.name ?: UUID.randomUUID().toString() }) { school ->
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectSchool(school) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.School, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        school.name ?: "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
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
