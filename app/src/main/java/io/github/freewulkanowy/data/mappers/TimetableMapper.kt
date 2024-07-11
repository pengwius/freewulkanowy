package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Timetable
import io.github.freewulkanowy.data.db.entities.TimetableAdditional
import io.github.freewulkanowy.data.db.entities.TimetableHeader
import io.github.freewulkanowy.data.pojos.TimetableFull
import io.github.freewulkanowy.sdk.pojo.Timetable as SdkTimetableFull
import io.github.freewulkanowy.sdk.pojo.TimetableDayHeader as SdkTimetableHeader
import io.github.freewulkanowy.sdk.pojo.Lesson as SdkLesson
import io.github.freewulkanowy.sdk.pojo.LessonAdditional as SdkTimetableAdditional

fun SdkTimetableFull.mapToEntities(semester: Semester) = TimetableFull(
    lessons = lessons.mapToEntities(semester),
    additional = additional.mapToEntities(semester),
    headers = headers.mapToEntities(semester)
)

fun List<SdkLesson>.mapToEntities(semester: Semester) = map {
    Timetable(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        number = it.number,
        start = it.start.toInstant(),
        end = it.end.toInstant(),
        date = it.date,
        subject = it.subject,
        subjectOld = it.subjectOld,
        group = it.group,
        room = it.room,
        roomOld = it.roomOld,
        teacher = it.teacher,
        teacherOld = it.teacherOld,
        info = it.info,
        isStudentPlan = it.studentPlan,
        changes = it.changes,
        canceled = it.canceled
    )
}

@JvmName("mapToEntitiesTimetableAdditional")
fun List<SdkTimetableAdditional>.mapToEntities(semester: Semester) = map {
    TimetableAdditional(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        subject = it.subject,
        date = it.date,
        start = it.start.toInstant(),
        end = it.end.toInstant(),
    )
}

@JvmName("mapToEntitiesTimetableHeaders")
fun List<SdkTimetableHeader>.mapToEntities(semester: Semester) = map {
    TimetableHeader(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        date = it.date,
        content = it.content
    )
}
