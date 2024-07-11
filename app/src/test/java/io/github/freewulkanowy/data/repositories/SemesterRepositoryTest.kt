package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.TestDispatchersProvider
import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.db.dao.SemesterDao
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.getSemesterPojo
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate.now

class SemesterRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var semesterDb: SemesterDao

    private val student = getStudentEntity()

    private lateinit var semesterRepository: SemesterRepository

    @Before
    fun initTest() {
        MockKAnnotations.init(this)

        semesterRepository =
            SemesterRepository(semesterDb, wulkanowySdkFactory, TestDispatchersProvider())
    }

    @Test
    fun getSemesters_noSemesters() {
        val semesters = listOf(
            getSemesterPojo(1, 1, now().minusMonths(6), now().minusMonths(3)),
            getSemesterPojo(1, 2, now().minusMonths(3), now())
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns emptyList()
        coEvery { sdk.getSemesters() } returns semesters
        coEvery { semesterDb.removeOldAndSaveNew(any(), any()) } just Runs

        runBlocking { semesterRepository.getSemesters(student) }

        coVerify {
            semesterDb.removeOldAndSaveNew(
                oldItems = emptyList(),
                newItems = semesters.mapToEntities(student.studentId),
            )
        }
    }

    @Test
    fun getSemesters_invalidDiary_api() {
        val badSemesters = listOf(
            getSemesterPojo(0, 1, now().minusMonths(6), now().minusMonths(3)),
            getSemesterPojo(0, 2, now().minusMonths(3), now())
        )

        val goodSemesters = listOf(
            getSemesterPojo(122, 1, now().minusMonths(6), now().minusMonths(3)),
            getSemesterPojo(123, 2, now().minusMonths(3), now())
        )

        coEvery {
            semesterDb.loadAll(
                student.studentId,
                student.classId
            )
        } returns badSemesters.mapToEntities(student.studentId)
        coEvery { sdk.getSemesters() } returns goodSemesters
        coEvery { semesterDb.removeOldAndSaveNew(any(), any()) } just Runs

        val items =
            runBlocking { semesterRepository.getSemesters(student.copy(loginMode = Sdk.Mode.HEBE.name)) }
        assertEquals(2, items.size)
        assertEquals(0, items[0].diaryId)
    }

    @Test
    fun getSemesters_invalidDiary_scrapper() = runTest {
        val badSemesters = listOf(
            getSemesterPojo(0, 2, now().minusMonths(6), now()),
            getSemesterPojo(0, 2, now(), now().plusMonths(6)),
        )

        val goodSemesters = listOf(
            getSemesterPojo(1, 2, now().minusMonths(6), now()),
            getSemesterPojo(2, 3, now(), now().plusMonths(6)),
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returnsMany listOf(
            badSemesters.mapToEntities(student.studentId),
            badSemesters.mapToEntities(student.studentId),
            goodSemesters.mapToEntities(student.studentId)
        )
        coEvery { sdk.getSemesters() } returns goodSemesters
        coEvery { semesterDb.removeOldAndSaveNew(any(), any()) } just Runs

        val items = semesterRepository.getSemesters(
            student = student.copy(loginMode = Sdk.Mode.SCRAPPER.name)
        )
        assertEquals(2, items.size)
        assertNotEquals(0, items[0].diaryId)
    }

    @Test
    fun getSemesters_noCurrent() {
        val semesters = listOf(
            getSemesterEntity(1, 1, now().minusMonths(12), now().minusMonths(6)),
            getSemesterEntity(1, 2, now().minusMonths(6), now().minusMonths(1))
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns semesters

        val items = runBlocking { semesterRepository.getSemesters(student) }
        assertEquals(2, items.size)
    }

    @Test
    fun getSemesters_oneCurrent() {
        val semesters = listOf(
            getSemesterEntity(1, 1, now().minusMonths(6), now().minusMonths(3)),
            getSemesterEntity(1, 2, now().minusMonths(3), now())
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns semesters

        val items = runBlocking { semesterRepository.getSemesters(student) }
        assertEquals(2, items.size)
    }

    @Test
    fun getSemesters_doubleCurrent() {
        val semesters = listOf(
            getSemesterEntity(1, 1, now(), now()),
            getSemesterEntity(1, 2, now(), now())
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns semesters

        val items = runBlocking { semesterRepository.getSemesters(student) }
        assertEquals(2, items.size)
    }

    @Test
    fun getSemesters_noSemesters_refreshOnNoCurrent() {
        val semesters = listOf(
            getSemesterPojo(1, 1, now().minusMonths(6), now().minusMonths(3)),
            getSemesterPojo(1, 2, now().minusMonths(3), now())
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns emptyList()
        coEvery { sdk.getSemesters() } returns semesters
        coEvery { semesterDb.removeOldAndSaveNew(any(), any()) } just Runs

        runBlocking { semesterRepository.getSemesters(student, refreshOnNoCurrent = true) }

        coVerify {
            semesterDb.removeOldAndSaveNew(
                oldItems = emptyList(),
                newItems = semesters.mapToEntities(student.studentId),
            )
        }
    }

    @Test
    fun getSemesters_noCurrent_refreshOnNoCurrent() {
        val semestersWithNoCurrent = listOf(
            getSemesterEntity(1, 1, now().minusMonths(12), now().minusMonths(6)),
            getSemesterEntity(1, 2, now().minusMonths(6), now().minusMonths(1))
        )

        val newSemesters = listOf(
            getSemesterPojo(1, 1, now().minusMonths(12), now().minusMonths(6)),
            getSemesterPojo(1, 2, now().minusMonths(6), now().minusMonths(1)),

            getSemesterPojo(2, 1, now().minusMonths(1), now().plusMonths(5)),
            getSemesterPojo(2, 2, now().plusMonths(5), now().plusMonths(11)),
        )

        coEvery {
            semesterDb.loadAll(
                student.studentId,
                student.classId
            )
        } returns semestersWithNoCurrent
        coEvery { sdk.getSemesters() } returns newSemesters
        coEvery { semesterDb.removeOldAndSaveNew(any(), any()) } just Runs

        val items =
            runBlocking { semesterRepository.getSemesters(student, refreshOnNoCurrent = true) }
        assertEquals(2, items.size)
    }

    @Test
    fun getSemesters_doubleCurrent_refreshOnNoCurrent() = runTest {
        val semesters = listOf(
            getSemesterEntity(1, 1, now().minusMonths(1), now().plusMonths(1)),
            getSemesterEntity(1, 2, now().minusMonths(1), now().plusMonths(1))
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns semesters

        val items = semesterRepository.getSemesters(student, refreshOnNoCurrent = true)
        assertEquals(2, items.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getCurrentSemester_doubleCurrent() {
        val semesters = listOf(
            getSemesterEntity(1, 1, now(), now()),
            getSemesterEntity(1, 1, now(), now())
        )

        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns semesters

        runBlocking { semesterRepository.getCurrentSemester(student) }
    }

    @Test(expected = RuntimeException::class)
    fun getCurrentSemester_emptyList() {
        coEvery { semesterDb.loadAll(student.studentId, student.classId) } returns emptyList()
        coEvery { sdk.getSemesters() } returns emptyList()

        runBlocking { semesterRepository.getCurrentSemester(student) }
    }
}
