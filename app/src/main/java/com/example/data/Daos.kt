package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getStudentById(id: Int): Student?
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId")
    fun getAttendanceForStudent(studentId: Int): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE studentId = :studentId AND date = :date")
    suspend fun deleteAttendanceForDate(studentId: Int, date: String)
}

@Dao
interface MemorizationDao {
    @Query("SELECT * FROM student_memorizations WHERE studentId = :studentId")
    fun getMemorizationsForStudent(studentId: Int): Flow<List<StudentMemorization>>

    @Query("SELECT * FROM student_memorizations")
    fun getAllMemorizations(): Flow<List<StudentMemorization>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemorization(memorization: StudentMemorization)

    @Query("DELETE FROM student_memorizations WHERE studentId = :studentId AND surahIndex = :surahIndex")
    suspend fun deleteMemorization(studentId: Int, surahIndex: Int)
}

@Dao
interface DailyEvaluationDao {
    @Query("SELECT * FROM daily_evaluations ORDER BY timestamp DESC")
    fun getAllDailyEvaluations(): Flow<List<DailyEvaluation>>

    @Query("SELECT * FROM daily_evaluations WHERE studentId = :studentId ORDER BY date DESC, timestamp DESC")
    fun getEvaluationsForStudent(studentId: Int): Flow<List<DailyEvaluation>>

    @Query("SELECT * FROM daily_evaluations WHERE studentId = :studentId AND date = :date LIMIT 1")
    suspend fun getEvaluationForStudentAndDate(studentId: Int, date: String): DailyEvaluation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyEvaluation(evaluation: DailyEvaluation)

    @Query("DELETE FROM daily_evaluations WHERE id = :id")
    suspend fun deleteDailyEvaluationById(id: Int)

    @Query("DELETE FROM daily_evaluations WHERE studentId = :studentId AND date = :date")
    suspend fun deleteDailyEvaluationForDate(studentId: Int, date: String)
}
