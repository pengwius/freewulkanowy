package io.github.freewulkanowy.ui.modules.message.preview

import android.annotation.SuppressLint
import android.view.MenuItem
import androidx.core.text.parseAsHtml
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Message
import io.github.freewulkanowy.data.db.entities.MessageWithAttachment
import io.github.freewulkanowy.data.enums.MessageFolder
import io.github.freewulkanowy.data.flatResourceFlow
import io.github.freewulkanowy.data.logResourceStatus
import io.github.freewulkanowy.data.onResourceData
import io.github.freewulkanowy.data.onResourceError
import io.github.freewulkanowy.data.onResourceNotLoading
import io.github.freewulkanowy.data.onResourceSuccess
import io.github.freewulkanowy.data.repositories.MessageRepository
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.ui.base.BasePresenter
import io.github.freewulkanowy.ui.base.ErrorHandler
import io.github.freewulkanowy.utils.AnalyticsHelper
import io.github.freewulkanowy.utils.toFormattedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class MessagePreviewPresenter @Inject constructor(
    errorHandler: ErrorHandler,
    studentRepository: StudentRepository,
    private val messageRepository: MessageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val analytics: AnalyticsHelper
) : BasePresenter<MessagePreviewView>(errorHandler, studentRepository) {

    private var messageWithAttachments: MessageWithAttachment? = null

    private lateinit var lastError: Throwable

    private var retryCallback: () -> Unit = {}

    fun onAttachView(view: MessagePreviewView, message: Message) {
        super.onAttachView(view)
        view.initView()
        errorHandler.showErrorMessage = ::showErrorViewOnError
        loadData(message)
    }

    private fun onMessageLoadRetry(message: Message) {
        view?.run {
            showErrorView(false)
            showProgress(true)
        }
        loadData(message)
    }

    fun onDetailsClick() {
        view?.showErrorDetailsDialog(lastError)
    }

    private fun loadData(messageToLoad: Message) {
        flatResourceFlow {
            val student = studentRepository.getCurrentStudent()
            messageRepository.getMessage(
                student = student,
                message = messageToLoad,
                markAsRead = !preferencesRepository.isIncognitoMode,
            )
        }
            .logResourceStatus("message ${messageToLoad.messageId} preview")
            .onResourceData {
                if (it != null) {
                    messageWithAttachments = it
                    view?.apply {
                        setMessageWithAttachment(it)
                        showContent(true)
                        initOptions()
                        updateMuteToggleButton(isMuted = it.mutedMessageSender != null)
                        if (preferencesRepository.isIncognitoMode && it.message.unread) {
                            showMessage(R.string.message_incognito_description)
                        }
                    }
                } else {
                    delay(1.seconds)
                    view?.run {
                        showMessage(messageNotExists)
                        popView()
                    }
                }
            }.onResourceSuccess {
                if (it != null) {
                    analytics.logEvent(
                        "load_item",
                        "type" to "message_preview",
                        "length" to it.message.content.length
                    )
                }
            }.onResourceNotLoading { view?.showProgress(false) }.onResourceError {
                retryCallback = { onMessageLoadRetry(messageToLoad) }
                errorHandler.dispatch(it)
            }.launch()
    }

    fun onReply(): Boolean {
        return if (messageWithAttachments?.message != null) {
            view?.openMessageReply(messageWithAttachments?.message)
            true
        } else false
    }

    fun onForward(): Boolean {
        return if (messageWithAttachments?.message != null) {
            view?.openMessageForward(messageWithAttachments?.message)
            true
        } else false
    }

    fun onShare(): Boolean {
        val message = messageWithAttachments?.message ?: return false
        val subject = message.subject.ifBlank { view?.messageNoSubjectString.orEmpty() }

        val text = buildString {
            appendLine("Temat: $subject")
            appendLine("Od: ${message.sender}")
            appendLine("Do: ${message.recipients}")
            appendLine("Data: ${message.date.toFormattedString("yyyy-MM-dd HH:mm:ss")}")

            appendLine()

            appendLine(message.content.parseAsHtml())

            if (!messageWithAttachments?.attachments.isNullOrEmpty()) {
                appendLine()
                appendLine("Załączniki:")

                append(
                    messageWithAttachments?.attachments.orEmpty()
                        .joinToString(separator = "\n") { attachment ->
                            "${attachment.filename}: ${attachment.url}"
                        })
            }
        }

        view?.shareText(
            subject = "FW: $subject",
            text = text,
        )
        return true
    }

    fun onMarkRead(i: MenuItem): Boolean {
        val message = checkNotNull(messageWithAttachments?.message)

        flatResourceFlow {
            val student = studentRepository.getCurrentStudent()
            messageRepository.markMessageRead(
                student = student,
                message = message
            )
        }
            .logResourceStatus("message ${message.messageId} mark as read")
            .onResourceSuccess {
                if (it != null) {
                    view?.showMessage(R.string.message_incognito_markread_success)
                }
            }.onResourceError {
                view?.showMessage(R.string.message_incognito_markread_error)
                errorHandler.dispatch(it)
            }.launch()

        i.setVisible(false)
        return true
    }

    @SuppressLint("NewApi")
    fun onPrint(): Boolean {
        val message = messageWithAttachments?.message ?: return false
        val subject = message.subject.ifBlank { view?.messageNoSubjectString.orEmpty() }

        val dateString = message.date.toFormattedString("yyyy-MM-dd HH:mm:ss")

        val infoContent = buildString {
            append("<div><h4>Data wysłania</h4>$dateString</div>")

            append("<div><h4>Od</h4>${message.sender}</div>")
            append("<div><h4>DO</h4>${message.recipients}</div>")
        }
        val messageContent = "<p>${message.content}</p>".replace(Regex("[\\n\\r]{2,}"), "</p><p>")
            .replace(Regex("[\\n\\r]"), "<br>")

        val jobName = buildString {
            append("Wiadomość ")
            append("od ${message.correspondents}")
            append("do ${message.correspondents}")
            append(" $dateString: $subject | Wulkanowy")
        }

        view?.apply {
            val html = printHTML.replace("%SUBJECT%", subject).replace("%CONTENT%", messageContent)
                .replace("%INFO%", infoContent)
            printDocument(html, jobName)
        }

        return true
    }

    private fun restoreMessage() {
        val message = messageWithAttachments?.message ?: return

        view?.run {
            showContent(false)
            showProgress(true)
            showOptions(
                show = false,
                isReplayable = false,
                isRestorable = false,
                isUnreadIncognito = false
            )
            showErrorView(false)
        }
        Timber.i("Restore message ${message.messageGlobalKey}")
        presenterScope.launch {
            runCatching {
                val student = studentRepository.getCurrentStudent(decryptPass = true)
                val mailbox = messageRepository.getMailboxByStudent(student)
                messageRepository.restoreMessages(student, mailbox, listOfNotNull(message))
            }
                .onFailure {
                    retryCallback = { onMessageRestore() }
                    errorHandler.dispatch(it)
                }
                .onSuccess {
                    view?.run {
                        showMessage(restoreMessageSuccessString)
                        popView()
                    }
                }
            view?.showProgress(false)
        }
    }

    private fun deleteMessage() {
        messageWithAttachments?.message ?: return

        view?.run {
            showContent(false)
            showProgress(true)
            showOptions(
                show = false,
                isReplayable = false,
                isRestorable = false,
                isUnreadIncognito = false
            )
            showErrorView(false)
        }

        Timber.i("Delete message ${messageWithAttachments?.message?.messageGlobalKey}")

        presenterScope.launch {
            runCatching {
                val student = studentRepository.getCurrentStudent(decryptPass = true)
                messageRepository.deleteMessage(student, messageWithAttachments?.message!!)
            }.onFailure {
                retryCallback = { onMessageDelete() }
                errorHandler.dispatch(it)
            }.onSuccess {
                view?.run {
                    showMessage(deleteMessageSuccessString)
                    popView()
                }
            }

            view?.showProgress(false)
        }
    }

    private fun showErrorViewOnError(message: String, error: Throwable) {
        view?.run {
            lastError = error
            setErrorDetails(message)
            showContent(false)
            showErrorView(true)
            setErrorRetryCallback { retryCallback() }
        }
    }

    fun onMessageRestore(): Boolean {
        restoreMessage()
        return true
    }

    fun onMessageDelete(): Boolean {
        deleteMessage()
        return true
    }

    private fun initOptions() {
        view?.apply {
            showOptions(
                show = messageWithAttachments?.message != null,
                isReplayable = messageWithAttachments?.message?.folderId == MessageFolder.RECEIVED.id,
                isRestorable = messageWithAttachments?.message?.folderId == MessageFolder.TRASHED.id,
                /*
                 * The unread check assumes that if a message is marked as read in the database,
                 * it's also on the server. This is a bug as the status may have been already changed
                 * from other app instalation or from web client. The bug is already present somewhere
                 * else and causes messages marked as read "externally" showing as unread in incognito
                 * mode. I think the solution should be splitting the unread status to that internal,
                 * seen in the app and that how is it seen by server. I'll fix it if I would have time.
                 */
                isUnreadIncognito = preferencesRepository.isIncognitoMode
                    && messageWithAttachments?.message?.unreadBy == null
                    && messageWithAttachments?.message?.unread == true
            )
        }
    }

    fun onCreateOptionsMenu() {
        initOptions()
    }

    fun onMute(): Boolean {
        val message = messageWithAttachments?.message ?: return false
        val isMuted = messageWithAttachments?.mutedMessageSender != null

        presenterScope.launch {
            runCatching {
                when (isMuted) {
                    true -> {
                        messageRepository.unmuteMessage(message.correspondents)
                        view?.run { showMessage(unmuteMessageSuccessString) }
                    }

                    false -> {
                        messageRepository.muteMessage(message.correspondents)
                        view?.run { showMessage(muteMessageSuccessString) }
                    }
                }
            }.onFailure {
                errorHandler.dispatch(it)
            }
        }
        view?.updateMuteToggleButton(isMuted)
        return true
    }
}
