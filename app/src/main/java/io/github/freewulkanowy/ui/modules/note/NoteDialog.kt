package io.github.freewulkanowy.ui.modules.note

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Note
import io.github.freewulkanowy.databinding.DialogNoteBinding
import io.github.freewulkanowy.sdk.scrapper.notes.NoteCategory
import io.github.freewulkanowy.ui.base.BaseDialogFragment
import io.github.freewulkanowy.utils.getThemeAttrColor
import io.github.freewulkanowy.utils.serializable
import io.github.freewulkanowy.utils.toFormattedString

@AndroidEntryPoint
class NoteDialog : BaseDialogFragment<DialogNoteBinding>() {

    private lateinit var note: Note

    companion object {

        private const val ARGUMENT_KEY = "Item"

        fun newInstance(note: Note) = NoteDialog().apply {
            arguments = bundleOf(ARGUMENT_KEY to note)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        note = requireArguments().serializable(ARGUMENT_KEY)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(DialogNoteBinding.inflate(layoutInflater).apply { binding = this }.root)
            .create()
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            noteDialogDateValue.text = note.date.toFormattedString()
            noteDialogCategoryValue.text = note.category
            noteDialogTeacherValue.text = note.teacher
            noteDialogContentValue.text = note.content
        }

        if (note.isPointsShow) {
            with(binding.noteDialogPointsValue) {
                text = "${if (note.points > 0) "+" else ""}${note.points}"
                setTextColor(
                    when (NoteCategory.getByValue(note.categoryType)) {
                        NoteCategory.POSITIVE -> ContextCompat.getColor(
                            requireContext(),
                            R.color.note_positive
                        )
                        NoteCategory.NEGATIVE -> ContextCompat.getColor(
                            requireContext(),
                            R.color.note_negative
                        )
                        else -> requireContext().getThemeAttrColor(android.R.attr.textColorPrimary)
                    }
                )
            }
        }

        binding.noteDialogClose.setOnClickListener { dismiss() }
    }
}
