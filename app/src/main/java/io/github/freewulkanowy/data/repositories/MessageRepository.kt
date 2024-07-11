package io.github.freewulkanowy.data.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.Resource
import io.github.freewulkanowy.data.WulkanowySdkFactory
import io.github.freewulkanowy.data.db.SharedPrefProvider
import io.github.freewulkanowy.data.db.dao.MailboxDao
import io.github.freewulkanowy.data.db.dao.MessageAttachmentDao
import io.github.freewulkanowy.data.db.dao.MessagesDao
import io.github.freewulkanowy.data.db.dao.MutedMessageSendersDao
import io.github.freewulkanowy.data.db.entities.Mailbox
import io.github.freewulkanowy.data.db.entities.Message
import io.github.freewulkanowy.data.db.entities.MessageWithAttachment
import io.github.freewulkanowy.data.db.entities.MessageWithMutedAuthor
import io.github.freewulkanowy.data.db.entities.MutedMessageSender
import io.github.freewulkanowy.data.db.entities.Recipient
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.enums.MessageFolder
import io.github.freewulkanowy.data.enums.MessageFolder.RECEIVED
import io.github.freewulkanowy.data.enums.MessageFolder.SENT
import io.github.freewulkanowy.data.enums.MessageFolder.TRASHED
import io.github.freewulkanowy.data.mappers.mapFromEntities
import io.github.freewulkanowy.data.mappers.mapToEntities
import io.github.freewulkanowy.data.networkBoundResource
import io.github.freewulkanowy.data.onResourceError
import io.github.freewulkanowy.data.onResourceSuccess
import io.github.freewulkanowy.data.pojos.MessageDraft
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.data.waitForResult
import io.github.freewulkanowy.domain.messages.GetMailboxByStudentUseCase
import io.github.freewulkanowy.sdk.pojo.Folder
import io.github.freewulkanowy.utils.AutoRefreshHelper
import io.github.freewulkanowy.utils.getRefreshKey
import io.github.freewulkanowy.utils.uniqueSubtract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messagesDb: MessagesDao,
    private val mutedMessageSendersDao: MutedMessageSendersDao,
    private val messageAttachmentDao: MessageAttachmentDao,
    private val wulkanowySdkFactory: WulkanowySdkFactory,
    @ApplicationContext private val context: Context,
    private val refreshHelper: AutoRefreshHelper,
    private val sharedPrefProvider: SharedPrefProvider,
    private val json: Json,
    private val mailboxDao: MailboxDao,
    private val getMailboxByStudentUseCase: GetMailboxByStudentUseCase,
) {
    private val saveFetchResultMutex = Mutex()

    private val messagesCacheKey = "message"
    private val mailboxCacheKey = "mailboxes"

    fun getMessages(
        student: Student,
        mailbox: Mailbox?,
        folder: MessageFolder,
        forceRefresh: Boolean,
        notify: Boolean = false,
    ): Flow<Resource<List<MessageWithMutedAuthor>>> = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(
                key = getRefreshKey(messagesCacheKey, mailbox, folder)
            )
            it.isEmpty() || forceRefresh || isExpired
        },
        query = {
            if (mailbox == null) {
                messagesDb.loadMessagesWithMutedAuthor(folder.id, student.email)
            } else messagesDb.loadMessagesWithMutedAuthor(mailbox.globalKey, folder.id)
        },
        fetch = {
            wulkanowySdkFactory.create(student)
                .getMessages(
                    folder = Folder.valueOf(folder.name),
                    mailboxKey = mailbox?.globalKey,
                )
                .mapToEntities(
                    student = student,
                    mailbox = mailbox,
                    allMailboxes = mailboxDao.loadAll(student.email)
                )
        },
        saveFetchResult = { oldWithAuthors, new ->
            val old = oldWithAuthors.map { it.message }
            messagesDb.removeOldAndSaveNew(
                oldItems = old uniqueSubtract new,
                newItems = (new uniqueSubtract old).onEach {
                    val muted = isMuted(it.correspondents)
                    it.isNotified = !notify || muted
                },
            )
            refreshHelper.updateLastRefreshTimestamp(
                getRefreshKey(messagesCacheKey, mailbox, folder)
            )
        }
    )

    fun getMessage(
        student: Student,
        message: Message,
        markAsRead: Boolean = false,
    ): Flow<Resource<MessageWithAttachment?>> = networkBoundResource(
        isResultEmpty = { it?.message?.content.isNullOrBlank() },
        shouldFetch = {
            checkNotNull(it) { "This message no longer exist!" }
            Timber.d("Message content in db empty: ${it.message.content.isBlank()}")
            (it.message.unread && markAsRead) || it.message.content.isBlank()
        },
        query = { messagesDb.loadMessageWithAttachment(message.messageGlobalKey) },
        fetch = {
            wulkanowySdkFactory.create(student)
                .getMessageDetails(
                    messageKey = message.messageGlobalKey,
                    markAsRead = message.unread && markAsRead,
                )
        },
        saveFetchResult = { old, new ->
            checkNotNull(old) { "Fetched message no longer exist!" }
            messagesDb.updateAll(
                listOf(old.message.apply {
                    id = message.id
                    unread = when {
                        markAsRead -> false
                        else -> unread
                    }
                    sender = new.sender
                    recipients = new.recipients.singleOrNull() ?: "Wielu adresatów"
                    content = content.ifBlank { new.content }
                })
            )
            messageAttachmentDao.insertAttachments(
                items = new.attachments.mapToEntities(message.messageGlobalKey),
            )

            Timber.d("Message ${message.messageId} with blank content: ${old.message.content.isBlank()}, marked as read: $markAsRead")
        }
    )

    fun markMessageRead(
        student: Student,
        message: Message
    ): Flow<Resource<MessageWithAttachment?>> = networkBoundResource(
        isResultEmpty = { it?.message?.content.isNullOrBlank() },
        shouldFetch = {
            checkNotNull(it) { "This message no longer exist!" }
            message.unread
        },
        query = { messagesDb.loadMessageWithAttachment(message.messageGlobalKey) },
        fetch = {
            wulkanowySdkFactory.create(student)
                .markMessageRead(
                    messageKey = message.messageGlobalKey
                )
        },
        saveFetchResult = { old, _ ->
            checkNotNull(old) { "Fetched message no longer exist!" }
            messagesDb.updateAll(
                listOf(old.message.apply { unread = false })
            )

            Timber.d("Marking as read message ${message.messageId} with blank content: ${old.message.content.isBlank()}")
        }
    )

    fun getMessagesFromDatabase(student: Student, mailbox: Mailbox?): Flow<List<Message>> {
        return if (mailbox == null) {
            messagesDb.loadAll(RECEIVED.id, student.email)
        } else messagesDb.loadAll(mailbox.globalKey, RECEIVED.id)
    }

    suspend fun updateMessages(messages: List<Message>) {
        return messagesDb.updateAll(messages)
    }

    suspend fun sendMessage(
        student: Student,
        subject: String,
        content: String,
        recipients: List<Recipient>,
        mailbox: Mailbox,
    ) {
        wulkanowySdkFactory.create(student)
            .sendMessage(
                subject = subject,
                content = content,
                recipients = recipients.mapFromEntities(),
                mailboxId = mailbox.globalKey,
            )
        refreshFolders(student, mailbox, listOf(SENT))
    }

    suspend fun restoreMessages(student: Student, mailbox: Mailbox?, messages: List<Message>) {
        wulkanowySdkFactory.create(student)
            .restoreMessages(messages = messages.map { it.messageGlobalKey })

        refreshFolders(student, mailbox)
    }

    suspend fun deleteMessage(student: Student, message: Message) {
        deleteMessages(student, listOf(message))
    }

    suspend fun deleteMessages(student: Student, messages: List<Message>) {
        val firstMessage = messages.first()
        wulkanowySdkFactory.create(student)
            .deleteMessages(
                messages = messages.map { it.messageGlobalKey },
                removeForever = firstMessage.folderId == TRASHED.id,
            )

        if (firstMessage.folderId != TRASHED.id) {
            val deletedMessages = messages.map {
                it.copy(folderId = TRASHED.id)
                    .apply {
                        id = it.id
                        content = it.content
                        sender = it.sender
                        recipients = it.recipients
                    }
            }

            messagesDb.updateAll(deletedMessages)
        } else {
            messagesDb.deleteAll(messages)
        }
    }

    private suspend fun refreshFolders(
        student: Student,
        mailbox: Mailbox?,
        folders: List<MessageFolder> = MessageFolder.entries
    ) {
        folders.forEach {
            getMessages(
                student = student,
                mailbox = mailbox,
                folder = it,
                forceRefresh = true,
            ).toFirstResult()
        }
    }

    suspend fun getMailboxes(student: Student, forceRefresh: Boolean) = networkBoundResource(
        mutex = saveFetchResultMutex,
        isResultEmpty = { it.isEmpty() },
        shouldFetch = {
            val isExpired = refreshHelper.shouldBeRefreshed(
                key = getRefreshKey(mailboxCacheKey, student),
            )
            it.isEmpty() || isExpired || forceRefresh
        },
        query = { mailboxDao.loadAll(student.email, student.symbol, student.schoolSymbol) },
        fetch = {
            wulkanowySdkFactory.create(student)
                .getMailboxes()
                .mapToEntities(student)
        },
        saveFetchResult = { old, new ->
            mailboxDao.deleteAll(old uniqueSubtract new)
            mailboxDao.insertAll(new uniqueSubtract old)

            refreshHelper.updateLastRefreshTimestamp(getRefreshKey(mailboxCacheKey, student))
        }
    )

    suspend fun getMailboxByStudent(student: Student): Mailbox? {
        val mailbox = getMailboxByStudentUseCase(student)

        return if (mailbox == null) {
            getMailboxes(student, forceRefresh = true)
                .onResourceError { throw it }
                .onResourceSuccess { Timber.i("Found ${it.size} new mailboxes") }
                .waitForResult()

            getMailboxByStudentUseCase(student)
        } else mailbox
    }

    var draftMessage: MessageDraft?
        get() = sharedPrefProvider.getString(context.getString(R.string.pref_key_message_draft))
            ?.let { json.decodeFromString(it) }
        set(value) = sharedPrefProvider.putString(
            context.getString(R.string.pref_key_message_draft),
            value?.let { json.encodeToString(it) }
        )

    private suspend fun isMuted(author: String): Boolean {
        return mutedMessageSendersDao.checkMute(author)
    }

    suspend fun muteMessage(author: String) {
        if (isMuted(author)) return
        mutedMessageSendersDao.insertMute(MutedMessageSender(author))
    }

    suspend fun unmuteMessage(author: String) {
        if (!isMuted(author)) return
        mutedMessageSendersDao.deleteMute(author)
    }
}
