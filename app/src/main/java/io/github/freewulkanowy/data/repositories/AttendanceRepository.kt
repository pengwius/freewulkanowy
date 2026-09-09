package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.data.WulkanowySdkFactory
import io.github.freewulkanowy.data.db.dao.AttendanceDao
import io.github.freewulkanowy.data.db.dao.TimetableDao
import io.github.freewulkanowy.data.db.entities.Attendance
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.networkBoundResource
import io.github.freewulkanowy.sdk.pojo.Absent
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.github.freewulkanowy.utils.getRefreshKey
import io.github.freewulkanowy.utils.monday
import io.github.freewulkanowy.utils.sunday
import io.github.freewulkanowy.utils.uniqueSubtract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val attendanceDb: AttendanceDao,
    private val timetableDb: TimetableDao,
    private val wulkanowySdkFactory: WulkanowySdkFactory,
    private val refreshHelper: AutoRefreshHelper,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "attendance"

    fun getAttendance(
        student: Student,
        semester: Semester,
        start: LocalDate,
        end: LocalDate,
        forceRefresh: Boolean,
        notify: Boolean = false,
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(
                key = getRefreshKey(cacheKey, semester, start, end)
            )
            it.isEmpty() || forceRefresh || isExpired
        },
        query = {
            attendanceDb.loadAll(semester.diaryId, semester.studentId, start.monday, end.sunday)
        },
        fetch = {
            val lessons = timetableDb.load(
                semester.diaryId, semester.studentId, start.monday, end.sunday
            )
            wulkanowySdkFactory.create(student, semester)
                .getAttendance(start.monday, end.sunday)
                .mapToEntities(semester, lessons)
        },
        saveFetchResult = { old, new ->
            val attendanceToAdd = (new uniqueSubtract old).map { newAttendance ->
                newAttendance.apply { if (notify) isNotified = false }
            }
            attendanceDb.removeOldAndSaveNew(
                oldItems = old uniqueSubtract new,
                newItems = attendanceToAdd,
            )
            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester, start, end))
        },
        filterResult = { it.filter { item -> item.date in start..end } }
    )

    fun getAttendanceFromDatabase(
        semester: Semester,
        start: LocalDate,
        end: LocalDate
    ): Flow<List<Attendance>> {
        return attendanceDb.loadAll(semester.diaryId, semester.studentId, start, end)
    }

    suspend fun updateTimetable(timetable: List<Attendance>) {
        return attendanceDb.updateAll(timetable)
    }

    suspend fun excuseForAbsence(
        student: Student,
        semester: Semester,
        absenceList: List<Attendance>,
        reason: String? = null
    ) {
        val items = absenceList.map { attendance ->
            Absent(
                date = LocalDateTime.of(attendance.date, LocalTime.of(0, 0)),
                timeId = attendance.timeId
            )
        }
        wulkanowySdkFactory.create(student, semester)
            .excuseForAbsence(items, reason)
    }
}
