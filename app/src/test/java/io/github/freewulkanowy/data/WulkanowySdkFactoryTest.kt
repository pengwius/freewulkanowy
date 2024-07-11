package io.github.freewulkanowy.data

import android.os.Build
import dagger.hilt.android.testing.HiltTestApplication
import io.github.freewulkanowy.data.db.dao.StudentDao
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.db.entities.StudentIsEduOne
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.pojo.RegisterStudent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = HiltTestApplication::class)
class WulkanowySdkFactoryTest {

    private lateinit var wulkanowySdkFactory: WulkanowySdkFactory
    private lateinit var studentDao: StudentDao
    private lateinit var sdk: Sdk

    @Before
    fun setUp() {
        sdk = mockk(relaxed = true)
        studentDao = mockk()
        wulkanowySdkFactory = spyk(
            WulkanowySdkFactory(
                chuckerInterceptor = mockk(),
                remoteConfig = mockk(relaxed = true),
                webkitCookieManagerProxy = mockk(),
                studentDb = studentDao,
                wulkanowyRepository = mockk(relaxed = true),
                context = mockk(),
            )
        )

        coEvery { wulkanowySdkFactory.create() } returns sdk
    }

    @Test
    fun `check sdk flag isEduOne when local student is eduone`() = runTest {
        val student = getStudentEntity().copy(isEduOne = true)

        wulkanowySdkFactory.create(student)

        verify { sdk.isEduOne = true }
        coVerify(exactly = 0) { sdk.getCurrentStudent() }
    }

    @Test
    fun `check sdk flag isEduOne when local student is not eduone`() = runTest {
        val student = getStudentEntity().copy(isEduOne = false)

        wulkanowySdkFactory.create(student)

        verify { sdk.isEduOne = false }
        coVerify(exactly = 0) { sdk.getCurrentStudent() }
    }

    @Test
    fun `check sdk flag isEduOne when local student is eduone null and remote student is eduone true`() =
        runTest {
            val studentToProcess = getStudentEntity().copy(isEduOne = null)
            val registerStudent = studentToProcess.toRegisterStudent(isEduOne = true)

            coEvery { studentDao.loadById(any()) } returns studentToProcess
            coEvery { studentDao.update(any(StudentIsEduOne::class)) } just Runs
            coEvery { sdk.getCurrentStudent() } returns registerStudent

            wulkanowySdkFactory.create(studentToProcess)

            verify { sdk.isEduOne = true }
            coVerify { sdk.getCurrentStudent() }
        }

    @Test
    fun `check sdk flag isEduOne when local student is eduone null and remote student is eduone false`() =
        runTest {
            val studentToProcess = getStudentEntity().copy(isEduOne = null)
            val registerStudent = studentToProcess.toRegisterStudent(isEduOne = false)

            coEvery { studentDao.loadById(any()) } returns studentToProcess
            coEvery { studentDao.update(any(StudentIsEduOne::class)) } just Runs
            coEvery { sdk.getCurrentStudent() } returns registerStudent

            wulkanowySdkFactory.create(studentToProcess)

            verify { sdk.isEduOne = false }
            coVerify { sdk.getCurrentStudent() }
        }

    @Test
    fun `check sdk flag isEduOne when sdk getCurrentStudent throws error`() =
        runTest {
            val studentToProcess = getStudentEntity().copy(isEduOne = null)

            coEvery { studentDao.loadById(any()) } returns studentToProcess
            coEvery { studentDao.update(any(StudentIsEduOne::class)) } just Runs
            coEvery { sdk.getCurrentStudent() } throws Exception()

            wulkanowySdkFactory.create(studentToProcess)

            verify { sdk.isEduOne = false }
            coVerify { sdk.getCurrentStudent() }
        }

    private fun Student.toRegisterStudent(isEduOne: Boolean) = RegisterStudent(
        studentId = studentId,
        studentName = studentName,
        studentSecondName = studentName,
        studentSurname = studentName,
        className = className,
        classId = classId,
        isParent = isParent,
        isAuthorized = isAuthorized,
        semesters = emptyList(),
        isEduOne = isEduOne,
    )
}
