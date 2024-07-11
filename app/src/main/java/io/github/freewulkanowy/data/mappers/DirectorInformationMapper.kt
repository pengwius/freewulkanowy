package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.SchoolAnnouncement
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.sdk.pojo.DirectorInformation as SdkDirectorInformation
import io.github.freewulkanowy.sdk.pojo.LastAnnouncement as SdkLastAnnouncement

@JvmName("mapDirectorInformationToEntities")
fun List<SdkDirectorInformation>.mapToEntities(student: Student) = map {
    SchoolAnnouncement(
        studentId = student.studentId,
        date = it.date,
        subject = it.subject,
        content = it.content,
        author = null,
    )
}

@JvmName("mapLastAnnouncementsToEntities")
fun List<SdkLastAnnouncement>.mapToEntities(student: Student) = map {
    SchoolAnnouncement(
        studentId = student.studentId,
        date = it.date,
        subject = it.subject,
        content = it.content,
        author = it.author,
    )
}
