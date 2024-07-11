package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.TimetableAdditionalDao
import io.github.freewulkanowy.data.db.dao.TimetableDao
import io.github.freewulkanowy.data.db.dao.TimetableHeaderDao
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.services.alarm.TimetableNotificationSchedulerHelper
import io.github.freewulkanowy.utils.AppWidgetUpdater
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalDateTime.of
import java.time.ZoneId
import io.github.freewulkanowy.sdk.pojo.Lesson as SdkLesson

class TimetableRepositoryTest {

    @MockK(relaxed = true)
    private lateinit var timetableNotificationSchedulerHelper: TimetableNotificationSchedulerHelper

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var timetableDb: TimetableDao

    @MockK
    private lateinit var timetableAdditionalDao: TimetableAdditionalDao

    @MockK
    private lateinit var timetableHeaderDao: TimetableHeaderDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    @MockK(relaxed = true)
    private lateinit var appWidgetUpdater: AppWidgetUpdater

    private val student = getStudentEntity()

    private val semester = getSemesterEntity()

    private lateinit var timetableRepository: TimetableRepository

    private val startDate = LocalDate.of(2021, 1, 4)

    private val endDate = LocalDate.of(2021, 1, 10)

    @Before
    fun initApi() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        timetableRepository = TimetableRepository(
            timetableDb,
            timetableAdditionalDao,
            timetableHeaderDao,
            wulkanowySdkFactory,
            timetableNotificationSchedulerHelper,
            refreshHelper,
            appWidgetUpdater
        )
    }

    @Test
    fun `force refresh without difference`() {
        val remoteList = listOf(
            createTimetableRemote(
                start = of(2021, 1, 4, 8, 0),
                number = 1,
                room = "123",
                subject = "Język polski",
                teacher = "Jan Kowalski",
                changes = false
            ),
            createTimetableRemote(
                start = of(2021, 1, 4, 8, 50),
                number = 2,
                room = "124",
                subject = "Język niemiecki",
                teacher = "Joanna Czarniecka",
                changes = true
            )
        )

        // prepare
        coEvery { sdk.getTimetable(startDate, endDate) } returns mockk {
            every { headers } returns emptyList()
            every { lessons } returns remoteList
            every { additional } returns emptyList()
        }
        coEvery { timetableDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester)),
            flowOf(remoteList.mapToEntities(semester))
        )
        coEvery { timetableDb.removeOldAndSaveNew(any(), any()) } just Runs

        coEvery {
            timetableAdditionalDao.loadAll(
                diaryId = 1,
                studentId = 1,
                from = startDate,
                end = endDate
            )
        } returns flowOf(listOf())
        coEvery { timetableAdditionalDao.removeOldAndSaveNew(any(), any()) } just Runs

        coEvery { timetableHeaderDao.loadAll(1, 1, startDate, endDate) } returns flowOf(listOf())
        coEvery { timetableHeaderDao.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            timetableRepository.getTimetable(
                student = student,
                semester = semester,
                start = startDate,
                end = endDate,
                forceRefresh = true
            ).toFirstResult()
        }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull!!.lessons.size)
        coVerify { sdk.getTimetable(startDate, endDate) }
        coVerify { timetableDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            timetableDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match { it.isEmpty() },
            )
        }
    }

    private fun createTimetableRemote(
        start: LocalDateTime,
        number: Int = 1,
        room: String = "",
        subject: String = "",
        teacher: String = "",
        changes: Boolean = false
    ) = SdkLesson(
        number = number,
        start = start.atZone(ZoneId.systemDefault()),
        end = start.plusMinutes(45).atZone(ZoneId.systemDefault()),
        date = start.toLocalDate(),
        subject = subject,
        group = "",
        room = room,
        teacher = teacher,
        info = "",
        changes = changes,
        canceled = false,
        roomOld = "",
        subjectOld = "",
        teacherOld = "",
        studentPlan = true
    )
}
