package io.github.freewulkanowy.utils

import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Grade
import io.github.freewulkanowy.data.db.entities.GradeSummary
import io.github.freewulkanowy.data.enums.GradeColorTheme
import io.github.freewulkanowy.sdk.scrapper.grades.getGradeValueWithModifier
import io.github.freewulkanowy.sdk.scrapper.grades.isGradeValid

fun List<Grade>.calcAverage(isOptionalArithmeticAverage: Boolean): Double {
    val isArithmeticAverage = isOptionalArithmeticAverage && !any { it.weightValue != .0 }
    var counter = 0.0
    var denominator = 0.0

    forEach {
        val weight = if (isArithmeticAverage && isGradeValid(it.entry)) 1.0 else it.weightValue
        counter += (it.value + it.modifier) * weight
        denominator += weight
    }
    return if (denominator != 0.0) counter / denominator else 0.0
}

fun List<GradeSummary>.calcFinalAverage(plusModifier: Double, minusModifier: Double) = asSequence()
    .mapNotNull { summary ->
        val (gradeValue, gradeModifier) = getGradeValueWithModifier(summary.finalGrade)
        if (gradeValue == null || gradeModifier == null) return@mapNotNull null

        when {
            gradeModifier > 0 -> gradeValue + plusModifier
            gradeModifier < 0 -> gradeValue - minusModifier
            else -> gradeValue + 0.0
        }
    }
    .average()
    .let { if (it.isNaN()) 0.0 else it }

fun Grade.getGradeColor() = when (color) {
    "000000" -> R.color.grade_black
    "F04C4C" -> R.color.grade_red
    "20A4F7" -> R.color.grade_blue
    "6ECD07" -> R.color.grade_green
    "B16CF1" -> R.color.grade_purple
    else -> R.color.grade_material_default
}

inline val Grade.colorStringId: Int
    get() = when (color) {
        "000000" -> R.string.all_black
        "F04C4C" -> R.string.all_red
        "20A4F7" -> R.string.all_blue
        "6ECD07" -> R.string.all_green
        "B16CF1" -> R.string.all_purple
        else -> R.string.all_empty_color
    }

fun Grade.changeModifier(plusModifier: Double, minusModifier: Double) = when {
    modifier > 0 -> copy(modifier = plusModifier)
    modifier < 0 -> copy(modifier = -minusModifier)
    else -> this
}

fun Grade.getBackgroundColor(theme: GradeColorTheme) = when (theme) {
    GradeColorTheme.GRADE_COLOR -> getGradeColor()
    GradeColorTheme.MATERIAL -> when (value.toInt()) {
        6 -> R.color.grade_material_six
        5 -> R.color.grade_material_five
        4 -> R.color.grade_material_four
        3 -> R.color.grade_material_three
        2 -> R.color.grade_material_two
        1 -> R.color.grade_material_one
        else -> R.color.grade_material_default
    }
    GradeColorTheme.VULCAN -> when (value.toInt()) {
        6 -> R.color.grade_vulcan_six
        5 -> R.color.grade_vulcan_five
        4 -> R.color.grade_vulcan_four
        3 -> R.color.grade_vulcan_three
        2 -> R.color.grade_vulcan_two
        1 -> R.color.grade_vulcan_one
        else -> R.color.grade_vulcan_default
    }
}
