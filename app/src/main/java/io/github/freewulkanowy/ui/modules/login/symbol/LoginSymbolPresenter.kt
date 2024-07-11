package io.github.freewulkanowy.ui.modules.login.symbol

import io.github.freewulkanowy.data.Resource
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.entities.AdminMessage
import io.github.freewulkanowy.data.enums.MessageType
import io.github.freewulkanowy.data.flatResourceFlow
import io.github.freewulkanowy.data.logResourceStatus
import io.github.freewulkanowy.data.onResourceData
import io.github.freewulkanowy.data.onResourceError
import io.github.freewulkanowy.data.onResourceNotLoading
import io.github.freewulkanowy.data.pojos.RegisterUser
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.data.resourceFlow
import io.github.freewulkanowy.domain.adminmessage.GetAppropriateAdminMessageUseCase
import io.github.freewulkanowy.sdk.scrapper.getNormalizedSymbol
import io.github.freewulkanowy.sdk.scrapper.login.InvalidSymbolException
import io.github.freewulkanowy.ui.base.BasePresenter
import io.github.freewulkanowy.ui.modules.login.LoginData
import io.github.freewulkanowy.ui.modules.login.LoginErrorHandler
import io.github.freewulkanowy.ui.modules.login.support.LoginSupportInfo
import io.github.freewulkanowy.utils.AnalyticsHelper
import io.github.freewulkanowy.utils.ifNullOrBlank
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class LoginSymbolPresenter @Inject constructor(
    studentRepository: StudentRepository,
    private val loginErrorHandler: LoginErrorHandler,
    private val analytics: AnalyticsHelper,
    private val preferencesRepository: PreferencesRepository,
    private val getAppropriateAdminMessageUseCase: GetAppropriateAdminMessageUseCase,
) : BasePresenter<LoginSymbolView>(loginErrorHandler, studentRepository) {

    private var lastError: Throwable? = null

    lateinit var loginData: LoginData

    private var registerUser: RegisterUser? = null

    fun onAttachView(view: LoginSymbolView, loginData: LoginData) {
        super.onAttachView(view)
        this.loginData = loginData
        loginErrorHandler.onBadCredentials = {
            view.setErrorSymbol(it.orEmpty())
        }
        with(view) {
            initView()
            showContact(false)
            setLoginToHeading(loginData.login)
            clearAndFocusSymbol()
            showSoftKeyboard()
        }

        loadAdminMessage()
    }

    private fun loadAdminMessage() {
        flatResourceFlow {
            getAppropriateAdminMessageUseCase(
                scrapperBaseUrl = loginData.baseUrl,
                type = MessageType.LOGIN_SYMBOL_MESSAGE,
            )
        }
            .logResourceStatus("load login admin message")
            .onResourceData { view?.showAdminMessage(it) }
            .onResourceError { view?.showAdminMessage(null) }
            .launch("load_admin_message")
    }

    fun onSymbolTextChanged() {
        view?.apply { if (symbolNameError != null) clearSymbolError() }
    }

    fun attemptLogin() {
        if (view?.symbolValue.isNullOrBlank()) {
            view?.setErrorSymbolRequire()
            return
        }
        if (isFormDefinitelyInvalid()) {
            view?.setErrorSymbolDefinitelyInvalid()
            return
        }

        loginData = loginData.copy(
            userEnteredSymbol = view?.symbolValue?.getNormalizedSymbol(),
        )
        resourceFlow {
            studentRepository.getUserSubjectsFromScrapper(
                email = loginData.login,
                password = loginData.password,
                scrapperBaseUrl = loginData.baseUrl,
                domainSuffix = loginData.domainSuffix,
                symbol = loginData.userEnteredSymbol.orEmpty(),
            )
        }.onEach { user ->
            registerUser = user.dataOrNull
            when (user) {
                is Resource.Loading -> view?.run {
                    Timber.i("Login with symbol started")
                    hideSoftKeyboard()
                    showProgress(true)
                    showContent(false)
                }

                is Resource.Success -> {
                    when (user.data.symbols.size) {
                        0 -> {
                            Timber.i("Login with symbol result: Empty student list")
                            view?.run {
                                setErrorSymbolIncorrect()
                                showContact(true)
                            }
                        }

                        else -> {
                            val enteredSymbolDetails = user.data.symbols
                                .firstOrNull()
                                ?.takeIf { it.symbol == loginData.userEnteredSymbol }

                            if (enteredSymbolDetails?.error is InvalidSymbolException) {
                                showInvalidSymbolError()
                            } else {
                                Timber.i("Login with symbol result: Success")
                                view?.navigateToStudentSelect(loginData, requireNotNull(user.data))
                            }
                        }
                    }
                    analytics.logEvent(
                        "registration_symbol",
                        "success" to true,
                        "scrapperBaseUrl" to loginData.baseUrl,
                        "symbol" to view?.symbolValue,
                        "error" to "No error"
                    )
                }

                is Resource.Error -> {
                    Timber.i("Login with symbol result: An exception occurred")
                    analytics.logEvent(
                        "registration_symbol",
                        "success" to false,
                        "students" to -1,
                        "scrapperBaseUrl" to loginData.baseUrl,
                        "symbol" to view?.symbolValue,
                        "error" to user.error.message.ifNullOrBlank { "No message" }
                    )
                    loginErrorHandler.dispatch(user.error)
                    lastError = user.error
                    view?.showContact(true)
                    if (user.error is InvalidSymbolException) {
                        showInvalidSymbolError()
                    }
                }
            }
        }.onResourceNotLoading {
            view?.apply {
                showProgress(false)
                showContent(true)
            }
        }.launch("login")
    }

    private fun isFormDefinitelyInvalid(): Boolean {
        val definitelyInvalidSymbols = listOf("vulcan", "uonet", "wulkanowy", "standardowa")
        val normalizedSymbol = view?.symbolValue.orEmpty().getNormalizedSymbol()

        return normalizedSymbol in definitelyInvalidSymbols
    }

    private fun showInvalidSymbolError() {
        view?.run {
            setErrorSymbolInvalid()
            showContact(true)
        }
    }

    fun onFaqClick() {
        view?.openFaqPage()
    }

    fun onEmailClick() {
        view?.openSupportDialog(
            LoginSupportInfo(
                loginData = loginData,
                registerUser = registerUser,
                lastErrorMessage = lastError?.message,
                enteredSymbol = view?.symbolValue,
            )
        )
    }

    fun onAdminMessageSelected(url: String?) {
        url?.let { view?.openInternetBrowser(it) }
    }

    fun onAdminMessageDismissed(adminMessage: AdminMessage) {
        preferencesRepository.dismissedAdminMessageIds += adminMessage.id

        view?.showAdminMessage(null)
    }
}
