package com.example.data

import kotlinx.coroutines.flow.Flow

class QuranRepository(
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceDao,
    private val memorizationDao: MemorizationDao,
    private val dailyEvaluationDao: DailyEvaluationDao
) {
    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    val allAttendanceRecords: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendanceRecords()
    val allMemorizations: Flow<List<StudentMemorization>> = memorizationDao.getAllMemorizations()
    val allDailyEvaluations: Flow<List<DailyEvaluation>> = dailyEvaluationDao.getAllDailyEvaluations()

    fun getEvaluationsForStudent(studentId: Int): Flow<List<DailyEvaluation>> {
        return dailyEvaluationDao.getEvaluationsForStudent(studentId)
    }

    suspend fun saveDailyEvaluation(studentId: Int, date: String, rating: String, note: String) {
        dailyEvaluationDao.deleteDailyEvaluationForDate(studentId, date)
        val evaluation = DailyEvaluation(
            studentId = studentId,
            date = date,
            rating = rating,
            note = note
        )
        dailyEvaluationDao.insertDailyEvaluation(evaluation)
    }

    suspend fun deleteDailyEvaluation(id: Int) {
        dailyEvaluationDao.deleteDailyEvaluationById(id)
    }

    suspend fun getStudentById(id: Int): Student? {
        return studentDao.getStudentById(id)
    }

    suspend fun insertStudent(name: String, parentName: String = "", parentPhone: String = "", age: String = "", photoUri: String = ""): Long {
        val student = Student(name = name, parentName = parentName, parentPhone = parentPhone, age = age, photoUri = photoUri)
        return studentDao.insertStudent(student)
    }

    suspend fun updateStudentNotes(student: Student, notes: String) {
        val updatedStudent = student.copy(notes = notes)
        studentDao.insertStudent(updatedStudent)
    }

    suspend fun updateStudent(student: Student) {
        studentDao.insertStudent(student)
    }

    suspend fun deleteStudent(student: Student) {
        studentDao.deleteStudent(student)
    }

    fun getAttendanceForStudent(studentId: Int): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForStudent(studentId)
    }

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(date)
    }

    suspend fun markAttendance(studentId: Int, date: String, isPresent: Boolean) {
        // delete first to ensure no duplicates for same student on same date
        attendanceDao.deleteAttendanceForDate(studentId, date)
        
        val record = AttendanceRecord(
            studentId = studentId,
            date = date,
            isPresent = isPresent
        )
        attendanceDao.insertAttendanceRecord(record)
    }

    suspend fun removeAttendanceForDate(studentId: Int, date: String) {
        attendanceDao.deleteAttendanceForDate(studentId, date)
    }

    fun getMemorizationsForStudent(studentId: Int): Flow<List<StudentMemorization>> {
        return memorizationDao.getMemorizationsForStudent(studentId)
    }

    suspend fun toggleMemorization(studentId: Int, surahIndex: Int, isMemorized: Boolean) {
        if (isMemorized) {
            val memo = StudentMemorization(studentId = studentId, surahIndex = surahIndex)
            memorizationDao.insertMemorization(memo)
        } else {
            memorizationDao.deleteMemorization(studentId, surahIndex)
        }
    }
}
