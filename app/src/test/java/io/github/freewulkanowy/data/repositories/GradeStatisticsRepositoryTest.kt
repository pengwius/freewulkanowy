package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.GradePartialStatisticsDao
import io.github.freewulkanowy.data.db.dao.GradePointsStatisticsDao
import io.github.freewulkanowy.data.db.dao.GradeSemesterStatisticsDao
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.pojo.GradeStatisticsItem
import io.github.freewulkanowy.sdk.pojo.GradeStatisticsSubject
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GradeStatisticsRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var gradePartialStatisticsDb: GradePartialStatisticsDao

    @MockK
    private lateinit var gradePointsStatisticsDb: GradePointsStatisticsDao

    @MockK
    private lateinit var gradeSemesterStatisticsDb: GradeSemesterStatisticsDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val semester = getSemesterEntity()

    private val student = getStudentEntity()

    private lateinit var gradeStatisticsRepository: GradeStatisticsRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        gradeStatisticsRepository = GradeStatisticsRepository(
            gradePartialStatisticsDb = gradePartialStatisticsDb,
            gradePointsStatisticsDb = gradePointsStatisticsDb,
            gradeSemesterStatisticsDb = gradeSemesterStatisticsDb,
            wulkanowySdkFactory = wulkanowySdkFactory,
            refreshHelper = refreshHelper,
        )
    }

    @Test
    fun `force refresh without difference`() {
        // prepare
        val remotePartialList = listOf(
            getGradeStatisticsPartialSubject("Fizyka"),
            getGradeStatisticsPartialSubject("Matematyka")
        )
        coEvery { sdk.getGradesPartialStatistics(1) } returns remotePartialList
        coEvery { gradePartialStatisticsDb.loadAll(1, 1) } returnsMany listOf(
            flowOf(remotePartialList.mapToEntities(semester)),
            flowOf(remotePartialList.mapToEntities(semester))
        )
        coEvery { gradePartialStatisticsDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            gradeStatisticsRepository.getGradesPartialStatistics(
                student = student,
                semester = semester,
                subjectName = "Wszystkie",
                forceRefresh = true,
            ).toFirstResult()
        }
        val items = res.dataOrNull.orEmpty()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2 + 1, res.dataOrNull?.size)
        assertEquals("", items[0].partial?.studentAverage)
        assertEquals("", items[1].partial?.studentAverage)
        assertEquals("", items[2].partial?.studentAverage)
        coVerify { sdk.getGradesPartialStatistics(1) }
        coVerify { gradePartialStatisticsDb.loadAll(1, 1) }
        coVerify { gradePartialStatisticsDb.removeOldAndSaveNew(emptyList(), emptyList()) }
    }

    @Test
    fun `force refresh without difference with filled up items`() {
        // prepare
        val remotePartialList = listOf(
            getGradeStatisticsPartialSubject("Fizyka", "1.0"),
            getGradeStatisticsPartialSubject("Matematyka", "5.0")
        )
        coEvery { sdk.getGradesPartialStatistics(1) } returns remotePartialList
        coEvery { gradePartialStatisticsDb.loadAll(1, 1) } returnsMany listOf(
            flowOf(remotePartialList.mapToEntities(semester)),
            flowOf(remotePartialList.mapToEntities(semester))
        )
        coEvery { gradePartialStatisticsDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            gradeStatisticsRepository.getGradesPartialStatistics(
                student = student,
                semester = semester,
                subjectName = "Wszystkie",
                forceRefresh = true,
            ).toFirstResult()
        }
        val items = res.dataOrNull.orEmpty()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2 + 1, res.dataOrNull?.size)
        assertEquals("3,00", items[0].partial?.studentAverage)
        assertEquals("1.0", items[1].partial?.studentAverage)
        assertEquals("5.0", items[2].partial?.studentAverage)
        coVerify { sdk.getGradesPartialStatistics(1) }
        coVerify { gradePartialStatisticsDb.loadAll(1, 1) }
        coVerify { gradePartialStatisticsDb.removeOldAndSaveNew(emptyList(), emptyList()) }
    }

    private fun getGradeStatisticsPartialSubject(
        subjectName: String,
        studentAverage: String = "",
        classAverage: String = "",
    ) = GradeStatisticsSubject(
        subject = subjectName,
        studentAverage = studentAverage,
        classAverage = classAverage,
        classItems = listOf(
            GradeStatisticsItem(
                subject = subjectName,
                grade = 0,
                amount = 0
            )
        ),
        studentItems = listOf()
    )
}
