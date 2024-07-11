package io.github.freewulkanowy.ui.modules.grade.summary

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.databinding.ItemGradeSummaryBinding
import io.github.freewulkanowy.databinding.ScrollableHeaderGradeSummaryBinding
import io.github.freewulkanowy.sdk.scrapper.grades.isGradeValid
import io.github.freewulkanowy.utils.calcFinalAverage
import io.github.freewulkanowy.utils.ifNullOrBlank
import java.util.Locale
import javax.inject.Inject

class GradeSummaryAdapter @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private enum class ViewType(val id: Int) {
        HEADER(1),
        ITEM(2)
    }

    var items = emptyList<GradeSummaryItem>()

    var onCalculatedHelpClickListener: () -> Unit = {}

    var onFinalHelpClickListener: () -> Unit = {}

    override fun getItemCount() = items.size + if (items.isNotEmpty()) 1 else 0

    override fun getItemViewType(position: Int) = when (position) {
        0 -> ViewType.HEADER.id
        else -> ViewType.ITEM.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            ViewType.HEADER.id -> HeaderViewHolder(
                ScrollableHeaderGradeSummaryBinding.inflate(inflater, parent, false)
            )

            ViewType.ITEM.id -> ItemViewHolder(
                ItemGradeSummaryBinding.inflate(inflater, parent, false)
            )

            else -> throw IllegalStateException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> bindHeaderViewHolder(holder.binding)
            is ItemViewHolder -> bindItemViewHolder(holder.binding, items[position - 1])
        }
    }

    private fun bindHeaderViewHolder(binding: ScrollableHeaderGradeSummaryBinding) {
        if (items.isEmpty()) return
        val gradeSummaries = items
            .filter { it.gradeDescriptive == null }
            .map { it.gradeSummary }
        val isSecondSemester = items.any { item ->
            item.gradeSummary.let { it.averageAllYear != null && it.averageAllYear != .0 }
        }

        val context = binding.root.context
        val finalItemsCount = gradeSummaries.count { isGradeValid(it.finalGrade) }
        val calculatedSemesterItemsCount = gradeSummaries.count { value -> value.average != 0.0 }
        val calculatedAnnualItemsCount =
            gradeSummaries.count { value -> value.averageAllYear != 0.0 }
        val allItemsCount = gradeSummaries.count { !it.subject.equals("zachowanie", true) }
        val finalAverage = gradeSummaries.calcFinalAverage(
            plusModifier = preferencesRepository.gradePlusModifier,
            minusModifier = preferencesRepository.gradeMinusModifier,
        )
        val calculatedSemesterAverage = gradeSummaries.filter { value -> value.average != 0.0 }
            .map { values -> values.average }
            .reversed() // fix average precision
            .average()
            .let { if (it.isNaN()) 0.0 else it }
        val calculatedAnnualAverage = gradeSummaries.filter { value -> value.averageAllYear != 0.0 }
            .mapNotNull { values -> values.averageAllYear }
            .reversed() // fix average precision
            .average()
            .let { if (it.isNaN()) 0.0 else it }

        with(binding) {
            gradeSummaryScrollableHeaderCalculated.text = formatAverage(calculatedSemesterAverage)
            gradeSummaryScrollableHeaderCalculatedAnnual.text =
                formatAverage(calculatedAnnualAverage)
            gradeSummaryScrollableHeaderFinal.text = formatAverage(finalAverage)
            gradeSummaryScrollableHeaderFinalSubjectCount.text = context.getString(
                R.string.grade_summary_from_subjects,
                finalItemsCount,
                allItemsCount
            )
            gradeSummaryScrollableHeaderCalculatedSubjectCount.text = context.getString(
                R.string.grade_summary_from_subjects,
                calculatedSemesterItemsCount,
                allItemsCount
            )
            gradeSummaryScrollableHeaderCalculatedSubjectCountAnnual.text = context.getString(
                R.string.grade_summary_from_subjects,
                calculatedAnnualItemsCount,
                allItemsCount
            )
            gradeSummaryScrollableHeaderCalculatedAnnualContainer.isVisible = isSecondSemester

            gradeSummaryCalculatedAverageHelp.setOnClickListener { onCalculatedHelpClickListener() }
            gradeSummaryCalculatedAverageHelpAnnual.setOnClickListener { onCalculatedHelpClickListener() }
            gradeSummaryFinalAverageHelp.setOnClickListener { onFinalHelpClickListener() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindItemViewHolder(binding: ItemGradeSummaryBinding, item: GradeSummaryItem) {
        val (gradeSummary, gradeDescriptive) = item

        with(binding) {
            gradeSummaryItemTitle.text = gradeSummary.subject
            gradeSummaryItemPoints.text = gradeSummary.pointsSum

            gradeSummaryItemAverage.text = formatAverage(gradeSummary.average, "")
            gradeSummaryItemAverageAllYear.text = gradeSummary.averageAllYear?.let {
                formatAverage(it, "")
            }

            gradeSummaryItemPredicted.text =
                "${gradeSummary.predictedGrade} ${gradeSummary.proposedPoints}".trim()
            gradeSummaryItemFinal.text =
                "${gradeSummary.finalGrade} ${gradeSummary.finalPoints}".trim()
            gradeSummaryItemDescriptive.text = gradeDescriptive?.description.ifNullOrBlank {
                root.context.getString(R.string.all_no_data)
            }

            gradeSummaryItemAverageContainer.isVisible = gradeSummary.average != .0
            gradeSummaryItemAverageDivider.isVisible = gradeSummaryItemAverageContainer.isVisible
            gradeSummaryItemAverageAllYearContainer.isGone =
                gradeSummary.averageAllYear == null || gradeSummary.averageAllYear == .0
            gradeSummaryItemAverageAllYearDivider.isGone =
                gradeSummaryItemAverageAllYearContainer.isGone
            gradeSummaryItemFinalDivider.isVisible = gradeDescriptive == null
            gradeSummaryItemPredictedDivider.isVisible = gradeDescriptive == null
            gradeSummaryItemPointsDivider.isVisible = gradeDescriptive == null
            gradeSummaryItemPredictedContainer.isVisible = gradeDescriptive == null
            gradeSummaryItemFinalContainer.isVisible = gradeDescriptive == null
            gradeSummaryItemDescriptiveContainer.isVisible = gradeDescriptive != null
            gradeSummaryItemPointsContainer.isVisible = gradeSummary.pointsSum.isNotBlank()
            gradeSummaryItemPointsDivider.isVisible = gradeSummaryItemPointsContainer.isVisible
        }
    }

    private fun formatAverage(average: Double, defaultValue: String = "-- --"): String {
        return if (average == 0.0) defaultValue
        else String.format(Locale.FRANCE, "%.2f", average)
    }

    private class HeaderViewHolder(val binding: ScrollableHeaderGradeSummaryBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class ItemViewHolder(val binding: ItemGradeSummaryBinding) :
        RecyclerView.ViewHolder(binding.root)
}
