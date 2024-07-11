package io.github.freewulkanowy.utils

import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Attendance
import io.github.freewulkanowy.data.db.entities.AttendanceSummary
import io.github.freewulkanowy.sdk.scrapper.attendance.AttendanceCategory

/**
 * [UONET+ - Zasady tworzenia podsumowań liczb uczniów obecnych i nieobecnych w tabeli frekwencji]
 * (https://www.vulcan.edu.pl/vulcang_files/user/AABW/AABW-PDF/uonetplus/uonetplus_Frekwencja-liczby-obecnych-nieobecnych.pdf)
 */

inline val AttendanceSummary.allPresences: Int
    get() = presence + absenceForSchoolReasons + lateness + latenessExcused

inline val AttendanceSummary.allAbsences: Int
    get() = absence + absenceExcused

inline val Attendance.isExcusableOrNotExcused: Boolean
    get() = (excusable || ((absence || lateness) && !excused)) && excuseStatus == null

fun AttendanceSummary.calculatePercentage() = calculatePercentage(allPresences.toDouble(), allAbsences.toDouble())

fun List<AttendanceSummary>.calculatePercentage(): Double {
    return calculatePercentage(sumOf { it.allPresences.toDouble() }, sumOf { it.allAbsences.toDouble() })
}

private fun calculatePercentage(presence: Double, absence: Double): Double {
    return if ((presence + absence) == 0.0) 0.0 else (presence / (presence + absence)) * 100
}

inline val Attendance.descriptionRes
    get() = when (AttendanceCategory.getCategoryByName(name)) {
        AttendanceCategory.PRESENCE -> R.string.attendance_present
        AttendanceCategory.ABSENCE_UNEXCUSED -> R.string.attendance_absence_unexcused
        AttendanceCategory.ABSENCE_EXCUSED -> R.string.attendance_absence_excused
        AttendanceCategory.UNEXCUSED_LATENESS -> R.string.attendance_unexcused_lateness
        AttendanceCategory.EXCUSED_LATENESS -> R.string.attendance_excused_lateness
        AttendanceCategory.ABSENCE_FOR_SCHOOL_REASONS -> R.string.attendance_absence_school
        AttendanceCategory.EXEMPTION -> R.string.attendance_exemption
        AttendanceCategory.DELETED -> R.string.attendance_deleted
        else -> R.string.attendance_unknown
    }
