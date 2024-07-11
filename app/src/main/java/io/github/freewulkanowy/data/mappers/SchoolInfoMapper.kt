package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.School
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.sdk.pojo.School as SdkSchool

fun SdkSchool.mapToEntity(semester: Semester) = School(
    studentId = semester.studentId,
    classId = semester.classId,
    name = name,
    address = address,
    contact = contact,
    headmaster = headmaster,
    pedagogue = pedagogue
)
