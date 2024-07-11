package io.github.freewulkanowy.data.mappers

import io.github.freewulkanowy.data.db.entities.LuckyNumber
import io.github.freewulkanowy.data.db.entities.Student
import java.time.LocalDate
import io.github.freewulkanowy.sdk.pojo.LuckyNumber as SdkLuckyNumber

fun SdkLuckyNumber.mapToEntity(student: Student) = LuckyNumber(
    studentId = student.studentId,
    date = LocalDate.now(),
    luckyNumber = number
)
