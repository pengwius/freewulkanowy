package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.CompletedLessonsDao
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDate.of
import io.github.freewulkanowy.sdk.pojo.CompletedLesson as SdkCompletedLesson

class CompletedLessonsRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var completedLessonDb: CompletedLessonsDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val semester = getSemesterEntity()

    private val student = getStudentEntity()

    private lateinit var completedLessonRepository: CompletedLessonsRepository

    private val remoteList = listOf(
        getCompletedLesson(of(2021, 1, 4)),
        getCompletedLesson(of(2021, 1, 7))
    )

    private val startDate = of(2021, 1, 4)

    private val endDate = of(2021, 1, 10)

    @Before
    fun initApi() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        completedLessonRepository =
            CompletedLessonsRepository(completedLessonDb, wulkanowySdkFactory, refreshHelper)
    }

    @Test
    fun `force refresh without difference`() = runTest {
        // prepare
        coEvery { sdk.getCompletedLessons(startDate, endDate) } returns remoteList
        coEvery { completedLessonDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester)),
            flowOf(remoteList.mapToEntities(semester))
        )
        coEvery { completedLessonDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = completedLessonRepository.getCompletedLessons(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getCompletedLessons(startDate, endDate) }
        coVerify { completedLessonDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            completedLessonDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match { it.isEmpty() },
            )
        }
    }

    @Test
    fun `force refresh with more items in remote`() = runTest {
        // prepare
        coEvery { sdk.getCompletedLessons(startDate, endDate) } returns remoteList
        coEvery { completedLessonDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.dropLast(1).mapToEntities(semester)),
            flowOf(
                remoteList.dropLast(1).mapToEntities(semester)
            ), // after fetch end before save result
            flowOf(remoteList.mapToEntities(semester))
        )
        coEvery { completedLessonDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = completedLessonRepository.getCompletedLessons(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true
        ).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getCompletedLessons(startDate, endDate) }
        coVerify { completedLessonDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            completedLessonDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(semester)[1]
                }
            )
        }
    }

    @Test
    fun `force refresh with more items in local`() = runTest {
        // prepare
        coEvery { sdk.getCompletedLessons(startDate, endDate) } returns remoteList.dropLast(1)
        coEvery { completedLessonDb.loadAll(1, 1, startDate, endDate) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(semester)),
            flowOf(remoteList.mapToEntities(semester)), // after fetch end before save result
            flowOf(remoteList.dropLast(1).mapToEntities(semester))
        )
        coEvery { completedLessonDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = completedLessonRepository.getCompletedLessons(
            student = student,
            semester = semester,
            start = startDate,
            end = endDate,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(1, res.dataOrNull?.size)
        coVerify { sdk.getCompletedLessons(startDate, endDate) }
        coVerify { completedLessonDb.loadAll(1, 1, startDate, endDate) }
        coVerify {
            completedLessonDb.removeOldAndSaveNew(
                oldItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(semester)[1]
                },
                newItems = match { it.isEmpty() },
            )
        }
    }

    private fun getCompletedLesson(date: LocalDate) = SdkCompletedLesson(
        date = date,
        subject = "",
        absence = "",
        resources = "",
        substitution = "",
        teacherSymbol = "",
        teacher = "",
        topic = "",
        number = 1
    )
}
