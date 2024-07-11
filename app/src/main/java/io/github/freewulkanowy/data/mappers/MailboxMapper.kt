package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Mailbox
import io.github.freewulkanowy.data.db.entities.MailboxType
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.sdk.pojo.Mailbox as SdkMailbox

fun List<SdkMailbox>.mapToEntities(student: Student) = map {
    Mailbox(
        globalKey = it.globalKey,
        fullName = it.fullName,
        userName = it.userName,
        studentName = it.studentName,
        schoolNameShort = it.schoolNameShort,
        type = MailboxType.valueOf(it.type.name),
        email = student.email,
        symbol = student.symbol,
        schoolId = student.schoolSymbol,
    )
}
