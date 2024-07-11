package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.StudentGuardian
import io.github.freewulkanowy.data.db.entities.StudentInfo
import io.github.freewulkanowy.data.enums.Gender
import io.github.freewulkanowy.sdk.pojo.StudentGuardian as SdkStudentGuardian
import io.github.freewulkanowy.sdk.pojo.StudentInfo as SdkStudentInfo

fun SdkStudentInfo.mapToEntity(semester: Semester) = StudentInfo(
    studentId = semester.studentId,
    fullName = fullName,
    firstName = firstName,
    secondName = secondName,
    surname = surname,
    birthDate = birthDate,
    birthPlace = birthPlace,
    gender = Gender.valueOf(gender.name),
    hasPolishCitizenship = hasPolishCitizenship,
    familyName = familyName,
    parentsNames = parentsNames,
    address = address,
    registeredAddress = registeredAddress,
    correspondenceAddress = correspondenceAddress,
    phoneNumber = phoneNumber,
    cellPhoneNumber = phoneNumber,
    email = email,
    firstGuardian = guardianFirst?.mapToEntity(),
    secondGuardian = guardianSecond?.mapToEntity()
)

fun SdkStudentGuardian.mapToEntity() = StudentGuardian(
    fullName = fullName,
    kinship = kinship,
    address = address,
    phones = phones,
    email = email
)
