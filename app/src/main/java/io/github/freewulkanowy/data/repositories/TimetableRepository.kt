package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.data.WulkanowySdkFactory
import io.github.freewulkanowy.data.db.dao.TimetableAdditionalDao
import io.github.freewulkanowy.data.db.dao.TimetableDao
import io.github.freewulkanowy.data.db.dao.TimetableHeaderDao
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.db.entities.Timetable
import io.github.freewulkanowy.data.db.entities.TimetableAdditional
import io.github.freewulkanowy.data.db.entities.TimetableHeader
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.networkBoundResource
import io.github.freewulkanowy.data.pojos.TimetableFull
import io.github.freewulkanowy.services.alarm.TimetableNotificationSchedulerHelper
import io.github.freewulkanowy.ui.modules.timetablewidget.TimetableWidgetProvider
import io.github.freewulkanowy.utils.AppWidgetUpdater
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.github.freewulkanowy.utils.getRefreshKey
import io.github.freewulkanowy.utils.monday
import io.github.freewulkanowy.utils.sunday
import io.github.freewulkanowy.utils.uniqueSubtract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TimetableRepository @Inject constructor(
    private val timetableDb: TimetableDao,
    private val timetableAdditionalDb: TimetableAdditionalDao,
    private val timetableHeaderDb: TimetableHeaderDao,
    private val wulkanowySdkFactory: WulkanowySdkFactory,
    private val schedulerHelper: TimetableNotificationSchedulerHelper,
    private val refreshHelper: AutoRefreshHelper,
    private val appWidgetUpdater: AppWidgetUpdater,
) {

    private val saveFetchResultMutex = Mutex()

    private val cacheKey = "timetable"

    enum class TimetableType {
        NORMAL, ADDITIONAL
    }

    fun getTimetable(
        student: Student,
        semester: Semester,
        start: LocalDate,
        end: LocalDate,
        forceRefresh: Boolean,
        refreshAdditional: Boolean = false,
        notify: Boolean = false,
        timetableType: TimetableType = TimetableType.NORMAL,
        isFromAppWidget: Boolean = false
    ) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = {
            when (timetableType) {
                TimetableType.NORMAL -> it.lessons.isEmpty()
                TimetableType.ADDITIONAL -> it.additional.isEmpty()
            }
        },
        shouldFetch = { (timetable, additional, headers) ->
            val refreshKey = getRefreshKey(cacheKey, semester, start, end)
            val isExpired = refreshHelper.shouldBeRefreshed(refreshKey)
            val isRefreshAdditional = additional.isEmpty() && refreshAdditional

            val isNoData = timetable.isEmpty() || isRefreshAdditional || headers.isEmpty()

            isNoData || forceRefresh || isExpired
        },
        query = { getFullTimetableFromDatabase(student, semester, start, end) },
        fetch = {
            val timetableFull = wulkanowySdkFactory.create(student, semester)
                .getTimetable(start.monday, end.sunday)

            timetableFull.mapToEntities(semester)
        },
        saveFetchResult = { timetableOld, timetableNew ->
            Timber.i("New timetable: %s", timetableNew.toString())
            refreshTimetable(student, timetableOld.lessons, timetableNew.lessons, notify)
            refreshAdditional(timetableOld.additional, timetableNew.additional)
            refreshDayHeaders(timetableOld.headers, timetableNew.headers)

            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(cacheKey, semester, start, end))
            if (!isFromAppWidget) {
                appWidgetUpdater.updateAllAppWidgetsByProvider(TimetableWidgetProvider::class)
            }
        },
        filterResult = { (timetable, additional, headers) ->
            TimetableFull(
                lessons = timetable.filter { it.date in start..end },
                additional = additional.filter { it.date in start..end },
                headers = headers.filter { it.date in start..end }
            )
        }
    )

    private fun getFullTimetableFromDatabase(
        student: Student,
        semester: Semester,
        start: LocalDate,
        end: LocalDate,
    ): Flow<TimetableFull> {
        val timetableFlow = timetableDb.loadAll(
            diaryId = semester.diaryId,
            studentId = semester.studentId,
            from = start.monday,
            end = end.sunday
        )
        val headersFlow = timetableHeaderDb.loadAll(
            diaryId = semester.diaryId,
            studentId = semester.studentId,
            from = start.monday,
            end = end.sunday
        )
        val additionalFlow = timetableAdditionalDb.loadAll(
            diaryId = semester.diaryId,
            studentId = semester.studentId,
            from = start.monday,
            end = end.sunday
        )
        return combine(timetableFlow, headersFlow, additionalFlow) { lessons, headers, additional ->
            schedulerHelper.scheduleNotifications(lessons, student)

            TimetableFull(
                lessons = lessons,
                headers = headers,
                additional = additional
            )
        }
    }

    suspend fun getTimetableFromDatabase(
        semester: Semester,
        start: LocalDate,
        end: LocalDate
    ): List<Timetable> {
        return timetableDb.load(semester.diaryId, semester.studentId, start, end)
    }

    suspend fun updateTimetable(timetable: List<Timetable>) {
        return timetableDb.updateAll(timetable)
    }

    private suspend fun refreshTimetable(
        student: Student,
        lessonsOld: List<Timetable>,
        lessonsNew: List<Timetable>,
        notify: Boolean
    ) {
        val lessonsToRemove = lessonsOld uniqueSubtract lessonsNew
        val lessonsToAdd = (lessonsNew uniqueSubtract lessonsOld).map { new ->
            new.apply { if (notify) isNotified = false }
        }

        timetableDb.removeOldAndSaveNew(
            oldItems = lessonsToRemove,
            newItems = lessonsToAdd,
        )

        schedulerHelper.cancelScheduled(lessonsToRemove, student)
        schedulerHelper.scheduleNotifications(lessonsToAdd, student)
    }

    private suspend fun refreshAdditional(
        old: List<TimetableAdditional>,
        new: List<TimetableAdditional>
    ) {
        val oldFiltered = old.filter { !it.isAddedByUser }
        timetableAdditionalDb.removeOldAndSaveNew(
            oldItems = oldFiltered uniqueSubtract new,
            newItems = new uniqueSubtract old,
        )
    }

    private suspend fun refreshDayHeaders(old: List<TimetableHeader>, new: List<TimetableHeader>) {
        timetableHeaderDb.removeOldAndSaveNew(
            oldItems = old uniqueSubtract new,
            newItems = new uniqueSubtract old,
        )
    }

    fun getLastRefreshTimestamp(semester: Semester, start: LocalDate, end: LocalDate): Instant {
        val refreshKey = getRefreshKey(cacheKey, semester, start, end)
        return refreshHelper.getLastRefreshTimestamp(refreshKey)
    }

    suspend fun saveAdditionalList(additionalList: List<TimetableAdditional>) =
        timetableAdditionalDb.insertAll(additionalList)

    suspend fun deleteAdditional(additional: TimetableAdditional, deleteSeries: Boolean) =
        if (deleteSeries) {
            timetableAdditionalDb.deleteAllByRepeatId(additional.repeatId!!)
        } else {
            timetableAdditionalDb.deleteAll(listOf(additional))
        }
}
