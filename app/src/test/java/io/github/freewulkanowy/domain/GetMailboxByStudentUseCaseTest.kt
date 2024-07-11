package io.github.freewulkanowy.domain

import io.github.freewulkanowy.data.db.dao.MailboxDao
import io.github.freewulkanowy.data.db.entities.Mailbox
import io.github.freewulkanowy.data.db.entities.MailboxType
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.domain.messages.GetMailboxByStudentUseCase
import io.github.freewulkanowy.sdk.Sdk
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GetMailboxByStudentUseCaseTest {

    @MockK
    private lateinit var mailboxDao: MailboxDao

    private lateinit var systemUnderTest: GetMailboxByStudentUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        coEvery { mailboxDao.deleteAll(any()) } just Runs
        coEvery { mailboxDao.insertAll(any()) } returns emptyList()
        coEvery { mailboxDao.loadAll(any()) } returns emptyList()

        systemUnderTest = GetMailboxByStudentUseCase(mailboxDao = mailboxDao)
    }

    @Test
    fun `get mailbox that doesn't exist`() = runTest {
        val student = getStudentEntity(
            userName = "Stanisław Kowalski",
            studentName = "Jan Kowalski",
        )
        coEvery { mailboxDao.loadAll(any()) } returns emptyList()

        assertNull(systemUnderTest(student))
    }

    @Test
    fun `get mailbox for user with additional spaces`() = runTest {
        val student = getStudentEntity(
            userName = "  Stanisław Kowalski  ",
            studentName = "  Jan Kowalski  ",
        )
        val expectedMailbox = getMailboxEntity("Jan Kowalski ")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        val selectedMailbox = systemUnderTest(student)
        assertEquals(expectedMailbox, selectedMailbox)
    }

    @Test
    fun `get mailbox for user with reversed name`() = runTest {
        val student = getStudentEntity(
            userName = "Kowalski Jan",
            studentName = "Jan Kowalski",
        )
        val expectedMailbox = getMailboxEntity("Kowalski Jan")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(expectedMailbox)

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for unique non-authorized student`() = runTest {
        val student = getStudentEntity(
            userName = "Stanisław Kowalski",
            studentName = "J** K*******",
        )
        val expectedMailbox = getMailboxEntity("Jan Kowalski")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for unique non-authorized student but with spaces`() = runTest {
        val student = getStudentEntity(
            userName = "Stanisław Kowalski",
            studentName = "J**  K*******",
        )
        val expectedMailbox = getMailboxEntity("Jan  Kowalski")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for not-unique non-authorized student`() = runTest {
        val student = getStudentEntity(
            userName = "Stanisław Kowalski",
            studentName = "J** K*******",
        )
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            getMailboxEntity("Jan Kowalski"),
            getMailboxEntity("Jan Kurowski"),
        )

        assertNull(systemUnderTest(student))
    }

    @Test
    fun `get mailbox for student with uppercase name`() = runTest {
        val student = getStudentEntity(
            userName = "Mochoń Julia",
            studentName = "KLAUDIA MOCHOŃ",
        )
        val expectedMailbox = getMailboxEntity("Klaudia Mochoń")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for student with second name`() = runTest {
        val student = getStudentEntity(
            userName = "Fistaszek Karolina",
            studentName = "Julia Fistaszek",
        )
        val expectedMailbox = getMailboxEntity("Julia Maria Fistaszek")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for student with second name and uppercase`() = runTest {
        val student = getStudentEntity(
            userName = "BEDNAREK KAMIL",
            studentName = "ALEKSANDRA BEDNAREK",
        )
        val expectedMailbox = getMailboxEntity("Aleksandra Anna Bednarek")
        coEvery { mailboxDao.loadAll(any()) } returns listOf(
            expectedMailbox,
        )

        assertEquals(expectedMailbox, systemUnderTest(student))
    }

    @Test
    fun `get mailbox for student with mailboxes from two different schools`() = runTest {
        val student = getStudentEntity(
            userName = "Kamil Bednarek",
            studentName = "Kamil Bednarek",
            schoolShortName = "CKZiU",
        )
        val mailbox1 = getMailboxEntity(
            studentName = "Kamil Bednarek",
            schoolShortName = "ZSTiO",
        )
        val mailbox2 = getMailboxEntity(
            studentName = "Kamil Bednarek",
            schoolShortName = "CKZiU",
        )
        coEvery { mailboxDao.loadAll(any()) } returns listOf(mailbox1, mailbox2)

        assertEquals(mailbox2, systemUnderTest(student))
    }

    private fun getMailboxEntity(
        studentName: String,
        schoolShortName: String = "test",
    ) = Mailbox(
        globalKey = "",
        fullName = "",
        userName = "",
        email = "",
        schoolId = "",
        symbol = "",
        studentName = studentName,
        schoolNameShort = schoolShortName,
        type = MailboxType.STUDENT,
    )

    private fun getStudentEntity(
        studentName: String,
        userName: String,
        schoolShortName: String = "test",
    ) = Student(
        scrapperBaseUrl = "http://fakelog.cf",
        scrapperDomainSuffix = "",
        email = "jan@fakelog.cf",
        certificateKey = "",
        classId = 0,
        className = "",
        isCurrent = false,
        isParent = false,
        loginMode = Sdk.Mode.HEBE.name,
        loginType = Sdk.ScrapperLoginType.STANDARD.name,
        mobileBaseUrl = "",
        password = "",
        privateKey = "",
        registrationDate = Instant.now(),
        schoolName = "",
        schoolShortName = schoolShortName,
        schoolSymbol = "",
        studentId = 1,
        studentName = studentName,
        symbol = "",
        userLoginId = 1,
        userName = userName,
        isAuthorized = false,
        isEduOne = false
    )
}
