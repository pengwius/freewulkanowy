package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Note
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.sdk.pojo.Note as SdkNote

fun List<SdkNote>.mapToEntities(semester: Semester) = map {
    Note(
        studentId = semester.studentId,
        date = it.date,
        teacher = it.teacher,
        teacherSymbol = it.teacherSymbol,
        category = it.category,
        categoryType = it.categoryType.id,
        isPointsShow = it.showPoints,
        points = it.points,
        content = it.content
    )
}
