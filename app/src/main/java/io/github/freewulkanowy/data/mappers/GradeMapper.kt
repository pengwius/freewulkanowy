package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Grade
import io.github.freewulkanowy.data.db.entities.GradeDescriptive
import io.github.freewulkanowy.data.db.entities.GradeSummary
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.sdk.pojo.Grade as SdkGrade
import io.github.freewulkanowy.sdk.pojo.GradeDescriptive as SdkGradeDescriptive
import io.github.freewulkanowy.sdk.pojo.GradeSummary as SdkGradeSummary

fun List<SdkGrade>.mapToEntities(semester: Semester) = map {
    Grade(
        studentId = semester.studentId,
        semesterId = semester.semesterId,
        subject = it.subject,
        entry = it.entry,
        value = it.value,
        modifier = it.modifier,
        comment = it.comment,
        color = it.color,
        gradeSymbol = it.symbol,
        description = it.description,
        weight = it.weight,
        weightValue = it.weightValue,
        date = it.date,
        teacher = it.teacher
    )
}

@JvmName("mapGradeSummaryToEntities")
fun List<SdkGradeSummary>.mapToEntities(semester: Semester) = map {
    GradeSummary(
        semesterId = semester.semesterId,
        studentId = semester.studentId,
        position = 0,
        subject = it.name,
        predictedGrade = it.predicted,
        finalGrade = it.final,
        pointsSum = it.pointsSum,
        pointsSumAllYear = it.pointsSumAllYear,
        proposedPoints = it.proposedPoints,
        finalPoints = it.finalPoints,
        average = it.average,
        averageAllYear = it.averageAllYear,
    )
}

@JvmName("mapGradeDescriptiveToEntities")
fun List<SdkGradeDescriptive>.mapToEntities(semester: Semester) = map {
    GradeDescriptive(
        semesterId = semester.semesterId,
        studentId = semester.studentId,
        subject = it.subject,
        description = it.description
    )
}


