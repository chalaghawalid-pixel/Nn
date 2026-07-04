package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: QuranRepository
    private val sharedPrefs = application.getSharedPreferences("quran_tracker_settings", android.content.Context.MODE_PRIVATE)

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "مصحف أخضر") ?: "مصحف أخضر")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(sharedPrefs.getString("font_size_scale", "متوسط") ?: "متوسط")
    val fontSizeScale: StateFlow<String> = _fontSizeScale.asStateFlow()

    fun updateTheme(themeName: String) {
        _appTheme.value = themeName
        sharedPrefs.edit().putString("app_theme", themeName).apply()
    }

    fun updateFontSizeScale(scaleName: String) {
        _fontSizeScale.value = scaleName
        sharedPrefs.edit().putString("font_size_scale", scaleName).apply()
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = QuranRepository(db.studentDao(), db.attendanceDao(), db.memorizationDao(), db.dailyEvaluationDao())
    }

    // Expose all daily evaluations
    val allDailyEvaluations: StateFlow<List<DailyEvaluation>> = repository.allDailyEvaluations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Date formatting helper
    private val dateFullFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayDateString: String = dateFullFormatter.format(Date())

    // Tracks selected date for attendance (defaults to today)
    private val _selectedDate = MutableStateFlow(todayDateString)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Expose all students
    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Expose all attendance records
    val allAttendance: StateFlow<List<AttendanceRecord>> = repository.allAttendanceRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Expose all memorizations
    val allMemorizations: StateFlow<List<StudentMemorization>> = repository.allMemorizations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Helper states computed from raw tables
    // Attendance status for each student on the CURRENT date: studentId -> isPresent (null if unmarked)
    val studentAttendanceForCurrentDate: StateFlow<Map<Int, Boolean?>> = combine(
        selectedDate,
        allAttendance
    ) { date, records ->
        records.filter { it.date == date }
            .associate { it.studentId to it.isPresent }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Attendance rates: studentId -> Double (Percentage)
    val attendanceRates: StateFlow<Map<Int, Double>> = allAttendance.map { records ->
        val grouped = records.groupBy { it.studentId }
        grouped.mapValues { (_, studentRecords) ->
            if (studentRecords.isEmpty()) 100.0
            else {
                val presents = studentRecords.count { it.isPresent }
                (presents.toDouble() / studentRecords.size) * 100.0
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Memorized count: studentId -> Set of memorized Surah indexes
    val studentMemorizedSet: StateFlow<Map<Int, Set<Int>>> = allMemorizations.map { memos ->
        memos.groupBy { it.studentId }
            .mapValues { (_, list) -> list.map { it.surahIndex }.toSet() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Monthly absences: studentId -> count of absences in the last 30 days
    val monthlyAbsences: StateFlow<Map<Int, Int>> = allAttendance.map { records ->
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        val grouped = records.groupBy { it.studentId }
        grouped.mapValues { (_, studentRecords) ->
            studentRecords.count { !it.isPresent && it.timestamp >= thirtyDaysAgo }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Operations
    fun addStudent(name: String, parentName: String = "", parentPhone: String = "", age: String = "", photoUri: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertStudent(name, parentName, parentPhone, age, photoUri)
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
        }
    }

    fun updateStudentNotes(student: Student, notes: String) {
        viewModelScope.launch {
            repository.updateStudentNotes(student, notes)
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    fun updateSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // Toggle attendance today for a student (Cycles through: Unmarked -> Present -> Absent -> Unmarked)
    fun cycleAttendanceForStudent(studentId: Int) {
        val currentDateVal = _selectedDate.value
        val currentStatus = studentAttendanceForCurrentDate.value[studentId]
        
        viewModelScope.launch {
            when (currentStatus) {
                null -> {
                    // Mark as Present
                    repository.markAttendance(studentId, currentDateVal, true)
                }
                true -> {
                    // Mark as Absent
                    repository.markAttendance(studentId, currentDateVal, false)
                }
                false -> {
                    // Remove record to go back to Unmarked
                    repository.removeAttendanceForDate(studentId, currentDateVal)
                }
            }
        }
    }

    // Change directly via buttons if required
    fun setAttendanceForStudent(studentId: Int, isPresent: Boolean) {
        val currentDateVal = _selectedDate.value
        viewModelScope.launch {
            repository.markAttendance(studentId, currentDateVal, isPresent)
        }
    }

    fun clearAttendanceForStudent(studentId: Int) {
        val currentDateVal = _selectedDate.value
        viewModelScope.launch {
            repository.removeAttendanceForDate(studentId, currentDateVal)
        }
    }

    // Memorization operations
    fun toggleSurahMemorized(studentId: Int, surahIndex: Int, isChecked: Boolean) {
        viewModelScope.launch {
            repository.toggleMemorization(studentId, surahIndex, isChecked)
        }
    }

    // Daily evaluation/note operations
    fun saveDailyEvaluation(studentId: Int, date: String, rating: String, note: String) {
        viewModelScope.launch {
            repository.saveDailyEvaluation(studentId, date, rating, note)
        }
    }

    fun deleteDailyEvaluation(id: Int) {
        viewModelScope.launch {
            repository.deleteDailyEvaluation(id)
        }
    }
}

class QuranViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuranViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuranViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
