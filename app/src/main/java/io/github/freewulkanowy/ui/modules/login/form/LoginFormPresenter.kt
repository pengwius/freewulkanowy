package io.github.freewulkanowy.ui.modules.login.form

import androidx.core.net.toUri
import io.github.freewulkanowy.data.Resource
import io.github.freewulkanowy.data.db.entities.AdminMessage
import io.github.freewulkanowy.data.enums.MessageType
import io.github.freewulkanowy.data.flatResourceFlow
import io.github.freewulkanowy.data.logResourceStatus
import io.github.freewulkanowy.data.onResourceData
import io.github.freewulkanowy.data.onResourceError
import io.github.freewulkanowy.data.onResourceLoading
import io.github.freewulkanowy.data.onResourceNotLoading
import io.github.freewulkanowy.data.onResourceSuccess
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
import io.github.freewulkanowy.utils.AppInfo
import io.github.freewulkanowy.utils.ifNullOrBlank
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.net.URL
import javax.inject.Inject

class LoginFormPresenter @Inject constructor(
    studentRepository: StudentRepository,
    private val loginErrorHandler: LoginErrorHandler,
    private val appInfo: AppInfo,
    private val analytics: AnalyticsHelper,
    private val getAppropriateAdminMessageUseCase: GetAppropriateAdminMessageUseCase,
    private val preferencesRepository: PreferencesRepository,
) : BasePresenter<LoginFormView>(loginErrorHandler, studentRepository) {

    private var lastError: Throwable? = null

    override fun onAttachView(view: LoginFormView) {
        super.onAttachView(view)
        view.run {
            initView()
            showContact(false)
            showOtherOptionsButton(appInfo.isDebug)
            showVersion()

            loginErrorHandler.onBadCredentials = {
                setErrorPassIncorrect(it.takeIf { !it.isNullOrBlank() })
                showSoftKeyboard()
                Timber.i("Entered wrong username or password")
            }
        }

        reloadAdminMessage()
    }

    private fun reloadAdminMessage() {
        flatResourceFlow {
            getAppropriateAdminMessageUseCase(
                scrapperBaseUrl = view?.formHostValue.orEmpty(),
                type = MessageType.LOGIN_MESSAGE,
            )
        }
            .logResourceStatus("load login admin message")
            .onResourceData { view?.showAdminMessage(it) }
            .onResourceError { view?.showAdminMessage(null) }
            .launch()
    }

    fun onAdminMessageSelected(url: String?) {
        url?.let { view?.openInternetBrowser(it) }
    }

    fun onAdminMessageDismissed(adminMessage: AdminMessage) {
        preferencesRepository.dismissedAdminMessageIds += adminMessage.id

        view?.showAdminMessage(null)
    }

    fun onPrivacyLinkClick() {
        view?.openPrivacyPolicyPage()
    }

    fun onAdvancedLoginClick() {
        view?.openAdvancedLogin()
    }

    fun onHostSelected() {
        view?.apply {
            clearPassError()
            clearUsernameError()
            clearHostError()
            if (formHostValue.contains("wulkanowy")) {
                setCredentials("jan@fakelog.cf", "jan123")
            } else if (formUsernameValue == "jan@fakelog.cf" && formPassValue == "jan123") {
                setCredentials("", "")
            }
            updateCustomDomainSuffixVisibility()
            updateUsernameLabel()
            reloadAdminMessage()
        }
    }

    fun onDomainSuffixChanged() {
        view?.apply {
            clearDomainSuffixError()
        }
    }

    fun updateCustomDomainSuffixVisibility() {
        view?.run {
            showDomainSuffixInput("customSuffix" in formHostValue)
        }
    }

    fun updateUsernameLabel() {
        view?.run {
            setUsernameLabel(if ("email" !in formHostValue) nicknameLabel else emailLabel)
        }
    }

    fun onPassTextChanged() {
        view?.clearPassError()
    }

    fun onUsernameTextChanged() {
        view?.clearUsernameError()

        val username = view?.formUsernameValue.orEmpty().trim()
        if ("@" in username && "@vulcan" !in username) {
            val hosts = view?.getHostsValues().orEmpty().associateBy { it.toUri().host }
            val usernameHost = username.substringAfter("@")

            hosts[usernameHost]?.let {
                view?.run {
                    setHost(it)
                    clearHostError()
                }
            }
        }
    }

    private fun getLoginData(): LoginData {
        val email = view?.formUsernameValue.orEmpty().trim()
        val password = view?.formPassValue.orEmpty().trim()
        val host = view?.formHostValue.orEmpty().trim()
        val domainSuffix = view?.formDomainSuffix.orEmpty().trim().takeIf {
            "customSuffix" in host
        }.orEmpty()
        val symbol = view?.formHostSymbol.orEmpty().trim()

        return LoginData(
            login = email,
            password = password,
            baseUrl = host,
            domainSuffix = domainSuffix,
            defaultSymbol = symbol
        )
    }

    fun onRetryAfterCaptcha() {
        onSignInClick()
    }

    fun onSignInClick() {
        val loginData = getLoginData()

        if (!validateCredentials(loginData)) return

        resourceFlow {
            studentRepository.getStudentsHybrid(
                email = loginData.login,
                password = loginData.password,
                scrapperBaseUrl = loginData.baseUrl,
                domainSuffix = loginData.domainSuffix,
                symbol = loginData.defaultSymbol,
            )
        }
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
                            defaultSymbol = "",
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

    fun onFaqClick() {
        view?.openFaqPage()
    }

    fun onEmailClick() {
        view?.openEmail(
            LoginSupportInfo(
                loginData = getLoginData(),
                lastErrorMessage = lastError?.message,
                registerUser = null,
                enteredSymbol = null,
            )
        )
    }

    fun onRecoverClick() {
        view?.onRecoverClick()
    }

    private fun validateCredentials(loginData: LoginData): Boolean {
        var isCorrect = true

        if (loginData.login.isEmpty()) {
            view?.setErrorUsernameRequired()
            isCorrect = false
        } else {
            if ("@" in loginData.login && "login" in loginData.baseUrl) {
                view?.setErrorLoginRequired()
                isCorrect = false
            }
            if ("@" !in loginData.login && "email" in loginData.baseUrl) {
                view?.setErrorEmailRequired()
                isCorrect = false
            }

            val isEmailLogin = "@" in loginData.login
            val isEmailWithLogin = "||" !in loginData.login
            val isLoginNotRequired = "login" !in loginData.baseUrl
            val isEmailNotRequired = "email" !in loginData.baseUrl
            if (isEmailLogin && isEmailWithLogin && isLoginNotRequired && isEmailNotRequired) {
                val emailHost = loginData.login.substringAfter("@")
                val emailDomain = URL(loginData.baseUrl).host
                if (!emailHost.equals(emailDomain, true)) {
                    view?.setErrorEmailInvalid(domain = emailDomain)
                    isCorrect = false
                }
            }
        }

        if (loginData.password.isEmpty()) {
            view?.setErrorPassRequired(focus = isCorrect)
            isCorrect = false
        }

        if (loginData.password.length < 6 && loginData.password.isNotEmpty()) {
            view?.setErrorPassInvalid(focus = isCorrect)
            isCorrect = false
        }

        if (loginData.domainSuffix !in listOf("", "rc", "kurs")) {
            view?.setDomainSuffixInvalid()
            isCorrect = false
        }

        return isCorrect
    }
}
