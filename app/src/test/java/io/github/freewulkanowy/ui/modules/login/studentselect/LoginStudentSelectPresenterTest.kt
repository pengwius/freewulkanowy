package io.github.freewulkanowy.ui.modules.login.studentselect

import io.github.freewulkanowy.MainCoroutineRule
import io.github.freewulkanowy.data.pojos.RegisterStudent
import io.github.freewulkanowy.data.pojos.RegisterSymbol
import io.github.freewulkanowy.data.pojos.RegisterUnit
import io.github.freewulkanowy.data.pojos.RegisterUser
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.SchoolsRepository
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.domain.adminmessage.GetAppropriateAdminMessageUseCase
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.scrapper.Scrapper
import io.github.freewulkanowy.services.sync.SyncManager
import io.github.freewulkanowy.ui.modules.login.LoginData
import io.github.freewulkanowy.ui.modules.login.LoginErrorHandler
import io.github.freewulkanowy.utils.AnalyticsHelper
import io.github.freewulkanowy.utils.AppInfo
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginStudentSelectPresenterTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    @MockK(relaxed = true)
    lateinit var errorHandler: LoginErrorHandler

    @MockK
    lateinit var loginStudentSelectView: LoginStudentSelectView

    @MockK
    lateinit var studentRepository: StudentRepository

    @MockK
    lateinit var schoolsRepository: SchoolsRepository

    @MockK
    lateinit var preferencesRepository: PreferencesRepository

    @MockK
    lateinit var getAppropriateAdminMessageUseCase: GetAppropriateAdminMessageUseCase

    @MockK(relaxed = true)
    lateinit var analytics: AnalyticsHelper

    @MockK(relaxed = true)
    lateinit var syncManager: SyncManager

    private val appInfo = AppInfo()

    private lateinit var presenter: LoginStudentSelectPresenter

    private val loginData = LoginData(
        login = "",
        password = "",
        baseUrl = "",
        defaultSymbol = "warszawa",
        domainSuffix = "",
    )

    private val subject = RegisterStudent(
        studentId = 0,
        studentName = "",
        studentSecondName = "",
        studentSurname = "",
        className = "",
        classId = 0,
        isParent = false,
        semesters = listOf(),
        isEduOne = false,
        isAuthorized = false,
    )

    private val school = RegisterUnit(
        userLoginId = 0,
        schoolId = "",
        schoolName = "",
        schoolShortName = "",
        parentIds = listOf(),
        studentIds = listOf(),
        employeeIds = listOf(),
        error = null,
        students = listOf(subject)
    )

    private val symbol = RegisterSymbol(
        symbol = "",
        error = null,
        userName = "",
        keyId = null,
        privatePem = null,
        hebeBaseUrl = null,
        schools = listOf(school),
    )

    private val registerUser = RegisterUser(
        email = "",
        password = "",
        login = "",
        scrapperBaseUrl = "",
        loginMode = Sdk.Mode.SCRAPPER,
        loginType = Scrapper.LoginType.AUTO,
        symbols = listOf(symbol),
    )

    private val testException by lazy { RuntimeException("Problem") }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        clearMocks(studentRepository, loginStudentSelectView)

        coEvery { studentRepository.getSavedStudents(false) } returns emptyList()
        coEvery { schoolsRepository.logSchoolLogin(any(), any()) } just Runs

        every { loginStudentSelectView.initView() } just Runs
        every { loginStudentSelectView.symbols } returns emptyMap()

        every { loginStudentSelectView.enableSignIn(any()) } just Runs
        every { loginStudentSelectView.showProgress(any()) } just Runs
        every { loginStudentSelectView.showContent(any()) } just Runs

        presenter = LoginStudentSelectPresenter(
            studentRepository = studentRepository,
            schoolsRepository = schoolsRepository,
            loginErrorHandler = errorHandler,
            syncManager = syncManager,
            analytics = analytics,
            appInfo = appInfo,
            preferencesRepository = preferencesRepository,
            getAppropriateAdminMessageUseCase = getAppropriateAdminMessageUseCase
        )
    }

    @Test
    fun initViewTest() {
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)
        verify { loginStudentSelectView.initView() }
    }

    @Test
    fun onSelectedStudentTest() {
        val itemsSlot = slot<List<LoginStudentSelectItem>>()
        every { loginStudentSelectView.updateData(capture(itemsSlot)) } just Runs
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)

        coEvery { studentRepository.saveStudents(any()) } just Runs

        every { loginStudentSelectView.navigateToNext() } just Runs

        itemsSlot.captured.filterIsInstance<LoginStudentSelectItem.Student>().first().let {
            it.onClick(it)
        }
        presenter.onSignIn()

        verify { loginStudentSelectView.showContent(false) }
        verify { loginStudentSelectView.showProgress(true) }
        verify { loginStudentSelectView.navigateToNext() }
    }

    @Test
    fun onSelectedStudentErrorTest() {
        val itemsSlot = slot<List<LoginStudentSelectItem>>()
        every { loginStudentSelectView.updateData(capture(itemsSlot)) } just Runs
        presenter.onAttachView(loginStudentSelectView, loginData, registerUser)

        coEvery { studentRepository.saveStudents(any()) } throws testException

        itemsSlot.captured.filterIsInstance<LoginStudentSelectItem.Student>().first().let {
            it.onClick(it)
        }
        presenter.onSignIn()

        verify { loginStudentSelectView.showContent(false) }
        verify { loginStudentSelectView.showProgress(true) }
        verify { errorHandler.dispatch(match { testException.message == it.message }) }
    }
}
