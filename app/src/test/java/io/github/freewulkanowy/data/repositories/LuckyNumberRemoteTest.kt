package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.LuckyNumberDao
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.mappers.mapToEntity
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.utils.AppWidgetUpdater
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
import io.github.freewulkanowy.sdk.pojo.LuckyNumber as SdkLuckyNumber

class LuckyNumberRemoteTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var luckyNumberDb: LuckyNumberDao

    @MockK(relaxed = true)
    private lateinit var appWidgetUpdater: AppWidgetUpdater

    private val student = getStudentEntity()

    private lateinit var luckyNumberRepository: LuckyNumberRepository

    private val luckyNumber = SdkLuckyNumber("", "", 14)

    private val date = LocalDate.now()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        luckyNumberRepository = LuckyNumberRepository(
            luckyNumberDb = luckyNumberDb,
            wulkanowySdkFactory = wulkanowySdkFactory,
            appWidgetUpdater = appWidgetUpdater,
        )
    }

    @Test
    fun `force refresh without difference`() {
        // prepare
        coEvery { sdk.getLuckyNumber(student.schoolShortName) } returns luckyNumber
        coEvery { luckyNumberDb.load(1, date) } returnsMany listOf(
            flowOf(luckyNumber.mapToEntity(student)),
            flowOf(luckyNumber.mapToEntity(student))
        )
        coEvery { luckyNumberDb.insertAll(any()) } returns listOf(1, 2, 3)
        coEvery { luckyNumberDb.deleteAll(any()) } just Runs

        // execute
        val res =
            runBlocking { luckyNumberRepository.getLuckyNumber(student, true).toFirstResult() }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(luckyNumber.number, res.dataOrNull?.luckyNumber)
        coVerify { sdk.getLuckyNumber(student.schoolShortName) }
        coVerify { luckyNumberDb.load(1, date) }
        coVerify(exactly = 0) { luckyNumberDb.insertAll(any()) }
        coVerify(exactly = 0) { luckyNumberDb.deleteAll(any()) }
    }

    @Test
    fun `force refresh with different item on remote`() = runTest {
        // prepare
        coEvery { sdk.getLuckyNumber(student.schoolShortName) } returns luckyNumber
        coEvery { luckyNumberDb.load(1, date) } returnsMany listOf(
            flowOf(luckyNumber.mapToEntity(student).copy(luckyNumber = 6666)),
            // after fetch end before save result
            flowOf(luckyNumber.mapToEntity(student).copy(luckyNumber = 6666)),
            flowOf(luckyNumber.mapToEntity(student))
        )
        coEvery { luckyNumberDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = luckyNumberRepository.getLuckyNumber(student, true).toFirstResult()

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(luckyNumber.number, res.dataOrNull?.luckyNumber)
        coVerify { sdk.getLuckyNumber(student.schoolShortName) }
        coVerify { luckyNumberDb.load(1, date) }
        coVerify {
            luckyNumberDb.removeOldAndSaveNew(
                oldItems = match {
                    it.size == 1 && it[0] == luckyNumber.mapToEntity(student)
                        .copy(luckyNumber = 6666)
                },
                newItems = match {
                    it.size == 1 && it[0] == luckyNumber.mapToEntity(student)
                }
            )
        }
    }

    @Test
    fun `force refresh no local item`() {
        // prepare
        coEvery { sdk.getLuckyNumber(student.schoolShortName) } returns luckyNumber
        coEvery { luckyNumberDb.load(1, date) } returnsMany listOf(
            flowOf(null),
            flowOf(null), // after fetch end before save result
            flowOf(luckyNumber.mapToEntity(student))
        )
        coEvery { luckyNumberDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res =
            runBlocking { luckyNumberRepository.getLuckyNumber(student, true).toFirstResult() }

        // verify
        assertEquals(null, res.errorOrNull)
        assertEquals(luckyNumber.number, res.dataOrNull?.luckyNumber)
        coVerify { sdk.getLuckyNumber(student.schoolShortName) }
        coVerify { luckyNumberDb.load(1, date) }
        coVerify {
            luckyNumberDb.removeOldAndSaveNew(
                oldItems = emptyList(),
                newItems = match {
                    it.size == 1 && it[0] == luckyNumber.mapToEntity(student)
                }
            )
        }
    }
}
