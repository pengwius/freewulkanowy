package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Timetable
import io.github.freewulkanowy.sdk.pojo.Attendance
import io.github.freewulkanowy.sdk.scrapper.attendance.SentExcuseStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class AttendanceMapperTest {

    @Test
    fun `map attendance when fallback is not necessary`() {
        val attendance = listOf(
            getSdkAttendance(1, LocalDate.of(2022, 11, 17), "Oryginalna 1"),
            getSdkAttendance(2, LocalDate.of(2022, 11, 17), "Oryginalna 2"),
        )
        val lessons = listOf(
            getEntityTimetable(1, LocalDate.of(2022, 11, 17), "Pierwsza"),
            getEntityTimetable(2, LocalDate.of(2022, 11, 17), "Druga"),
        )

        val result = attendance.mapToEntities(getEntitySemester(), lessons)
        assertEquals("Oryginalna 1", result[0].subject)
        assertEquals("Oryginalna 2", result[1].subject)
    }

    @Test
    fun `map attendance when fallback is not always necessary`() {
        val attendance = listOf(
            getSdkAttendance(1, LocalDate.of(2022, 11, 17), "Oryginalna 1"),
            getSdkAttendance(2, LocalDate.of(2022, 11, 17), ""),
        )
        val lessons = listOf(
            getEntityTimetable(1, LocalDate.of(2022, 11, 17), "Pierwsza"),
            getEntityTimetable(2, LocalDate.of(2022, 11, 17), "Druga"),
        )

        val result = attendance.mapToEntities(getEntitySemester(), lessons)
        assertEquals("Oryginalna 1", result[0].subject)
        assertEquals("Druga", result[1].subject)
    }

    @Test
    fun `map attendance when fallback is sometimes empty`() {
        val attendance = listOf(
            getSdkAttendance(1, LocalDate.of(2022, 11, 17), "Oryginalna 1"),
            getSdkAttendance(2, LocalDate.of(2022, 11, 17), ""),
        )
        val lessons = listOf(
            getEntityTimetable(1, LocalDate.of(2022, 11, 17), "Pierwsza"),
        )

        val result = attendance.mapToEntities(getEntitySemester(), lessons)
        assertEquals("Oryginalna 1", result[0].subject)
        assertEquals("", result[1].subject)
    }

    @Test
    fun `map attendance when fallback is empty`() {
        val attendance = listOf(
            getSdkAttendance(1, LocalDate.of(2022, 11, 17), ""),
            getSdkAttendance(2, LocalDate.of(2022, 11, 17), ""),
        )
        val lessons = listOf(
            getEntityTimetable(1, LocalDate.of(2022, 11, 18), "Pierwsza"),
            getEntityTimetable(2, LocalDate.of(2022, 10, 17), "Druga"),
        )

        val result = attendance.mapToEntities(getEntitySemester(), lessons)
        assertEquals("", result[0].subject)
        assertEquals("", result[1].subject)
    }

    @Test
    fun `map attendance with all subject fallback`() {
        val attendance = listOf(
            getSdkAttendance(1, LocalDate.of(2022, 11, 17)),
            getSdkAttendance(2, LocalDate.of(2022, 11, 17)),
        )
        val lessons = listOf(
            getEntityTimetable(1, LocalDate.of(2022, 11, 17), "Pierwsza"),
            getEntityTimetable(2, LocalDate.of(2022, 11, 17), "Druga"),
        )

        val result = attendance.mapToEntities(getEntitySemester(), lessons)
        assertEquals("Pierwsza", result[0].subject)
        assertEquals("Druga", result[1].subject)
    }

    private fun getSdkAttendance(number: Int, date: LocalDate, subject: String = "") = Attendance(
        number = number,
        name = "ABSENCE",
        subject = subject,
        date = date,
        timeId = 1,
        categoryId = 1,
        deleted = false,
        excuseStatus = SentExcuseStatus.WAITING,
        excusable = false,
        absence = false,
        excused = false,
        exemption = false,
        lateness = false,
        presence = false,
    )

    private fun getEntityTimetable(number: Int, date: LocalDate, subject: String = "") = Timetable(
        number = number,
        start = Instant.now(),
        end = Instant.now(),
        date = date,
        subject = subject,
        subjectOld = "",
        group = "",
        room = "",
        roomOld = "",
        teacher = "",
        teacherOld = "",
        info = "",
        changes = false,
        canceled = false,
        studentId = 0,
        diaryId = 0,
        isStudentPlan = false,
    )

    private fun getEntitySemester() = Semester(
        studentId = 0,
        diaryId = 0,
        kindergartenDiaryId = 0,
        diaryName = "",
        schoolYear = 0,
        semesterId = 0,
        semesterName = 0,
        start = LocalDate.now(),
        end = LocalDate.now(),
        classId = 0,
        unitId = 0
    )
}
