package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val notes: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val age: String = "",
    val photoUri: String = ""
)

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val date: String, // format: "yyyy-MM-dd"
    val isPresent: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "student_memorizations")
data class StudentMemorization(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val surahIndex: Int // 1 to 114
)

@Entity(tableName = "daily_evaluations")
data class DailyEvaluation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val date: String, // format: "yyyy-MM-dd"
    val rating: String, // "جيد" or "ضعيف" or ""
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
