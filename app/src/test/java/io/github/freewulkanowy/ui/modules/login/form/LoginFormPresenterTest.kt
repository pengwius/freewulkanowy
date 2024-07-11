package io.github.freewulkanowy.ui.modules.login.form

import io.github.freewulkanowy.MainCoroutineRule
import io.github.freewulkanowy.data.pojos.RegisterUser
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.domain.adminmessage.GetAppropriateAdminMessageUseCase
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.scrapper.Scrapper
import io.github.freewulkanowy.ui.modules.login.LoginErrorHandler
import io.github.freewulkanowy.utils.AnalyticsHelper
import io.github.freewulkanowy.utils.AppInfo
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class LoginFormPresenterTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    @MockK(relaxed = true)
    lateinit var loginFormView: LoginFormView

    @MockK
    lateinit var repository: StudentRepository

    @MockK(relaxed = true)
    lateinit var errorHandler: LoginErrorHandler

    @MockK(relaxed = true)
    lateinit var analytics: AnalyticsHelper

    @MockK
    lateinit var appInfo: AppInfo

    @MockK
    lateinit var getAppropriateAdminMessageUseCase: GetAppropriateAdminMessageUseCase

    @MockK
    lateinit var preferencesRepository: PreferencesRepository

    private lateinit var presenter: LoginFormPresenter

    private val registerUser = RegisterUser(
        email = "",
        password = "",
        login = "",
        scrapperBaseUrl = "",
        loginMode = Sdk.Mode.HEBE,
        loginType = Scrapper.LoginType.AUTO,
        symbols = listOf(),
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { loginFormView.initView() } just Runs
        every { loginFormView.showContact(any()) } just Runs
        every { loginFormView.showVersion() } just Runs
        every { loginFormView.showProgress(any()) } just Runs
        every { loginFormView.showContent(any()) } just Runs
        every { loginFormView.formHostSymbol } returns "Default"
        every { loginFormView.setErrorPassInvalid(any()) } just Runs
        every { loginFormView.setErrorPassRequired(any()) } just Runs
        every { loginFormView.setErrorUsernameRequired() } just Runs
        every { appInfo.isDebug } returns false

        presenter = LoginFormPresenter(
            studentRepository = repository,
            loginErrorHandler = errorHandler,
            appInfo = appInfo,
            analytics = analytics,
            getAppropriateAdminMessageUseCase = getAppropriateAdminMessageUseCase,
            preferencesRepository = preferencesRepository,
        )
        presenter.onAttachView(loginFormView)
    }

    @Test
    fun initViewTest() {
        verify { loginFormView.initView() }
    }

    @Test
    fun emptyNicknameLoginTest() {
        every { loginFormView.formUsernameValue } returns ""
        every { loginFormView.formPassValue } returns "test123"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        presenter.onSignInClick()

        verify { loginFormView.setErrorUsernameRequired() }
        verify(exactly = 0) { loginFormView.setErrorPassRequired(true) }
        verify(exactly = 0) { loginFormView.setErrorPassInvalid(false) }
    }

    @Test
    fun invalidEmailLoginTest() {
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123456"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/"
        presenter.onSignInClick()

        verify { loginFormView.setErrorEmailInvalid("fakelog.cf") }
    }

    @Test
    fun emptyPassLoginTest() {
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns ""
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        presenter.onSignInClick()

        verify(exactly = 0) { loginFormView.setErrorUsernameRequired() }
        verify { loginFormView.setErrorPassRequired(true) }
        verify(exactly = 0) { loginFormView.setErrorPassInvalid(false) }
    }

    @Test
    fun invalidPassLoginTest() {
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        presenter.onSignInClick()

        verify(exactly = 0) { loginFormView.setErrorUsernameRequired() }
        verify(exactly = 0) { loginFormView.setErrorPassRequired(true) }
        verify { loginFormView.setErrorPassInvalid(true) }
    }

    @Test
    fun loginTest() {
        coEvery {
            repository.getUserSubjectsFromScrapper(any(), any(), any(), any(), any())
        } returns registerUser

        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123456"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        every { loginFormView.formHostSymbol } returns "Default"
        presenter.onSignInClick()

        verify { loginFormView.hideSoftKeyboard() }
        verify { loginFormView.showProgress(true) }
        verify { loginFormView.showProgress(false) }
        verify { loginFormView.showContent(false) }
        verify { loginFormView.showContent(true) }
    }

    @Test
    fun loginEmptyTest() {
        coEvery {
            repository.getUserSubjectsFromScrapper(any(), any(), any(), any(), any())
        } returns registerUser
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123456"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        every { loginFormView.formHostSymbol } returns "Default"
        presenter.onSignInClick()

        verify { loginFormView.showContent(false) }
        verify { loginFormView.showProgress(true) }
        verify { loginFormView.showProgress(false) }
        verify { loginFormView.showContent(false) }
        verify { loginFormView.showContent(true) }
    }

    @Test
    fun loginEmptyTwiceTest() {
        coEvery {
            repository.getUserSubjectsFromScrapper(any(), any(), any(), any(), any())
        } returns registerUser
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123456"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        every { loginFormView.formHostSymbol } returns "Default"
        presenter.onSignInClick()
        presenter.onSignInClick()

        verify(exactly = 2) { loginFormView.hideSoftKeyboard() }
        verify(exactly = 2) { loginFormView.showProgress(true) }
        verify(exactly = 2) { loginFormView.showProgress(false) }
        verify(exactly = 2) { loginFormView.showContent(false) }
        verify(exactly = 2) { loginFormView.showContent(true) }
    }

    @Test
    fun loginErrorTest() {
        val testException = IOException("test")
        coEvery {
            repository.getUserSubjectsFromScrapper(any(), any(), any(), any(), any())
        } throws testException
        every { loginFormView.formUsernameValue } returns "@"
        every { loginFormView.formPassValue } returns "123456"
        every { loginFormView.formHostValue } returns "https://fakelog.cf/?email"
        every { loginFormView.formHostSymbol } returns "Default"
        every { loginFormView.showProgress(any()) } just Runs
        every { loginFormView.showProgress(any()) } just Runs
        presenter.onSignInClick()

        verify { loginFormView.hideSoftKeyboard() }
        verify { loginFormView.showProgress(false) }
        verify { loginFormView.showContent(false) }
        verify { loginFormView.showContent(true) }
        verify { errorHandler.dispatch(match { it.message == testException.message }) }
    }
}
