package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import com.example.data.*
import com.example.ui.QuranViewModel
import com.example.ui.QuranViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

// Screen navigation state definition
sealed interface Screen {
    object StudentsList : Screen
    data class StudentProfile(val studentId: Int) : Screen
    object AbsenceMatrix : Screen
    object Settings : Screen
}

/**
 * Helper to copy chosen images safely to internal files storage so permissions don't expire.
 */
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "student_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: QuranViewModel by viewModels {
        QuranViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()

            MyApplicationTheme(appTheme = appTheme) {
                val fontScale = when (fontSizeScale) {
                    "صغير" -> 0.85f
                    "متوسط" -> 1.0f
                    "كبير" -> 1.18f
                    "كبير جداً" -> 1.35f
                    else -> 1.0f
                }
                val currentDensity = LocalDensity.current

                // Ensure RTL layout for beautiful Arabic-first experience and dynamic font sizing
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(
                        density = currentDensity.density,
                        fontScale = fontScale
                    )
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.StudentsList) }

                    // Standard Android back-press navigation support
                    BackHandler(enabled = currentScreen != Screen.StudentsList) {
                        currentScreen = Screen.StudentsList
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        label = "screen_transition",
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { screen ->
                        when (screen) {
                            is Screen.StudentsList -> {
                                StudentsListScreen(
                                    viewModel = viewModel,
                                    onStudentClick = { studentId ->
                                        currentScreen = Screen.StudentProfile(studentId)
                                    },
                                    onNavigate = { target ->
                                        currentScreen = target
                                    }
                                )
                            }
                            is Screen.StudentProfile -> {
                                StudentProfileScreen(
                                    studentId = screen.studentId,
                                    viewModel = viewModel,
                                    onBackClick = {
                                        currentScreen = Screen.StudentsList
                                    }
                                )
                            }
                            is Screen.AbsenceMatrix -> {
                                AbsenceMatrixScreen(
                                    viewModel = viewModel,
                                    onNavigate = { target ->
                                        currentScreen = target
                                    }
                                )
                            }
                            is Screen.Settings -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigate = { target ->
                                        currentScreen = target
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsListScreen(
    viewModel: QuranViewModel,
    onStudentClick: (Int) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val attendanceRates by viewModel.attendanceRates.collectAsStateWithLifecycle()
    val studentMemorizedSet by viewModel.studentMemorizedSet.collectAsStateWithLifecycle()
    val todayAttendanceStatus by viewModel.studentAttendanceForCurrentDate.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val monthlyAbsences by viewModel.monthlyAbsences.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var newStudentName by remember { mutableStateOf("") }
    var newStudentAge by remember { mutableStateOf("") }
    var newStudentParentName by remember { mutableStateOf("") }
    var newStudentParentPhone by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    // Date formatter for top bar display in Arabic
    val displayDateStr = remember(selectedDate) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(selectedDate) ?: Date()
            val formatter = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ar"))
            formatter.format(date)
        } catch (e: Exception) {
            selectedDate
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "متابعة الطلبة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_student_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "إضافة طالب",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentScreen = Screen.StudentsList,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 650.dp)
            ) {
            // Elegant Welcome Header Card with dynamic stats
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "طلبة المدرسة القرآنية",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "متابعة الحفظ والغياب",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تاريخ التحضير: $displayDateStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Display general count
                        Text(
                            text = "عدد الطلاب: ${students.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick search & Filter bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("search_field"),
                placeholder = { Text("ابحث عن اسم طالب...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث"
                    )
                },
                maxLines = 1,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )

            val filteredStudents = remember(students, searchQuery) {
                if (searchQuery.isBlank()) students
                else students.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "لم يتم العثور على نتائج" else "قائمة الطلاب فارغة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "تأكد من كتابة الاسم بشكل صحيح" else "ابدأ بإضافة أول طالب للحلقة بالضغط على الرمز العلوي 👤+",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val attendanceRate = attendanceRates[student.id] ?: 100.0
                        val memorizedCount = studentMemorizedSet[student.id]?.size ?: 0
                        val todayStatus = todayAttendanceStatus[student.id]
                        val monthlyAbsenceCount = monthlyAbsences[student.id] ?: 0

                        StudentRowItem(
                            student = student,
                            attendanceRate = attendanceRate,
                            memorizedCount = memorizedCount,
                            todayStatus = todayStatus,
                            monthlyAbsenceCount = monthlyAbsenceCount,
                            onClick = { onStudentClick(student.id) },
                            onAttendanceToggle = {
                                viewModel.cycleAttendanceForStudent(student.id)
                            }
                        )
                    }
                }
            }
        }
        }
    }

    // Elegant Dialog to Add Student
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "إضافة طالب جديد",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "يرجى ملء بيانات الطالب للتسجيل في حلقة القرآن الكريم:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = newStudentName,
                        onValueChange = { newStudentName = it },
                        placeholder = { Text("الاسم واللقب (مطلوب)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_student_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newStudentAge,
                        onValueChange = { newStudentAge = it },
                        placeholder = { Text("عمر الطالب (مثل: ١٢ سنة)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_student_age_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newStudentParentName,
                        onValueChange = { newStudentParentName = it },
                        placeholder = { Text("اسم ولي الأمر") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_student_parent_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newStudentParentPhone,
                        onValueChange = { newStudentParentPhone = it },
                        placeholder = { Text("رقم هاتف ولي الأمر") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_student_parent_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newStudentName.isNotBlank()) {
                            viewModel.addStudent(
                                name = newStudentName.trim(),
                                parentName = newStudentParentName.trim(),
                                parentPhone = newStudentParentPhone.trim(),
                                age = newStudentAge.trim()
                            )
                            newStudentName = ""
                            newStudentAge = ""
                            newStudentParentName = ""
                            newStudentParentPhone = ""
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newStudentName = ""
                        newStudentAge = ""
                        newStudentParentName = ""
                        newStudentParentPhone = ""
                        showAddDialog = false
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentRowItem(
    student: Student,
    attendanceRate: Double,
    memorizedCount: Int,
    todayStatus: Boolean?, // null: unmarked, true: present, false: absent
    monthlyAbsenceCount: Int,
    onClick: () -> Unit,
    onAttendanceToggle: () -> Unit
) {
    val context = LocalContext.current
    val callLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && student.parentPhone.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intent)
            } catch (e: Exception) {
                val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intentDial)
            }
        } else {
            if (student.parentPhone.isNotBlank()) {
                val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intentDial)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_card_${student.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Student Avatar (Custom Uploaded Photo or initials fallback)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (student.photoUri.isNotBlank() && File(student.photoUri).exists()) {
                    AsyncImage(
                        model = File(student.photoUri),
                        contentDescription = "صورة الطالب",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = student.name.trim().take(1).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Student information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Call Parent direct action icon
                    if (student.parentPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${student.parentPhone}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                                        context.startActivity(intentDial)
                                    }
                                } else {
                                    callLauncher.launch(Manifest.permission.CALL_PHONE)
                                }
                            },
                            modifier = Modifier.size(24.dp).testTag("call_parent_icon_${student.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "اتصال مباشر بولي الأمر",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Memorization count badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$memorizedCount سورة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Attendance rate badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (attendanceRate >= 80.0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "حضور: ${attendanceRate.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Monthly Absences badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (monthlyAbsenceCount > 3) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "غياب شهري: $monthlyAbsenceCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Dynamic animated colors for the attendance bubble
            val boxBgColor by animateColorAsState(
                targetValue = when (todayStatus) {
                    true -> Color(0xFFE8F5E9)      // light green
                    false -> Color(0xFFFFEBEE)     // light red
                    null -> MaterialTheme.colorScheme.surfaceVariant // light grey
                },
                label = "att_box_bg"
            )
            val boxContentColor by animateColorAsState(
                targetValue = when (todayStatus) {
                    true -> Color(0xFF2E7D32)      // dark green
                    false -> Color(0xFFC62828)     // dark red
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "att_box_content"
            )

            // Quick attendance action tracker button (Right side)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(boxBgColor)
                    .clickable(onClick = onAttendanceToggle)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("attendance_quick_toggle_${student.id}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (todayStatus) {
                            true -> Icons.Default.CheckCircle
                            false -> Icons.Default.Cancel
                            null -> Icons.Default.CircleNotifications
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = boxContentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (todayStatus) {
                            true -> "حاضر"
                            false -> "غائب"
                            null -> "اضغط للتحضير"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = boxContentColor
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    studentId: Int,
    viewModel: QuranViewModel,
    onBackClick: () -> Unit
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val allMemorizations by viewModel.allMemorizations.collectAsStateWithLifecycle()
    val allDailyEvaluations by viewModel.allDailyEvaluations.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val student = remember(students, studentId) {
        students.find { it.id == studentId } ?: Student(id = studentId, name = "طالب مجهول")
    }

    // Filter relevant logs for the calculated student
    val studentAttendanceRecords = remember(allAttendance, studentId) {
        allAttendance.filter { it.studentId == studentId }
    }

    val studentEvaluations = remember(allDailyEvaluations, studentId) {
        allDailyEvaluations.filter { it.studentId == studentId }
    }

    val todayEvaluation = remember(studentEvaluations, selectedDate) {
        studentEvaluations.find { it.date == selectedDate }
    }

    val studentMemorizations = remember(allMemorizations, studentId) {
        allMemorizations.filter { it.studentId == studentId }.map { it.surahIndex }.toSet()
    }

    // Calculated Metrics
    val totalAttendanceDays = studentAttendanceRecords.size
    val presentsCount = studentAttendanceRecords.count { it.isPresent }
    val attendanceRate = if (totalAttendanceDays == 0) 100.0 else (presentsCount.toDouble() / totalAttendanceDays) * 100.0

    // Metric 1: Last Attendance exact timestamp matching (آخر حضور بالتاريخ والوقت)
    val lastAttendanceRecord = remember(studentAttendanceRecords) {
        studentAttendanceRecords.filter { it.isPresent }.maxByOrNull { it.timestamp }
    }
    
    val lastAttendanceDisplayStr = remember(lastAttendanceRecord) {
        if (lastAttendanceRecord != null) {
            try {
                val formatterStr = SimpleDateFormat("yyyy-MM-dd / hh:mm a", Locale.forLanguageTag("ar"))
                "تم تسجيل حضوره في " + formatterStr.format(Date(lastAttendanceRecord.timestamp))
            } catch (e: Exception) {
                // Return simple date value if timestamp isn't formatted
                "حاضر بتاريخ " + lastAttendanceRecord.date
            }
        } else {
            "لا توجد سجلات حضور نشطة"
        }
    }

    // Metric 2: Weekly absence rate (أيام الغياب هذا الأسبوع المباشرة)
    val absentDaysThisWeek = remember(studentAttendanceRecords) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        studentAttendanceRecords.count { !it.isPresent && it.timestamp >= sevenDaysAgo }
    }

    // Metric 3: Monthly absence rate (أيام الغياب هذا الشهر)
    val absentDaysThisMonth = remember(studentAttendanceRecords) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        studentAttendanceRecords.count { !it.isPresent && it.timestamp >= thirtyDaysAgo }
    }

    // Quran Memorization Metrics
    val surahsList = QuranData.surahs
    val memorizedSurahsCount = studentMemorizations.size
    val progressFraction = memorizedSurahsCount.toFloat() / surahsList.size

    // Simple custom search filter for surahs inside student screen
    var surahSearchQuery by remember { mutableStateOf("") }
    var surahTabSelections by remember { mutableStateOf(0) } // 0: All, 1: Juz Amma (78-114), 2: Juz Tabarak (67-77)

    val currentFilteredSurahs = remember(surahSearchQuery, surahTabSelections) {
        var base = surahsList
        if (surahTabSelections == 1) {
            base = surahsList.filter { it.juz == 30 }
        } else if (surahTabSelections == 2) {
            base = surahsList.filter { it.juz == 29 }
        }

        val filtered = if (surahSearchQuery.isBlank()) base
        else base.filter { it.nameArabic.contains(surahSearchQuery) || it.nameEnglish.contains(surahSearchQuery, ignoreCase = true) }
        filtered.sortedByDescending { it.index }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Notes and profile state managers
    var showNotesDialog by remember { mutableStateOf(false) }
    var notesTextState by remember(student.notes) { mutableStateOf(student.notes) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember(student.name) { mutableStateOf(student.name) }
    var editAge by remember(student.age) { mutableStateOf(student.age) }
    var editParentName by remember(student.parentName) { mutableStateOf(student.parentName) }
    var editParentPhone by remember(student.parentPhone) { mutableStateOf(student.parentPhone) }

    val profileCallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && student.parentPhone.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intent)
            } catch (e: Exception) {
                val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intentDial)
            }
        } else {
            if (student.parentPhone.isNotBlank()) {
                val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                context.startActivity(intentDial)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = saveImageToInternalStorage(context, uri)
            if (savedPath != null) {
                viewModel.updateStudent(student.copy(photoUri = savedPath))
                Toast.makeText(context, "تم حفظ صورة الطالب بنجاح ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = student.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // Small icon next to the name inside info
                            IconButton(
                                onClick = { showNotesDialog = true },
                                modifier = Modifier.size(24.dp).testTag("student_general_notes_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NoteAlt,
                                    contentDescription = "الملاحظات الدائمة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "ملف الطالب ومتابعة السور",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    // Delete student action
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("delete_student_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الطالب",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        // Daily evaluation state helpers
        var localDailyNote by remember(todayEvaluation) { mutableStateOf(todayEvaluation?.note ?: "") }
        var localDailyRating by remember(todayEvaluation) { mutableStateOf(todayEvaluation?.rating ?: "") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 650.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            item {
                // Interactive Profile Photo Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (student.photoUri.isNotBlank() && File(student.photoUri).exists()) {
                                AsyncImage(
                                    model = File(student.photoUri),
                                    contentDescription = "صورة الطالب",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "رفع صورة الطالب",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            
                            // Small overlay edit badge
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "اضغط على الصورة لرفع أو تغيير صورة الطالب",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item {
                // Main Performance Metrics Section (لوحة البيانات التحصيلية)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "مؤشرات أداء الطالب",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Attendance Indicators & Date Log (المتطلبات المحددة للحضور والغياب)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Rate
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("نسبة الحضور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        "${attendanceRate.toInt()}%",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (attendanceRate >= 80) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.padding(vertical = 4.dp).testTag("attendance_rate_text")
                                    )
                                    Text(
                                        "إجمالي الأيام: $totalAttendanceDays",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Absences weekly log
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("غياب هذا الأسبوع", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        "$absentDaysThisWeek أيام",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (absentDaysThisWeek > 1) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Text(
                                        "آخر 7 أيام",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Absences monthly log
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("الغياب الشهري", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        "$absentDaysThisMonth أيام",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (absentDaysThisMonth > 3) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Text(
                                        "آخر 30 يوماً",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic Date and exact recorded Time for latest attendance (التاريخ والوقت)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lastAttendanceDisplayStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "مؤشر الإنجاز (الحفظ اليومي)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "حفظ $memorizedSurahsCount سورة من أصل 114",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .testTag("memorization_progress_bar"),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            item {
                // Personal & Contact Details Section (بيانات التواصل والمعلومات الشخصية)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("personal_details_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.size(32.dp).testTag("edit_student_profile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل البيانات الشخصية",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "البيانات الشخصية وولي الأمر",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Age Field
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = student.age.ifBlank { "غير مسجل" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "عمر الطالب:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Cake,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)

                        // Parent Name Field
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = student.parentName.ifBlank { "غير مسجل" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "اسم ولي الأمر:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)

                        // Parent Phone Field
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = student.parentPhone.ifBlank { "غير مسجل" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (student.parentPhone.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                                try {
                                                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${student.parentPhone}"))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val intentDial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.parentPhone}"))
                                                    context.startActivity(intentDial)
                                                }
                                            } else {
                                                profileCallLauncher.launch(Manifest.permission.CALL_PHONE)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp).testTag("profile_call_parent_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "اتصال مباشر بولي الأمر",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "رقم هاتف الولي:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Unified Daily Notes & Evaluation Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "صغر الأيقونة" -> Shrink the icon! A highly polished small save button
                            IconButton(
                                onClick = {
                                    viewModel.saveDailyEvaluation(
                                        studentId = studentId,
                                        date = selectedDate,
                                        rating = localDailyRating,
                                        note = localDailyNote.trim()
                                    )
                                    Toast.makeText(context, "تم حفظ تقييم اليوم بنجاح ✅", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("save_daily_notes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "حفظ التقييم",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp) // shrunken icon!
                                )
                            }
                            Text(
                                text = "تقييم وملاحظات اليوم ($selectedDate)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Daily Rating Selection (زر جيد أو ضعيف لكل يوم)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "جيد" button
                            val isGoodSelected = localDailyRating == "جيد"
                            Button(
                                onClick = {
                                    localDailyRating = if (isGoodSelected) "" else "جيد"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isGoodSelected) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isGoodSelected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = if (isGoodSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isGoodSelected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("أداء جيد 👍", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            // "ضعيف" button
                            val isWeakSelected = localDailyRating == "ضعيف"
                            Button(
                                onClick = {
                                    localDailyRating = if (isWeakSelected) "" else "ضعيف"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWeakSelected) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isWeakSelected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = if (isWeakSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isWeakSelected) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("أداء ضعيف 👎", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Daily Note Input Field
                        OutlinedTextField(
                            value = localDailyNote,
                            onValueChange = { localDailyNote = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .testTag("teacher_notes_input"),
                            placeholder = { Text("اكتب ملاحظات نصية قصيرة حول أداء وحفظ الطالب اليوم...", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Right),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }

            // Show preceding evaluation list (قائمة الملاحظات لكل يوم)
            item {
                Text(
                    text = "سجل الملاحظات والتقييمات اليومية",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            if (studentEvaluations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد ملاحظات أو تقييمات مسجلة بعد لهذا الطالب.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(studentEvaluations, key = { it.id }) { evaluation ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Date tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = evaluation.date,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    // Evaluation rating badge
                                    if (evaluation.rating.isNotBlank()) {
                                        val isGood = evaluation.rating == "جيد"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isGood) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isGood) "ممتاز/جيد 👍" else "ضعيف 👎",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGood) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }

                                if (evaluation.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = evaluation.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Small delete action button to erase mistake entries
                            IconButton(
                                onClick = {
                                    viewModel.deleteDailyEvaluation(evaluation.id)
                                    Toast.makeText(context, "تم حذف الملاحظة بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف الملاحظة",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Tab bar and Search header inside student profile
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), thickness = 1.dp)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "متابعة السور المباركة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                // Simplified Tabs for quicker Surah navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("المصحف كاملاً", "جزء عمّ (٣٠)", "جزء تبارك (٢٩)").forEachIndexed { index, title ->
                        val isSelected = surahTabSelections == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                )
                                .clickable { surahTabSelections = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                // Search bar for precise Surah lookup
                OutlinedTextField(
                    value = surahSearchQuery,
                    onValueChange = { surahSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("surah_search_field"),
                    placeholder = { Text("بحث باسم السورة...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    maxLines = 1,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
            }

            items(currentFilteredSurahs, key = { it.index }) { surah ->
                val isMemorized = studentMemorizations.contains(surah.index)

                SurahCheckRow(
                    surah = surah,
                    isCheckActive = isMemorized,
                    onCheckChange = { isChecked ->
                        viewModel.toggleSurahMemorized(studentId, surah.index, isChecked)
                    }
                )
            }
        }
        }
    }

    // Delete Student Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("تأكيد حذف الطالب", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف الطالب '${student.name}' نهائياً وسجلاته من حلقة الحفظ؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudent(student)
                        showDeleteConfirmDialog = false
                        onBackClick()
                    },
                    modifier = Modifier.testTag("dialog_delete_confirm_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // General Permanent Notes Dialog (تم نقله هنا ليكون كأيقونة صغيرة بجانب الاسم)
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = {
                Text(
                    "ملاحظات المعلم الدائمة للطالب",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = notesTextState,
                    onValueChange = { notesTextState = it },
                    placeholder = { Text("اكتب ملاحظات عامة دائمة حول سلوك وحالة الطالب...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("student_general_notes_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStudentNotes(student, notesTextState.trim())
                        Toast.makeText(context, "تم حفظ الملاحظات العامة بنجاح ✅", Toast.LENGTH_SHORT).show()
                        showNotesDialog = false
                    },
                    modifier = Modifier.testTag("notes_dialog_confirm")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Edit Profile Personal and Contact Info Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    "تعديل البيانات الشخصية والتواصل",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("الاسم واللقب (مطلوب)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_student_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it },
                        label = { Text("عمر الطالب") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_student_age_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editParentName,
                        onValueChange = { editParentName = it },
                        label = { Text("اسم ولي الأمر") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_student_parent_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editParentPhone,
                        onValueChange = { editParentPhone = it },
                        label = { Text("رقم هاتف ولي الأمر") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_student_parent_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateStudent(
                                student.copy(
                                    name = editName.trim(),
                                    age = editAge.trim(),
                                    parentName = editParentName.trim(),
                                    parentPhone = editParentPhone.trim()
                                )
                            )
                            Toast.makeText(context, "تم تحديث البيانات بنجاح ✅", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("edit_profile_confirm_button")
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SurahCheckRow(
    surah: Surah,
    isCheckActive: Boolean,
    onCheckChange: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCheckActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        label = "surah_bg_color"
    )
    val indexBgColor by animateColorAsState(
        targetValue = if (isCheckActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "surah_index_bg"
    )
    val indexTextColor by animateColorAsState(
        targetValue = if (isCheckActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "surah_index_text"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCheckActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "surah_title"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onCheckChange(!isCheckActive) }
            .testTag("surah_row_${surah.index}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index Ring badge styled
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(indexBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${surah.index}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = indexTextColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Surah naming details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "سورة ${surah.nameArabic}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "جزء ${surah.juz}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${surah.versesCount} آية",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Beautiful Custom checkbox status (✔) using Material Design Checkbox
            Checkbox(
                checked = isCheckActive,
                onCheckedChange = onCheckChange,
                modifier = Modifier.testTag("surah_check_${surah.index}"),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                )
            )
        }
    }
}

/**
 * Premium Bottom Navigation bar compliant with Material Design 3.
 */
@Composable
fun AppBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen is Screen.StudentsList,
            onClick = { onNavigate(Screen.StudentsList) },
            icon = {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "الطلاب",
                    tint = if (currentScreen is Screen.StudentsList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "الطلبة",
                    fontWeight = if (currentScreen is Screen.StudentsList) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("nav_students_list")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.AbsenceMatrix,
            onClick = { onNavigate(Screen.AbsenceMatrix) },
            icon = {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "سجل الغيابات",
                    tint = if (currentScreen is Screen.AbsenceMatrix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "الغيابات",
                    fontWeight = if (currentScreen is Screen.AbsenceMatrix) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("nav_absence_matrix")
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "الإعدادات",
                    tint = if (currentScreen is Screen.Settings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = {
                Text(
                    text = "الإعدادات",
                    fontWeight = if (currentScreen is Screen.Settings) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("nav_settings")
        )
    }
}

/**
 * Calendar table displaying horizontal months and vertical student names showing absences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceMatrixScreen(
    viewModel: QuranViewModel,
    onNavigate: (Screen) -> Unit
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val months = remember {
        listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
    }

    // Build absences matrix: StudentId -> MonthIndex (0..11) -> Count of absences
    val absenceMatrix = remember(students, allAttendance, currentYear) {
        val matrix = mutableMapOf<Int, MutableMap<Int, Int>>()
        for (st in students) {
            matrix[st.id] = mutableMapOf()
            for (m in 0..11) {
                matrix[st.id]!![m] = 0
            }
        }

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        for (record in allAttendance) {
            if (!record.isPresent) { // Absent
                try {
                    val date = parser.parse(record.date)
                    if (date != null) {
                        cal.time = date
                        if (cal.get(Calendar.YEAR) == currentYear) {
                            val monthIndex = cal.get(Calendar.MONTH)
                            val map = matrix[record.studentId]
                            if (map != null) {
                                val currentCount = map[monthIndex] ?: 0
                                map[monthIndex] = currentCount + 1
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        matrix
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "سجل الغيابات السنوي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "متابعة غيابات الطلاب الشهرية لعام $currentYear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentScreen = Screen.AbsenceMatrix,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Explanatory Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "يعرض الجدول عدد أيام الغياب لكل طالب مقسمة حسب أشهر السنة الحالية. الخلية التي تحتوي على رقم تمثل تكرار الغياب في هذا الشهر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (students.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد طلاب مسجلين لعرض مصفوفة الغياب",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                // Table spreadsheet scroll container
                val horizontalScrollState = rememberScrollState()

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sticky-like name placeholder width
                                Text(
                                    text = "اسم الطالب",
                                    modifier = Modifier
                                        .width(135.dp)
                                        .padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Right
                                )

                                // Vertical Divider separating student name
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                )

                                // Month Headers
                                months.forEach { month ->
                                    Text(
                                        text = month,
                                        modifier = Modifier
                                            .width(75.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), thickness = 1.dp)

                            // Student Rows
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(students, key = { it.id }) { student ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Student name column
                                        Text(
                                            text = student.name,
                                            modifier = Modifier
                                                .width(135.dp)
                                                .padding(horizontal = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Right
                                        )

                                        // Column separator line
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(24.dp)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        )

                                        // Months columns for student
                                        months.forEachIndexed { index, _ ->
                                            val absencesCount = absenceMatrix[student.id]?.get(index) ?: 0
                                            Box(
                                                modifier = Modifier
                                                    .width(75.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (absencesCount > 0) {
                                                    val cellBg = if (absencesCount >= 3) {
                                                        MaterialTheme.colorScheme.errorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                                    }
                                                    val cellText = if (absencesCount >= 3) {
                                                        MaterialTheme.colorScheme.onErrorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.error
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(cellBg)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$absencesCount غ",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = cellText
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "-",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main settings screen to adjust styles, themes, and font size scales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: QuranViewModel,
    onNavigate: (Screen) -> Unit
) {
    val currentTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val currentFontSize by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val themesList = listOf(
        Triple("مصحف أخضر", "مصحف أخضر", Color(0xFF2E7D32)),
        Triple("أزرق سماوي", "أزرق سماوي", Color(0xFF0288D1)),
        Triple("ذهبي دافئ", "ذهبي دافئ", Color(0xFFF57C00)),
        Triple("كلاسيكي غامق", "كلاسيكي غامق", Color(0xFF37474F))
    )

    val fontSizesList = listOf(
        "صغير" to "خط مقروء مدمج",
        "متوسط" to "خط النظام القياسي",
        "كبير" to "خط مكبر للمتابعة المريحة",
        "كبير جداً" to "خط أقصى وضوح ومقروئية"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "إعدادات التطبيق",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentScreen = Screen.Settings,
                onNavigate = onNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 650.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Theme selection card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "مظهر التطبيق وأنماط الألوان",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "اختر نمط الألوان المفضل لديك لتسهيل قراءة البيانات ومتابعة حلقة حفظ القرآن الكريم:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Render themes grid
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            themesList.forEach { (themeKey, themeLabel, themeColor) ->
                                val isSelected = currentTheme == themeKey
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateTheme(themeKey)
                                            Toast.makeText(context, "تم تطبيق السمة: $themeLabel ✅", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("theme_option_$themeKey"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        }
                                    ),
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Circular Color showcase circle
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(themeColor)
                                            )

                                            Text(
                                                text = themeLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateTheme(themeKey)
                                                Toast.makeText(context, "تم تطبيق السمة: $themeLabel ✅", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Font size selection card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "حجم خط نصوص التطبيق",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "اضبط مقياس حجم الخط لتناسب شاشة جهازك وتوفر رؤية واضحة ومقروئية مريحة أثناء الاستخدام:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Render sizes list
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            fontSizesList.forEach { (sizeKey, sizeDescription) ->
                                val isSelected = currentFontSize == sizeKey
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateFontSizeScale(sizeKey)
                                            Toast.makeText(context, "تم تطبيق مقياس الخط: $sizeKey ✅", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("font_size_option_$sizeKey"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        }
                                    ),
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = sizeKey,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = sizeDescription,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateFontSizeScale(sizeKey)
                                                Toast.makeText(context, "تم تطبيق مقياس الخط: $sizeKey ✅", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Realtime preview card showing a beautifully designed Qur'anic verse block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "معاينة حجم خط النصوص المباشر:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "خَيرُكُم مَن تَعَلَّمَ القُرآنَ وَعَلَّمَهُ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Credits Footer Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "مدرسة القرآن الكريم المتابعة الذكية • v1.4",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
        }
    }
}

