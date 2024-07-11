package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Attendance
import io.github.freewulkanowy.data.db.entities.AttendanceSummary
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Timetable
import io.github.freewulkanowy.sdk.pojo.Attendance as SdkAttendance
import io.github.freewulkanowy.sdk.pojo.AttendanceSummary as SdkAttendanceSummary

fun List<SdkAttendance>.mapToEntities(semester: Semester, lessons: List<Timetable>) = map {
    Attendance(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        date = it.date,
        timeId = it.timeId,
        number = it.number,
        subject = it.subject.ifBlank {
            lessons.find { lesson ->
                lesson.date == it.date && lesson.number == it.number
            }?.subject.orEmpty()
        },
        name = it.name,
        presence = it.presence,
        absence = it.absence,
        exemption = it.exemption,
        lateness = it.lateness,
        excused = it.excused,
        deleted = it.deleted,
        excusable = it.excusable,
        excuseStatus = it.excuseStatus?.name
    )
}

fun List<SdkAttendanceSummary>.mapToEntities(semester: Semester, subjectId: Int) = map {
    AttendanceSummary(
        studentId = semester.studentId,
        diaryId = semester.diaryId,
        subjectId = subjectId,
        month = it.month,
        presence = it.presence,
        absence = it.absence,
        absenceExcused = it.absenceExcused,
        absenceForSchoolReasons = it.absenceForSchoolReasons,
        lateness = it.lateness,
        latenessExcused = it.latenessExcused,
        exemption = it.exemption
    )
}
