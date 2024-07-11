package io.github.freewulkanowy.ui.modules.note

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Note
import io.github.freewulkanowy.databinding.ItemNoteBinding
import io.github.freewulkanowy.sdk.scrapper.notes.NoteCategory
import io.github.freewulkanowy.utils.getThemeAttrColor
import io.github.freewulkanowy.utils.toFormattedString
import javax.inject.Inject

class NoteAdapter @Inject constructor() : RecyclerView.Adapter<NoteAdapter.ItemViewHolder>() {

    var items = mutableListOf<Note>()

    var onClickListener: (Note, position: Int) -> Unit = { _, _ -> }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ItemViewHolder(
        ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]

        with(holder.binding) {
            with(noteItemDate) {
                text = item.date.toFormattedString()
                setTypeface(null, if (item.isRead) Typeface.NORMAL else Typeface.BOLD)
            }
            with(noteItemType) {
                text = item.category
                setTypeface(null, if (item.isRead) Typeface.NORMAL else Typeface.BOLD)
            }
            with(noteItemPoints) {
                text = "${if (item.points > 0) "+" else ""}${item.points}"
                visibility = if (item.isPointsShow) View.VISIBLE else View.GONE
                setTextColor(when (NoteCategory.getByValue(item.categoryType)) {
                    NoteCategory.POSITIVE -> ContextCompat.getColor(context, R.color.note_positive)
                    NoteCategory.NEGATIVE -> ContextCompat.getColor(context, R.color.note_negative)
                    else -> context.getThemeAttrColor(android.R.attr.textColorPrimary)
                })
            }
            noteItemTeacher.text = item.teacher
            noteItemContent.text = item.content

            root.setOnClickListener { onClickListener(item, position) }
        }
    }

    class ItemViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)
}
