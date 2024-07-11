package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Conference
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.sdk.pojo.Conference as SdkConference

fun List<SdkConference>.mapToEntities(semester: Semester) = map {
    Conference(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        agenda = it.agenda,
        conferenceId = it.id,
        date = it.date.toInstant(),
        presentOnConference = it.presentOnConference,
        subject = it.topic,
        title = it.place,
    )
}
