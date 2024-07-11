package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.AttendanceDao
import io.github.freewulkanowy.data.db.dao.TimetableDao
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDate.of
import io.github.freewulkanowy.sdk.pojo.Attendance as SdkAttendance

class AttendanceRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var attendanceDb: AttendanceDao

    @MockK
    private lateinit var timetableDb: TimetableDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val semester = getSemesterEntity()

    private val student = getStudentEntity()

    private lateinit var attendanceRepository: AttendanceRepository

    private val remoteList = listOf(
        getAttendanceRemote(of(2021, 1, 4)),
        getAttendanceRemote(of(2021, 1, 7))
    )

    private val startDate = of(2021, 1, 4)

    private val endDate = of(2021, 1, 10)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false
        coEvery { timetableDb.load(any(), any(), any(), any()) } returns emptyList()

        attendanceRepository =
            AttendanceRepository(attendanceDb, timetableDb, wulkanowySdkFactory, refreshHelper)
    }

    @Test
    fun `force refresh without difference`() = runTest {
        // prepare
        coEvery { sdk.getAttendance(startDate, endDate) } returns remoteList
        coEvery { attendanceDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester, emptyList())),
            flowOf(remoteList.mapToEntities(semester, emptyList()))
        )
        coEvery { attendanceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = attendanceRepository.getAttendance(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true,
        ).toFirstResult()


        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getAttendance(startDate, endDate) }
        coVerify { attendanceDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            attendanceDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match { it.isEmpty() },
            )
        }
    }

    @Test
    fun `force refresh with more items in remote`() {
        // prepare
        coEvery { sdk.getAttendance(startDate, endDate) } returns remoteList
        coEvery { attendanceDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.dropLast(1).mapToEntities(semester, emptyList())),
            flowOf(
                remoteList.dropLast(1).mapToEntities(semester, emptyList())
            ), // after fetch end before save result
            flowOf(remoteList.mapToEntities(semester, emptyList()))
        )
        coEvery { attendanceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            attendanceRepository.getAttendance(
                student,
                semester,
                startDate,
                endDate,
                true
            ).toFirstResult()
        }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getAttendance(startDate, endDate) }
        coVerify { attendanceDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            attendanceDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(semester, emptyList())[1]
                },
            )
        }
    }

    @Test
    fun `force refresh with more items in local`() {
        // prepare
        coEvery { sdk.getAttendance(startDate, endDate) } returns remoteList.dropLast(1)
        coEvery { attendanceDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester, emptyList())),
            flowOf(
                remoteList.mapToEntities(
                    semester,
                    emptyList()
                )
            ), // after fetch end before save result
            flowOf(remoteList.dropLast(1).mapToEntities(semester, emptyList()))
        )
        coEvery { attendanceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            attendanceRepository.getAttendance(
                student,
                semester,
                startDate,
                endDate,
                true
            ).toFirstResult()
        }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(1, res.dataOrNull?.size)
        coVerify { sdk.getAttendance(startDate, endDate) }
        coVerify { attendanceDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            attendanceDb.removeOldAndSaveNew(
                oldItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(semester, emptyList())[1]
                },
                newItems = emptyList(),
            )
        }
    }

    private fun getAttendanceRemote(date: LocalDate) = SdkAttendance(
        subject = "Fizyka",
        name = "Obecność",
        date = date,
        timeId = 0,
        number = 0,
        deleted = false,
        excusable = false,
        excused = false,
        exemption = false,
        lateness = false,
        presence = false,
        categoryId = 1,
        absence = false,
        excuseStatus = null
    )
}
