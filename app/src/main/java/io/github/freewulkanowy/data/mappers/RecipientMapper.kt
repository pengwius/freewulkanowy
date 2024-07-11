package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.MailboxType
import io.github.freewulkanowy.data.db.entities.Recipient
import io.github.freewulkanowy.sdk.pojo.Recipient as SdkRecipient

fun List<SdkRecipient>.mapToEntities(studentMailboxGlobalKey: String) = map {
    Recipient(
        mailboxGlobalKey = it.mailboxGlobalKey,
        fullName = it.fullName,
        userName = it.userName,
        studentMailboxGlobalKey = studentMailboxGlobalKey,
        schoolShortName = it.schoolNameShort,
        type = MailboxType.valueOf(it.type.name),
    )
}
