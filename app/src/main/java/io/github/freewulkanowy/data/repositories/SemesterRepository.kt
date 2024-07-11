package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.data.WulkanowySdkFactory
import io.github.freewulkanowy.data.db.dao.SemesterDao
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.utils.DispatchersProvider
import io.github.freewulkanowy.utils.getCurrentOrLast
import io.github.freewulkanowy.utils.isCurrent
import io.github.freewulkanowy.utils.uniqueSubtract
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemesterRepository @Inject constructor(
    private val semesterDb: SemesterDao,
    private val wulkanowySdkFactory: WulkanowySdkFactory,
    private val dispatchers: DispatchersProvider,
) {

    suspend fun getSemesters(
        student: Student,
        forceRefresh: Boolean = false,
        refreshOnNoCurrent: Boolean = false
    ) = withContext(dispatchers.io) {
        val semesters = semesterDb.loadAll(student.studentId, student.classId)

        if (isShouldFetch(student, semesters, forceRefresh, refreshOnNoCurrent)) {
            refreshSemesters(student)
            semesterDb.loadAll(student.studentId, student.classId)
        } else semesters
    }

    private fun isShouldFetch(
        student: Student,
        semesters: List<Semester>,
        forceRefresh: Boolean,
        refreshOnNoCurrent: Boolean
    ): Boolean {
        val isNoSemesters = semesters.isEmpty()

        val isRefreshOnModeChangeRequired = when {
            Sdk.Mode.valueOf(student.loginMode) != Sdk.Mode.HEBE -> {
                semesters.firstOrNull { it.isCurrent() }?.let {
                    0 == it.diaryId && 0 == it.kindergartenDiaryId
                } == true
            }

            else -> false
        }

        val isRefreshOnNoCurrentAppropriate =
            refreshOnNoCurrent && !semesters.any { semester -> semester.isCurrent() }

        return forceRefresh || isNoSemesters || isRefreshOnModeChangeRequired || isRefreshOnNoCurrentAppropriate
    }

    private suspend fun refreshSemesters(student: Student) {
        val new = wulkanowySdkFactory.create(student)
            .getSemesters()
            .mapToEntities(student.studentId)

        if (new.isEmpty()) {
            Timber.i("Empty semester list from SDK!")
            return
        }

        val old = semesterDb.loadAll(student.studentId, student.classId)
        semesterDb.removeOldAndSaveNew(
            oldItems = old uniqueSubtract new,
            newItems = new uniqueSubtract old,
        )
    }

    suspend fun getCurrentSemester(student: Student, forceRefresh: Boolean = false) =
        withContext(dispatchers.io) {
            getSemesters(student, forceRefresh).getCurrentOrLast()
        }
}
