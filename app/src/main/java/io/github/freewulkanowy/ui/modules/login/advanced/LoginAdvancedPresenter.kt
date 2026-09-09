package io.github.freewulkanowy.ui.modules.login.advanced

import io.github.freewulkanowy.data.Resource
import io.github.freewulkanowy.data.logResourceStatus
import io.github.freewulkanowy.data.onResourceNotLoading
import io.github.freewulkanowy.data.pojos.RegisterUser
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.data.resourceFlow
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.scrapper.getNormalizedSymbol
import io.github.freewulkanowy.ui.base.BasePresenter
import io.github.freewulkanowy.ui.modules.login.LoginData
import io.github.freewulkanowy.ui.modules.login.LoginErrorHandler
import io.github.freewulkanowy.utils.AnalyticsHelper
import io.github.freewulkanowy.utils.ifNullOrBlank
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class LoginAdvancedPresenter @Inject constructor(
    studentRepository: StudentRepository,
    private val loginErrorHandler: LoginErrorHandler,
    private val analytics: AnalyticsHelper
) : BasePresenter<LoginAdvancedView>(loginErrorHandler, studentRepository) {

    override fun onAttachView(view: LoginAdvancedView) {
        super.onAttachView(view)
        view.run {
            initView()
            showOnlyScrapperModeInputs()
            with(loginErrorHandler) {
                onBadCredentials = ::onBadCredentials
                onInvalidToken = ::onInvalidToken
                onInvalidSymbol = ::onInvalidSymbol
                onInvalidPin = ::onInvalidPin
            }
        }
    }

    private fun onBadCredentials(message: String?) {
        view?.run {
            setErrorPassIncorrect(message)
            showSoftKeyboard()
            Timber.i("Entered wrong username or password")
        }
    }

    private fun onInvalidToken(message: String) {
        view?.run {
            setErrorTokenInvalid(message)
            showSoftKeyboard()
            Timber.i("Entered invalid token")
        }
    }

    private fun onInvalidSymbol(message: String) {
        view?.run {
            setErrorSymbolInvalid(message)
            showSoftKeyboard()
            Timber.i("Entered invalid symbol")
        }
    }

    private fun onInvalidPin(message: String) {
        view?.run {
            setErrorPinInvalid(message)
            showSoftKeyboard()
            Timber.i("Entered invalid PIN")
        }
    }

    fun updateUsernameLabel() {
        view?.apply {
            setUsernameLabel(if ("vulcan" in formHostValue || "wulkanowy" in formHostValue) emailLabel else nicknameLabel)
        }
    }

    fun onHostSelected() {
        view?.apply {
            clearPassError()
            clearUsernameError()
            if (formHostValue.contains("wulkanowy")) {
                setDefaultCredentials(
                    "jan@fakelog.cf", "jan123", "powiatwulkanowy", "FK100000", "999999"
                )
            }
            setSymbol(formHostSymbol)
            updateUsernameLabel()
        }
    }

    fun onLoginModeSelected(type: Sdk.Mode) {
        view?.run {
            when (type) {
                Sdk.Mode.HEBE -> {
                    showOnlyMobileApiModeInputs()
                    showMobileApiWarningMessage()
                }

                Sdk.Mode.SCRAPPER -> {
                    showOnlyScrapperModeInputs()
                    showScraperWarningMessage()
                }

                Sdk.Mode.HYBRID -> {
                    showOnlyHybridModeInputs()
                    showHybridWarningMessage()
                }
            }
        }
    }

    fun onPassTextChanged() {
        view?.clearPassError()
    }

    fun onUsernameTextChanged() {
        view?.clearUsernameError()
    }

    fun onPinTextChanged() {
        view?.clearPinKeyError()
    }

    fun onSymbolTextChanged() {
        view?.clearSymbolError()
    }

    fun onTokenTextChanged() {
        view?.clearTokenError()
    }

    fun onSignInClick() {
        if (!validateCredentials()) return

        resourceFlow { getStudentsAppropriatesToLoginType() }
            .logResourceStatus("login")
            .onEach {
                when (it) {
                    is Resource.Loading -> view?.run {
                        hideSoftKeyboard()
                        showProgress(true)
                        showContent(false)
                    }

                    is Resource.Success -> {
                        analytics.logEvent(
                            "registration_form",
                            "success" to true,
                            "scrapperBaseUrl" to view?.formHostValue.orEmpty(),
                            "error" to "No error"
                        )
                        val loginData = LoginData(
                            login = view?.formUsernameValue.orEmpty().trim(),
                            password = view?.formPassValue.orEmpty().trim(),
                            baseUrl = view?.formHostValue.orEmpty().trim(),
                            domainSuffix = view?.formDomainSuffix.orEmpty().trim(),
                            defaultSymbol = view?.formSymbolValue.orEmpty().trim().getNormalizedSymbol(),
                        )
                        when (it.data.symbols.size) {
                            0 -> view?.navigateToSymbol(loginData)
                            else -> view?.navigateToStudentSelect(
                                loginData = loginData,
                                registerUser = it.data,
                            )
                        }
                    }

                    is Resource.Error -> {
                        analytics.logEvent(
                            "registration_form",
                            "success" to false, "students" to -1,
                            "error" to it.error.message.ifNullOrBlank { "No message" }
                        )
                        loginErrorHandler.dispatch(it.error)
                    }
                }
            }.onResourceNotLoading {
                view?.apply {
                    showProgress(false)
                    showContent(true)
                }
            }.launch("login")
    }

    private suspend fun getStudentsAppropriatesToLoginType(): RegisterUser {
        val email = view?.formUsernameValue.orEmpty()
        val password = view?.formPassValue.orEmpty()
        val endpoint = view?.formHostValue.orEmpty()
        val domainSuffix = view?.formDomainSuffix.orEmpty()

        val pin = view?.formPinValue.orEmpty()
        val symbol = view?.formSymbolValue.orEmpty()
        val token = view?.formTokenValue.orEmpty()

        return when (Sdk.Mode.valueOf(view?.formLoginType.orEmpty())) {
            Sdk.Mode.HEBE -> studentRepository.getStudentsApi(pin, symbol, token)
            Sdk.Mode.SCRAPPER -> studentRepository.getUserSubjectsFromScrapper(
                email, password, endpoint, domainSuffix, symbol
            )

            Sdk.Mode.HYBRID -> studentRepository.getStudentsHybrid(
                email, password, endpoint, domainSuffix, symbol
            )
        }
    }

    private fun validateCredentials(): Boolean {
        val login = view?.formUsernameValue.orEmpty()
        val password = view?.formPassValue.orEmpty()

        val host = view?.formHostValue.orEmpty()

        val pin = view?.formPinValue.orEmpty()
        val symbol = view?.formSymbolValue.orEmpty()
        val token = view?.formTokenValue.orEmpty()

        var isCorrect = true

        when (Sdk.Mode.valueOf(view?.formLoginType.orEmpty())) {
            Sdk.Mode.HEBE -> {
                if (pin.isEmpty()) {
                    view?.setErrorPinRequired()
                    isCorrect = false
                }

                if (symbol.isEmpty()) {
                    view?.setErrorSymbolRequired()
                    isCorrect = false
                }

                if (token.isEmpty()) {
                    view?.setErrorTokenRequired()
                    isCorrect = false
                }
            }

            Sdk.Mode.HYBRID, Sdk.Mode.SCRAPPER -> {
                if (login.isEmpty()) {
                    view?.setErrorUsernameRequired()
                    isCorrect = false
                } else {
                    if ("@" in login && "login" in host) {
                        view?.setErrorLoginRequired()
                        isCorrect = false
                    }
                    if ("@" !in login && "email" in host) {
                        view?.setErrorEmailRequired()
                        isCorrect = false
                    }
                }

                if (password.isEmpty()) {
                    view?.setErrorPassRequired(focus = isCorrect)
                    isCorrect = false
                }

                if (password.length < 6 && password.isNotEmpty()) {
                    view?.setErrorPassInvalid(focus = isCorrect)
                    isCorrect = false
                }
            }
        }

        return isCorrect
    }
}
