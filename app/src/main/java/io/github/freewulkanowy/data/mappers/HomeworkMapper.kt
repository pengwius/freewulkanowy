package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.sdk.pojo.Homework as SdkHomework
import io.github.freewulkanowy.data.db.entities.Homework
import io.github.freewulkanowy.data.db.entities.Semester

fun List<SdkHomework>.mapToEntities(semester: Semester) = map {
    Homework(
        semesterId = semester.semesterId,
        studentId = semester.studentId,
        date = it.date,
        entryDate = it.entryDate,
        subject = it.subject,
        content = it.content,
        teacher = it.teacher,
        teacherSymbol = it.teacherSymbol,
        attachments = it.attachments.map { attachment ->
            attachment.url to attachment.name
        }
    )
}
