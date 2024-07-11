package io.github.freewulkanowy.data.repositories

import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.db.dao.RecipientDao
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.getMailboxEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.pojo.MailboxType
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.github.freewulkanowy.sdk.pojo.Recipient as SdkRecipient

class RecipientLocalTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var recipientDb: RecipientDao

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    private val student = getStudentEntity()

    private lateinit var recipientRepository: RecipientRepository

    private val remoteList = listOf(
        SdkRecipient(
            mailboxGlobalKey = "2rPracownik",
            userName = "Kowalski Jan",
            fullName = "Kowalski Jan [KJ] - Pracownik (Fake123456)",
            studentName = "",
            schoolNameShort = "",
            type = MailboxType.UNKNOWN,
        ),
        SdkRecipient(
            mailboxGlobalKey = "3rPracownik",
            userName = "Kowalska Karolina",
            fullName = "Kowalska Karolina [KK] - Pracownik (Fake123456)",
            studentName = "",
            schoolNameShort = "",
            type = MailboxType.UNKNOWN,
        ),
        SdkRecipient(
            mailboxGlobalKey = "4rPracownik",
            userName = "Krupa Stanisław",
            fullName = "Krupa Stanisław [KS] - Uczeń (Fake123456)",
            studentName = "",
            schoolNameShort = "",
            type = MailboxType.UNKNOWN,
        )
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false

        recipientRepository = RecipientRepository(recipientDb, wulkanowySdkFactory, refreshHelper)
    }

    @Test
    fun `load recipients when items already in database`() {
        // prepare
        coEvery {
            recipientDb.loadAll(
                io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
                "v4"
            )
        } returnsMany listOf(
            remoteList.mapToEntities("v4"),
            remoteList.mapToEntities("v4")
        )
        coEvery { recipientDb.insertAll(any()) } returns listOf(1, 2, 3)
        coEvery { recipientDb.deleteAll(any()) } just Runs

        // execute
        val res = runBlocking {
            recipientRepository.getRecipients(
                student = student,
                mailbox = getMailboxEntity(),
                type = io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
            )
        }

        // verify
        assertEquals(3, res.size)
        coVerify {
            recipientDb.loadAll(
                type = io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
                studentMailboxGlobalKey = "v4"
            )
        }
    }

    @Test
    fun `load recipients when database is empty`() {
        // prepare
        coEvery { sdk.getRecipients("v4") } returns remoteList
        coEvery {
            recipientDb.loadAll(
                io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
                "v4"
            )
        } returnsMany listOf(
            emptyList(),
            remoteList.mapToEntities("v4")
        )
        coEvery { recipientDb.removeOldAndSaveNew(any(), any()) } just Runs

        // execute
        val res = runBlocking {
            recipientRepository.getRecipients(
                student = student,
                mailbox = getMailboxEntity(),
                type = io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
            )
        }

        // verify
        assertEquals(3, res.size)
        coVerify { sdk.getRecipients("v4") }
        coVerify {
            recipientDb.loadAll(
                io.github.freewulkanowy.data.db.entities.MailboxType.UNKNOWN,
                "v4"
            )
        }
        coVerify { recipientDb.removeOldAndSaveNew(match { it.isEmpty() }, match { it.isEmpty() }) }
    }
}
