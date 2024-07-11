package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.*
import io.github.freewulkanowy.sdk.pojo.MailboxType
import timber.log.Timber
import io.github.freewulkanowy.sdk.pojo.Message as SdkMessage
import io.github.freewulkanowy.sdk.pojo.MessageAttachment as SdkMessageAttachment
import io.github.freewulkanowy.sdk.pojo.Recipient as SdkRecipient

fun List<SdkMessage>.mapToEntities(
    student: Student,
    mailbox: Mailbox?,
    allMailboxes: List<Mailbox>
): List<Message> = map {
    Message(
        messageGlobalKey = it.globalKey,
        mailboxKey = mailbox?.globalKey ?: allMailboxes.find { box ->
            box.fullName == it.mailbox
        }?.globalKey.let { mailboxKey ->
            if (mailboxKey == null) {
                Timber.e("Can't find ${it.mailbox} in $allMailboxes")
                "unknown"
            } else mailboxKey
        },
        email = student.email,
        messageId = it.id,
        correspondents = it.correspondents,
        subject = it.subject.trim(),
        date = it.date.toInstant(),
        folderId = it.folderId,
        unread = it.unread,
        unreadBy = it.unreadBy,
        readBy = it.readBy,
        hasAttachments = it.hasAttachments,
    ).apply {
        content = it.content.orEmpty()
    }
}

fun List<SdkMessageAttachment>.mapToEntities(messageGlobalKey: String) = map {
    MessageAttachment(
        messageGlobalKey = messageGlobalKey,
        url = it.url,
        filename = it.filename
    )
}

fun List<Recipient>.mapFromEntities() = map {
    SdkRecipient(
        fullName = it.fullName,
        userName = it.userName,
        studentName = it.userName,
        mailboxGlobalKey = it.mailboxGlobalKey,
        schoolNameShort = it.schoolShortName,
        type = MailboxType.valueOf(it.type.name),
    )
}
