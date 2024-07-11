package io.github.freewulkanowy.ui.modules.message.preview

import androidx.annotation.StringRes
import io.github.freewulkanowy.data.db.entities.Message
import io.github.freewulkanowy.data.db.entities.MessageWithAttachment
import io.github.freewulkanowy.ui.base.BaseView

interface MessagePreviewView : BaseView {

    val deleteMessageSuccessString: String

    val muteMessageSuccessString: String

    val unmuteMessageSuccessString: String

    val restoreMessageSuccessString: String

    val messageNoSubjectString: String

    val printHTML: String

    val messageNotExists: String

    fun initView()

    fun setMessageWithAttachment(item: MessageWithAttachment)

    fun updateMuteToggleButton(isMuted: Boolean)

    fun showProgress(show: Boolean)

    fun showContent(show: Boolean)

    fun showErrorView(show: Boolean)

    fun setErrorDetails(message: String)

    fun setErrorRetryCallback(callback: () -> Unit)

    fun showOptions(show: Boolean, isReplayable: Boolean, isRestorable: Boolean, isUnreadIncognito: Boolean)

    fun openMessageReply(message: Message?)

    fun openMessageForward(message: Message?)

    fun shareText(text: String, subject: String)

    fun popView()

    fun printDocument(html: String, jobName: String)

    fun showMessage(@StringRes messageId: Int)
}
