package io.github.freewulkanowy.ui.modules.login.support

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import dagger.hilt.android.AndroidEntryPoint
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.databinding.DialogLoginSupportBinding
import io.github.freewulkanowy.sdk.scrapper.login.AccountPermissionException
import io.github.freewulkanowy.sdk.scrapper.login.InvalidSymbolException
import io.github.freewulkanowy.ui.base.BaseDialogFragment
import io.github.freewulkanowy.utils.AppInfo
import io.github.freewulkanowy.utils.openEmailClient
import io.github.freewulkanowy.utils.serializable
import javax.inject.Inject

@AndroidEntryPoint
class LoginSupportDialog : BaseDialogFragment<DialogLoginSupportBinding>() {

    @Inject
    lateinit var appInfo: AppInfo

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private lateinit var supportInfo: LoginSupportInfo

    companion object {
        private const val ARGUMENT_KEY = "info"

        fun newInstance(info: LoginSupportInfo) = LoginSupportDialog().apply {
            arguments = bundleOf(ARGUMENT_KEY to info)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.WulkanowyTheme_NoActionBar)
        supportInfo = requireArguments().serializable(ARGUMENT_KEY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogLoginSupportBinding.inflate(inflater)
            .apply { binding = this }
        binding.dialogLoginSupportToolbar.setNavigationOnClickListener { dismiss() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            dialogLoginSupportSchoolInput.doOnTextChanged { _, _, _, _ ->
                with(dialogLoginSupportSchoolLayout) {
                    isErrorEnabled = false
                    error = null
                }
            }
            dialogLoginSupportSubmit.setOnClickListener { onSubmitClick() }
        }
    }

    private fun onSubmitClick() {
        when {
            binding.dialogLoginSupportSchoolInput.text.isNullOrBlank() -> {
                with(binding.dialogLoginSupportSchoolLayout) {
                    isErrorEnabled = true
                    error = getString(R.string.error_field_required)
                }
            }

            Patterns.EMAIL_ADDRESS.matcher(
                binding.dialogLoginSupportSchoolInput.text.toString()
            ).matches() -> {
                with(binding.dialogLoginSupportSchoolLayout) {
                    isErrorEnabled = true
                    error = getString(R.string.login_support_school_invalid)
                }
            }

            else -> {
                openEmailClientWithFilledTemplate()
                dismiss()
            }
        }
    }

    private fun openEmailClientWithFilledTemplate() {
        with(binding) {
            context?.openEmailClient(
                chooserTitle = requireContext().getString(R.string.login_email_intent_title),
                email = "wulkanowyinc@gmail.com",
                subject = requireContext().getString(R.string.login_email_subject),
                body = requireContext().getString(
                    R.string.login_email_text,
                    "${appInfo.systemManufacturer} ${appInfo.systemModel}",
                    appInfo.systemVersion.toString(),
                    "${appInfo.versionName}-${appInfo.buildFlavor}",
                    supportInfo.loginData.let { "${it.baseUrl}/${it.defaultSymbol}/${it.userEnteredSymbol}" },
                    preferencesRepository.installationId,
                    getLastErrorFromStudentSelectScreen(),
                    dialogLoginSupportSchoolInput.text.takeIf { !it.isNullOrBlank() }
                        ?: return@with,
                    dialogLoginSupportAdditionalInput.text,
                )
            )
        }
    }

    private fun getLastErrorFromStudentSelectScreen(): String {
        if (!supportInfo.lastErrorMessage.isNullOrBlank()) {
            return supportInfo.lastErrorMessage!!
        }
        if (supportInfo.registerUser?.symbols.isNullOrEmpty()) {
            return ""
        }

        return "\n" + supportInfo.registerUser?.symbols?.filterNot {
            (it.error is AccountPermissionException || it.error is InvalidSymbolException) &&
                it.symbol != supportInfo.enteredSymbol
        }?.joinToString(";\n") { symbol ->
            buildString {
                append(" -")
                append(symbol.symbol)
                append("(${symbol.error?.message?.let { it.take(46) + "..." } ?: symbol.schools.size})")
                if (symbol.schools.isNotEmpty()) {
                    append(": ")
                }
                append(symbol.schools.joinToString(", ") { unit ->
                    buildString {
                        append(unit.schoolShortName)
                        append("(${unit.error?.message?.let { it.take(46) + "..." } ?: unit.students.size})")
                    }
                })
            }
        } + "\nPozostałe: " + supportInfo.registerUser?.symbols?.filter {
            it.error is AccountPermissionException || it.error is InvalidSymbolException
        }?.joinToString(", ") { it.symbol }
    }
}
