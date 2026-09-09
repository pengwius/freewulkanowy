package io.github.freewulkanowy.data.repositories

import androidx.room.withTransaction
import io.github.freewulkanowy.data.WulkanowySdkFactory
import io.github.freewulkanowy.data.db.AppDatabase
import io.github.freewulkanowy.data.db.dao.SemesterDao
import io.github.freewulkanowy.data.db.dao.StudentDao
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.db.entities.StudentIsAuthorized
import io.github.freewulkanowy.data.db.entities.StudentName
import io.github.freewulkanowy.data.db.entities.StudentNickAndAvatar
import io.github.freewulkanowy.data.db.entities.StudentWithSemesters
import io.github.freewulkanowy.data.exceptions.NoCurrentStudentException
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.mappers.mapToPojo
import io.github.freewulkanowy.data.pojos.RegisterUser
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.utils.DispatchersProvider
import io.github.freewulkanowy.utils.security.Scrambler
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val dispatchers: DispatchersProvider,
    private val studentDb: StudentDao,
    private val semesterDb: SemesterDao,
    private val wulkanowySdkFactory: WulkanowySdkFactory,
    private val appDatabase: AppDatabase,
    private val scrambler: Scrambler,
) {

    suspend fun isCurrentStudentSet() = studentDb.loadCurrent()?.isCurrent ?: false

    suspend fun getStudentsApi(
        pin: String,
        symbol: String,
        token: String
    ): RegisterUser = wulkanowySdkFactory.create()
        .getStudentsFromHebe(token, pin, symbol, "")
        .mapToPojo(null)
        .also { it.logErrors() }

    suspend fun getUserSubjectsFromScrapper(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        domainSuffix: String,
        symbol: String
    ): RegisterUser = wulkanowySdkFactory.create()
        .getUserSubjectsFromScrapper(email, password, scrapperBaseUrl, domainSuffix, symbol)
        .mapToPojo(password)
        .also { it.logErrors() }

    suspend fun getStudentsHybrid(
        email: String,
        password: String,
        scrapperBaseUrl: String,
        domainSuffix: String,
        symbol: String
    ): RegisterUser = wulkanowySdkFactory.create()
        .getStudentsHybrid(email, password, scrapperBaseUrl, domainSuffix, symbol)
        .mapToPojo(password)
        .also { it.logErrors() }

    suspend fun getSavedStudents(decryptPass: Boolean = true): List<StudentWithSemesters> {
        return studentDb.loadStudentsWithSemesters().map { (student, semesters) ->
            StudentWithSemesters(
                student = student.apply {
                    if (decryptPass && Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE) {
                        student.password = withContext(dispatchers.io) {
                            scrambler.decrypt(student.password)
                        }
                    }
                },
                semesters = semesters,
            )
        }
    }

    suspend fun getSavedStudentById(id: Long, decryptPass: Boolean = true): StudentWithSemesters? =
        studentDb.loadStudentWithSemestersById(id).let { res ->
            StudentWithSemesters(
                student = res.keys.firstOrNull() ?: return null,
                semesters = res.values.first(),
            )
        }.apply {
            if (decryptPass && Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE) {
                student.password = withContext(dispatchers.io) {
                    scrambler.decrypt(student.password)
                }
            }
        }

    suspend fun getStudentById(id: Long, decryptPass: Boolean = true): Student {
        val student = studentDb.loadById(id) ?: throw NoCurrentStudentException()

        if (decryptPass && Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE) {
            student.password = withContext(dispatchers.io) {
                scrambler.decrypt(student.password)
            }
        }
        return student
    }

    suspend fun updateCurrentStudentAuthStatus() {
        Timber.i("Check isAuthorized: started")
        val student = getCurrentStudent()
        if (student.isAuthorized) {
            Timber.i("Check isAuthorized: already authorized")
            return
        }

        val initializedSdk = wulkanowySdkFactory.create(student)
        val newCurrentStudent = runCatching { initializedSdk.getCurrentStudent() }
            .onFailure { Timber.e(it, "Check isAuthorized: error occurred") }
            .getOrNull()

        if (newCurrentStudent == null) {
            Timber.d("Check isAuthorized: current user is null")
            return
        }

        val currentStudentSemesters = semesterDb.loadAll(student.studentId, student.classId)
        if (currentStudentSemesters.isEmpty()) {
            Timber.d("Check isAuthorized: apply empty semesters workaround")
            semesterDb.insertSemesters(
                items = newCurrentStudent.semesters.mapToEntities(student.studentId),
            )
        }

        if (!newCurrentStudent.isAuthorized) {
            Timber.i("Check isAuthorized: authorization required")
            throw NoAuthorizationException()
        }

        val studentIsAuthorized = StudentIsAuthorized(
            id = student.id,
            isAuthorized = true
        )

        Timber.i("Check isAuthorized: already authorized, update local status")
        studentDb.update(studentIsAuthorized)
    }

    suspend fun getCurrentStudent(decryptPass: Boolean = true): Student {
        val student = studentDb.loadCurrent() ?: throw NoCurrentStudentException()

        if (decryptPass && Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE) {
            student.password = withContext(dispatchers.io) {
                scrambler.decrypt(student.password)
            }
        }
        return student
    }

    suspend fun saveStudents(studentsWithSemesters: List<StudentWithSemesters>) {
        val semesters = studentsWithSemesters.flatMap { it.semesters }
        val students = studentsWithSemesters.map { it.student }
            .map {
                it.apply {
                    if (Sdk.Mode.valueOf(it.loginMode) != Sdk.Mode.HEBE) {
                        password = withContext(dispatchers.io) {
                            scrambler.encrypt(password)
                        }
                    }
                }
            }
            .mapIndexed { index, student ->
                if (index == 0) {
                    student.copy(isCurrent = true).apply { avatarColor = student.avatarColor }
                } else student
            }

        appDatabase.withTransaction {
            studentDb.resetCurrent()
            semesterDb.insertSemesters(semesters)
            studentDb.insertAll(students)
        }
    }

    suspend fun switchStudent(studentWithSemesters: StudentWithSemesters) {
        studentDb.switchCurrent(studentWithSemesters.student.id)
    }

    suspend fun logoutStudent(student: Student) = studentDb.delete(student)

    suspend fun updateStudentNickAndAvatar(studentNickAndAvatar: StudentNickAndAvatar) =
        studentDb.update(studentNickAndAvatar)

    suspend fun isOneUniqueStudent() = getSavedStudents(false)
        .distinctBy { it.student.studentName }.size == 1

    suspend fun authorizePermission(student: Student, semester: Semester, pesel: String) =
        wulkanowySdkFactory.create(student, semester)
            .authorizePermission(pesel)

    suspend fun refreshStudentAfterAuthorize(student: Student, semester: Semester) {
        val wulkanowySdk = wulkanowySdkFactory.create(student, semester)
            val newCurrentApiStudent = runCatching { wulkanowySdk.getCurrentStudent() }
            .onFailure { Timber.e(it, "Can't find student with id ${student.studentId}") }
            .getOrNull() ?: return

        val studentName = StudentName(
            studentName = "${newCurrentApiStudent.studentName} ${newCurrentApiStudent.studentSurname}"
        ).apply { id = student.id }

        studentDb.update(studentName)
        semesterDb.removeOldAndSaveNew(
            oldItems = semesterDb.loadAll(student.studentId, semester.classId),
            newItems = newCurrentApiStudent.semesters.mapToEntities(newCurrentApiStudent.studentId)
        )
    }

    suspend fun deleteStudentsAssociatedWithAccount(student: Student) {
        studentDb.deleteByEmailAndUserName(student.email, student.userName)
    }

    suspend fun clearAll() {
        withContext(dispatchers.io) {
            scrambler.clearKeyPair()
            appDatabase.clearAllTables()
        }
    }

    private fun RegisterUser.logErrors() {
        val symbolsErrors = symbols.filter { it.error != null }
            .map { it.error }
        val unitsErrors = symbols.flatMap { it.schools }
            .filter { it.error != null }
            .map { it.error }

        (symbolsErrors + unitsErrors).forEach { error ->
            Timber.e(error, "Error occurred while fetching students")
        }
    }
}

class NoAuthorizationException : Exception()
