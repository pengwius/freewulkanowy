package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Teacher
import io.github.freewulkanowy.sdk.pojo.Teacher as SdkTeacher

fun List<SdkTeacher>.mapToEntities(semester: Semester) = map {
    Teacher(
        studentId = semester.studentId,
        name = it.name,
        subject = it.subject,
        shortName = it.short,
        classId = semester.classId
    )
}
