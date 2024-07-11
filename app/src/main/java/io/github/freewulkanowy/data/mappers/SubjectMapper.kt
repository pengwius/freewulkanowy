package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Subject
import io.github.freewulkanowy.sdk.pojo.Subject as SdkSubject

fun List<SdkSubject>.mapToEntities(semester: Semester) = map {
    Subject(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        name = it.name,
        realId = it.id
    )
}
