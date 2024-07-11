package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Exam
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.sdk.pojo.Exam as SdkExam

fun List<SdkExam>.mapToEntities(semester: Semester) = map {
    Exam(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        date = it.date,
        entryDate = it.entryDate,
        subject = it.subject,
        group = "",
        type = it.type,
        description = it.description,
        teacher = it.teacher,
        teacherSymbol = it.teacherSymbol
    )
}
