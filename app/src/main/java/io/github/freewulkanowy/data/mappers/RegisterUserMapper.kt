package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.db.entities.StudentWithSemesters
import io.github.freewulkanowy.data.pojos.*
import java.time.Instant
import io.github.freewulkanowy.sdk.pojo.RegisterStudent as SdkRegisterStudent
import io.github.freewulkanowy.sdk.pojo.RegisterUser as SdkRegisterUser

fun SdkRegisterUser.mapToPojo(password: String?) = RegisterUser(
    email = email,
    login = login,
    password = password,
    scrapperBaseUrl = scrapperBaseUrl,
    loginMode = loginMode,
    loginType = loginType,
    symbols = symbols.map { registerSymbol ->
        RegisterSymbol(
            symbol = registerSymbol.symbol,
            error = registerSymbol.error,
            hebeBaseUrl = registerSymbol.hebeBaseUrl,
            keyId = registerSymbol.keyId,
            privatePem = registerSymbol.privatePem,
            userName = registerSymbol.userName,
            schools = registerSymbol.schools.map {
                RegisterUnit(
                    userLoginId = it.userLoginId,
                    schoolId = it.schoolId,
                    schoolName = it.schoolName,
                    schoolShortName = it.schoolShortName,
                    parentIds = it.parentIds,
                    studentIds = it.studentIds,
                    employeeIds = it.employeeIds,
                    error = it.error,
                    students = it.subjects
                        .filterIsInstance<SdkRegisterStudent>()
                        .map { registerStudent ->
                            RegisterStudent(
                                studentId = registerStudent.studentId,
                                studentName = registerStudent.studentName,
                                studentSecondName = registerStudent.studentSecondName,
                                studentSurname = registerStudent.studentSurname,
                                className = registerStudent.className,
                                classId = registerStudent.classId,
                                isParent = registerStudent.isParent,
                                isAuthorized = registerStudent.isAuthorized,
                                isEduOne = registerStudent.isEduOne,
                                semesters = registerStudent.semesters
                                    .mapToEntities(registerStudent.studentId),
                            )
                        },
                )
            }
        )
    },
)

fun RegisterStudent.mapToStudentWithSemesters(
    user: RegisterUser,
    scrapperDomainSuffix: String,
    symbol: RegisterSymbol,
    unit: RegisterUnit,
    colors: List<Long>,
): StudentWithSemesters = StudentWithSemesters(
    semesters = semesters,
    student = Student(
        email = user.login, // for compatibility
        userName = symbol.userName,
        userLoginId = unit.userLoginId,
        isParent = isParent,
        className = className,
        classId = classId,
        studentId = studentId,
        symbol = symbol.symbol,
        loginType = user.loginType?.name.orEmpty(),
        schoolName = unit.schoolName,
        schoolShortName = unit.schoolShortName,
        schoolSymbol = unit.schoolId,
        studentName = "$studentName $studentSurname",
        loginMode = user.loginMode.name,
        scrapperBaseUrl = user.scrapperBaseUrl.orEmpty(),
        scrapperDomainSuffix = scrapperDomainSuffix,
        mobileBaseUrl = symbol.hebeBaseUrl.orEmpty(),
        certificateKey = symbol.keyId.orEmpty(),
        privateKey = symbol.privatePem.orEmpty(),
        password = user.password.orEmpty(),
        isCurrent = false,
        registrationDate = Instant.now(),
        isAuthorized = this.isAuthorized,
        isEduOne = this.isEduOne,
    ).apply {
        avatarColor = colors.random()
    },
)
