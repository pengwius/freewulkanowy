package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.dao.MobileDeviceDao
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.pojo.Device
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime.of

class MobileDeviceRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var mobileDeviceDb: MobileDeviceDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val semester = getSemesterEntity()

    private val student = getStudentEntity()

    private lateinit var mobileDeviceRepository: MobileDeviceRepository

    private val remoteList = listOf(
        getDevicePojo(1),
        getDevicePojo(2)
    )

    @Before
    fun initTest() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        mobileDeviceRepository =
            MobileDeviceRepository(mobileDeviceDb, wulkanowySdkFactory, refreshHelper)
    }

    @Test
    fun `force refresh without difference`() = runTest {
        // prepare
        coEvery { sdk.getRegisteredDevices() } returns remoteList
        coEvery { mobileDeviceDb.loadAll(student.studentId) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(student)),
            flowOf(remoteList.mapToEntities(student))
        )
        coEvery { mobileDeviceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = mobileDeviceRepository.getDevices(
            student = student,
            semester = semester,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        Assert.assertEquals(null, res.errorOrNull)
        Assert.assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getRegisteredDevices() }
        coVerify { mobileDeviceDb.loadAll(1) }
        coVerify {
            mobileDeviceDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match { it.isEmpty() },
            )
        }
    }

    @Test
    fun `force refresh with more items in remote`() = runTest {
        // prepare
        coEvery { sdk.getRegisteredDevices() } returns remoteList
        coEvery { mobileDeviceDb.loadAll(1) } returnsMany listOf(
            flowOf(remoteList.dropLast(1).mapToEntities(student)),
            flowOf(
                remoteList.dropLast(1).mapToEntities(student)
            ), // after fetch end before save result
            flowOf(remoteList.mapToEntities(student))
        )
        coEvery { mobileDeviceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = mobileDeviceRepository.getDevices(
            student = student,
            semester = semester,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        Assert.assertEquals(null, res.errorOrNull)
        Assert.assertEquals(2, res.dataOrNull?.size)
        coVerify { sdk.getRegisteredDevices() }
        coVerify { mobileDeviceDb.loadAll(1) }
        coVerify {
            mobileDeviceDb.removeOldAndSaveNew(
                oldItems = match { it.isEmpty() },
                newItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(student)[1]
                },
            )
        }
    }

    @Test
    fun `force refresh with more items in local`() = runTest {
        // prepare
        coEvery { sdk.getRegisteredDevices() } returns remoteList.dropLast(1)
        coEvery { mobileDeviceDb.loadAll(1) } returnsMany listOf(
            flowOf(remoteList.mapToEntities(student)),
            flowOf(remoteList.mapToEntities(student)), // after fetch end before save result
            flowOf(remoteList.dropLast(1).mapToEntities(student))
        )
        coEvery { mobileDeviceDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = mobileDeviceRepository.getDevices(
            student = student,
            semester = semester,
            forceRefresh = true,
        ).toFirstResult()

        // verify
        Assert.assertEquals(null, res.errorOrNull)
        Assert.assertEquals(1, res.dataOrNull?.size)
        coVerify { sdk.getRegisteredDevices() }
        coVerify { mobileDeviceDb.loadAll(1) }
        coVerify {
            mobileDeviceDb.removeOldAndSaveNew(
                oldItems = match {
                    it.size == 1 && it[0] == remoteList.mapToEntities(student)[1]
                },
                newItems = match { it.isEmpty() },
            )
        }
    }

    private fun getDevicePojo(day: Int) = Device(
        id = 0,
        name = "",
        deviceId = "",
        createDate = of(2019, 5, day, 0, 0, 0, 0, ZoneOffset.UTC),
        modificationDate = of(2019, 5, day, 0, 0, 0, 0, ZoneOffset.UTC),
    )
}
