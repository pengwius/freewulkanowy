package io.github.freewulkanowy

import io.github.freewulkanowy.data.db.entities.Mailbox
import io.github.freewulkanowy.data.db.entities.MailboxType
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.sdk.Sdk
import java.time.Instant.now
import java.time.LocalDate
import io.github.freewulkanowy.sdk.pojo.Semester as SdkSemester

fun getSemesterEntity(diaryId: Int = 1, semesterId: Int = 1, start: LocalDate = LocalDate.now(), end: LocalDate = LocalDate.now(), semesterName: Int = 1) = Semester(
    studentId = 1,
    diaryId = diaryId,
    kindergartenDiaryId = 0,
    semesterId = semesterId,
    diaryName = "$semesterId",
    schoolYear = 1970,
    classId = 0,
    semesterName = semesterName,
    unitId = 1,
    start = start,
    end = end
)

fun getMailboxEntity() = Mailbox(
    globalKey = "v4",
    fullName = "",
    userName = "",
    email = "test",
    symbol = "powiatwulkanowy",
    schoolId = "123456",
    studentName = "",
    schoolNameShort = "",
    type = MailboxType.UNKNOWN,
)

fun getSemesterPojo(diaryId: Int, semesterId: Int, start: LocalDate, end: LocalDate, semesterName: Int = 1) = SdkSemester(
    diaryId = diaryId,
    kindergartenDiaryId = 0,
    semesterId = semesterId,
    diaryName = "$semesterId",
    schoolYear = 1970,
    classId = 0,
    className = "Ti",
    semesterNumber = semesterName,
    unitId = 1,
    start = start,
    end = end,
)

fun getStudentEntity(mode: Sdk.Mode = Sdk.Mode.HEBE) = Student(
    scrapperBaseUrl = "http://fakelog.cf",
    scrapperDomainSuffix = "",
    email = "jan@fakelog.cf",
    certificateKey = "",
    classId = 0,
    className = "",
    isCurrent = false,
    isParent = false,
    loginMode = mode.name,
    loginType = "STANDARD",
    mobileBaseUrl = "",
    password = "",
    privateKey = "",
    registrationDate = now(),
    schoolName = "",
    schoolShortName = "test",
    schoolSymbol = "",
    studentId = 1,
    studentName = "",
    symbol = "",
    userLoginId = 1,
    userName = "",
    isEduOne = false,
    isAuthorized = false
).apply {
    id = 1
}
