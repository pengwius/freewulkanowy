package io.github.freewulkanowy.data.repositories

import android.content.Context
import io.github.freewulkanowy.createWulkanowySdkFactoryMock
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.SharedPrefProvider
import io.github.freewulkanowy.data.db.dao.MailboxDao
import io.github.freewulkanowy.data.db.dao.MessageAttachmentDao
import io.github.freewulkanowy.data.db.dao.MessagesDao
import io.github.freewulkanowy.data.db.dao.MutedMessageSendersDao
import io.github.freewulkanowy.data.db.entities.Message
import io.github.freewulkanowy.data.db.entities.MessageWithAttachment
import io.github.freewulkanowy.data.db.entities.MutedMessageSender
import io.github.freewulkanowy.data.enums.MessageFolder
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.domain.messages.GetMailboxByStudentUseCase
import io.github.freewulkanowy.getMailboxEntity
import io.github.freewulkanowy.getStudentEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.pojo.Folder
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.github.freewulkanowy.utils.Status
import io.github.freewulkanowy.utils.status
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.checkEquals
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertTrue

class MessageRepositoryTest {

    private var sdk = spyk<Sdk>()
    private val wulkanowySdkFactory = createWulkanowySdkFactoryMock(sdk)

    @MockK
    private lateinit var messageDb: MessagesDao

    @MockK
    private lateinit var mutesDb: MutedMessageSendersDao

    @MockK
    private lateinit var messageAttachmentDao: MessageAttachmentDao

    @MockK
    private lateinit var context: Context

    @MockK(relaxUnitFun = true)
    private lateinit var refreshHelper: AutoRefreshHelper

    @MockK
    private lateinit var sharedPrefProvider: SharedPrefProvider

    @MockK
    private lateinit var mailboxDao: MailboxDao

    @MockK
    private lateinit var getMailboxByStudentUseCase: GetMailboxByStudentUseCase

    private val student = getStudentEntity()

    private val mailbox = getMailboxEntity()

    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { refreshHelper.shouldBeRefreshed(any()) } returns false
        coEvery { mutesDb.checkMute(any()) } returns false
        coEvery {
            messageDb.loadMessagesWithMutedAuthor(
                mailboxKey = any(),
                folder = any()
            )
        } returns flowOf(emptyList())
        coEvery {
            messageDb.loadMessagesWithMutedAuthor(
                folder = any(),
                email = any()
            )
        } returns flowOf(emptyList())
        repository = MessageRepository(
            messagesDb = messageDb,
            mutedMessageSendersDao = mutesDb,
            messageAttachmentDao = messageAttachmentDao,
            wulkanowySdkFactory = wulkanowySdkFactory,
            context = context,
            refreshHelper = refreshHelper,
            sharedPrefProvider = sharedPrefProvider,
            json = Json,
            mailboxDao = mailboxDao,
            getMailboxByStudentUseCase = getMailboxByStudentUseCase,
        )
    }

    @Test
    fun `get messages when fetched completely new message without notify`() = runTest {
        coEvery { mailboxDao.loadAll(any()) } returns listOf(mailbox)
        every { messageDb.loadAll(mailbox.globalKey, any()) } returns flowOf(emptyList())
        coEvery { sdk.getMessages(Folder.RECEIVED, any()) } returns listOf(
            getMessageDto().copy(
                unreadBy = 5,
                readBy = 10,
            )
        )
        coEvery { messageDb.removeOldAndSaveNew(any(), any()) } just Runs

        val res = repository.getMessages(
            student = student,
            mailbox = mailbox,
            folder = MessageFolder.RECEIVED,
            forceRefresh = true,
            notify = false,
        ).toFirstResult()

        assertEquals(null, res.errorOrNull)
        coVerify {
            messageDb.removeOldAndSaveNew(
                oldItems = withArg { checkEquals(emptyList<Message>()) },
                newItems = withArg {
                    assertEquals(4, it.single().messageId)
                    assertTrue(it.single().isNotified)
                },
            )
        }
    }

    @Test(expected = NoSuchElementException::class)
    fun `throw error when message is not in the db`() {
        val testMessage = getMessageEntity(1, "", false)
        coEvery {
            messageDb.loadMessageWithAttachment("v4")
        } throws NoSuchElementException("No message in database")

        runBlocking { repository.getMessage(student, testMessage).toFirstResult() }
    }

    @Test
    fun `get message when content already in db`() {
        val testMessage = getMessageEntity(123, "Test", false)
        val messageWithAttachment = MessageWithAttachment(
            testMessage,
            emptyList(),
            MutedMessageSender("Jan Kowalski - P - (WULKANOWY)")
        )

        coEvery { messageDb.loadMessageWithAttachment("v4") } returns flowOf(
            messageWithAttachment
        )

        val res = runBlocking { repository.getMessage(student, testMessage).toFirstResult() }

        assertEquals(null, res.errorOrNull)
        assertEquals(Status.SUCCESS, res.status)
        assertEquals("Test", res.dataOrNull!!.message.content)
    }

    @Test
    fun `get message when content in db is empty`() = runTest {
        val testMessage = getMessageEntity(123, "", true)
        val testMessageWithContent = testMessage.copy().apply { content = "Test" }

        val mWa = MessageWithAttachment(
            testMessage,
            emptyList(),
            MutedMessageSender("Jan Kowalski - P - (WULKANOWY)")
        )
        val mWaWithContent = MessageWithAttachment(
            testMessageWithContent,
            emptyList(),
            MutedMessageSender("Jan Kowalski - P - (WULKANOWY)")
        )

        coEvery {
            messageDb.loadMessageWithAttachment("v4")
        } returnsMany listOf(flowOf(mWa), flowOf(mWaWithContent))
        coEvery {
            sdk.getMessageDetails("v4", any())
        } returns mockk {
            every { sender } returns ""
            every { recipients } returns listOf("")
            every { attachments } returns listOf()
        }
        coEvery { messageDb.updateAll(any()) } just Runs
        coEvery { messageAttachmentDao.insertAttachments(any()) } returns listOf(1)

        val res = repository.getMessage(student, testMessage).toFirstResult()

        assertEquals(null, res.errorOrNull)
        assertEquals(Status.SUCCESS, res.status)
        assertEquals("Test", res.dataOrNull!!.message.content)
        coVerify { messageDb.updateAll(listOf(testMessageWithContent)) }
    }

    @Test(expected = UnknownHostException::class)
    fun `get message when content in db is empty and there is no internet connection`() {
        val testMessage = getMessageEntity(123, "", false)

        coEvery {
            messageDb.loadMessageWithAttachment("v4")
        } throws UnknownHostException()

        runBlocking { repository.getMessage(student, testMessage).toFirstResult() }
    }

    @Test(expected = UnknownHostException::class)
    fun `get message when content in db is empty, unread and there is no internet connection`() {
        val testMessage = getMessageEntity(123, "", true)

        coEvery {
            messageDb.loadMessageWithAttachment("v4")
        } throws UnknownHostException()

        runBlocking { repository.getMessage(student, testMessage).toList()[1] }
    }

    private fun getMessageEntity(
        messageId: Int,
        content: String,
        unread: Boolean
    ) = Message(
        messageGlobalKey = "v4",
        mailboxKey = "",
        email = "",
        correspondents = "",
        messageId = messageId,
        subject = "",
        date = Instant.EPOCH,
        folderId = 1,
        unread = unread,
        readBy = 1,
        unreadBy = 1,
        hasAttachments = false,
    ).apply {
        this.content = content
    }

    private fun getMessageDto() = io.github.freewulkanowy.sdk.pojo.Message(
        globalKey = "v4",
        mailbox = "",
        correspondents = "",
        id = 4,
        recipients = listOf(),
        subject = "",
        content = "Test",
        date = Instant.EPOCH.atZone(ZoneOffset.UTC),
        folderId = 1,
        unread = true,
        readBy = 1,
        unreadBy = 1,
        hasAttachments = false,
    )
}
