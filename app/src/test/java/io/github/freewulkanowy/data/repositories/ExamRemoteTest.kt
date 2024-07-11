package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.ExamDao
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
import io.github.freewulkanowy.sdk.pojo.Exam as SdkExam

class ExamRemoteTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var examDb: ExamDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val semester = getSemesterEntity()

    private val student = getStudentEntity()

    private lateinit var examRepository: ExamRepository

    private val remoteList = listOf(
        getExam(LocalDate.of(2021, 1, 4)),
        getExam(LocalDate.of(2021, 1, 7))
    )

    private val startDate = LocalDate.of(2021, 1, 4)

    private val endDate = LocalDate.of(2021, 1, 10)

    private val realEndDate = LocalDate.of(2021, 1, 31)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        examRepository = ExamRepository(examDb, wulkanowySdkFactory, refreshHelper)
    }

    @Test
    fun `force refresh without difference`() {
        // prepare
        coEvery { sdk.getExams(startDate, realEndDate) } returns remoteList
        coEvery { examDb.loadAll(1, 1, startDate, realEndDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester)),
            flowOf(remoteList.mapToEntities(semester))
        )
        coEvery { examDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            examRepository.getExams(student, semester, startDate, endDate, true).toFirstResult()
        }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getExams(startDate, realEndDate) }
        coVerify { examDb.loadAll(1, 1, startDate, realEndDate) }
        coVerify { examDb.removeOldAndSaveNew(emptyList(), emptyList()) }
    }

    @Test
    fun `force refresh with more items in remote`() = runTest {
        // prepare
        coEvery { sdk.getExams(startDate, realEndDate) } returns remoteList
        coEvery { examDb.loadAll(1, 1, startDate, realEndDate) } returnsMany listOf(
            flowOf(remoteList.dropLast(1).mapToEntities(semester)),
            flowOf(
                remoteList.dropLast(1).mapToEntities(semester)
            ), // after fetch end before save result
            flowOf(remoteList.mapToEntities(semester))
        )
        coEvery { examDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = examRepository.getExams(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getExams(startDate, realEndDate) }
        coVerify { examDb.loadAll(1, 1, startDate, realEndDate) }
        coVerify {
            examDb.removeOldAndSaveNew(
                oldItems = emptyList(),
                newItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(semester)[1]
                },
            )
        }
    }

    @Test
    fun `force refresh with more items in local`() = runTest {
        // prepare
        coEvery { sdk.getExams(startDate, realEndDate) } returns remoteList.dropLast(1)
        coEvery { examDb.loadAll(1, 1, startDate, realEndDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester)),
            flowOf(remoteList.mapToEntities(semester)), // after fetch end before save result
            flowOf(remoteList.dropLast(1).mapToEntities(semester))
        )
        coEvery { examDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = examRepository.getExams(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(1, res.dataOrNull?.size)
        coVerify { sdk.getExams(startDate, realEndDate) }
        coVerify { examDb.loadAll(1, 1, startDate, realEndDate) }
        coVerify {
            examDb.removeOldAndSaveNew(
                oldItems = match { it.size == 1 && it[0] == remoteList.mapToEntities(semester)[1] },
                newItems = emptyList()
            )
        }
    }

    private fun getExam(date: LocalDate) = SdkExam(
        subject = "",
        type = "",
        description = "",
        teacher = "",
        teacherSymbol = "",
        date = date,
        entryDate = date
    )
}
