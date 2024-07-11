package io.github.freewulkanowy.ui.modules.grade

import io.github.freewulkanowy.data.Resource
import io.github.freewulkanowy.data.dataOrNull
import io.github.freewulkanowy.data.db.entities.Grade
import io.github.freewulkanowy.data.db.entities.GradeSummary
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.errorOrNull
import io.github.freewulkanowy.data.repositories.GradeRepository
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.SemesterRepository
import io.github.freewulkanowy.data.resourceFlow
import io.github.freewulkanowy.data.toFirstResult
import io.github.freewulkanowy.getSemesterEntity
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.utils.Status
import io.github.freewulkanowy.utils.status
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate.now
import java.time.LocalDate.of

class GradeAverageProviderTest {

    private suspend fun <T> Flow<Resource<T>>.getResult() = toFirstResult().let {
        it.dataOrNull ?: throw it.errorOrNull ?: error("Unknown state")
    }

    @MockK
    lateinit var preferencesRepository: PreferencesRepository

    @MockK
    lateinit var semesterRepository: SemesterRepository

    @MockK
    lateinit var gradeRepository: GradeRepository

    private lateinit var gradeAverageProvider: GradeAverageProvider

    private val student = Student(
        scrapperBaseUrl = "",
        scrapperDomainSuffix = "",
        mobileBaseUrl = "",
        loginType = "",
        loginMode = "SCRAPPER",
        certificateKey = "",
        privateKey = "",
        isParent = false,
        email = "",
        password = "",
        symbol = "",
        studentId = 101,
        userLoginId = 0,
        userName = "",
        studentName = "",
        schoolSymbol = "",
        schoolShortName = "",
        schoolName = "",
        className = "",
        classId = 1,
        isCurrent = true,
        registrationDate = Instant.now(),
        isAuthorized = false,
        isEduOne = false
    )

    private val semesters = mutableListOf(
        getSemesterEntity(10, 21, of(2019, 1, 31), of(2019, 6, 23)),
        getSemesterEntity(11, 22, of(2019, 9, 1), of(2020, 1, 31)),
        getSemesterEntity(11, 23, of(2020, 2, 1), now(), semesterName = 2)
    )

    private val firstGrades = listOf(
        // avg: 3.5
        getGrade(22, "Matematyka", 4.0),
        getGrade(22, "Matematyka", 3.0),

        // avg: 3.5
        getGrade(22, "Fizyka", 6.0),
        getGrade(22, "Fizyka", 1.0)
    )

    private val firstSummaries = listOf(
        getSummary(semesterId = 22, subject = "Matematyka", average = 3.9),
        getSummary(semesterId = 22, subject = "Fizyka", average = 3.1)
    )

    private val secondGrades = listOf(
        // avg: 2.5
        getGrade(23, "Matematyka", 2.0),
        getGrade(23, "Matematyka", 3.0),

        // avg: 3.0
        getGrade(23, "Fizyka", 4.0),
        getGrade(23, "Fizyka", 2.0)
    )

    private val secondSummaries = listOf(
        getSummary(semesterId = 23, subject = "Matematyka", average = 2.9),
        getSummary(semesterId = 23, subject = "Fizyka", average = 3.4)
    )

    private val secondGradeWithModifier = listOf(
        // avg: 3.375
        getGrade(24, "Język polski", 3.0, -0.50, entry = "3-"),
        getGrade(24, "Język polski", 4.0, 0.25, entry = "4+")
    )

    private val secondSummariesWithModifier = listOf(
        getSummary(24, "Język polski", 3.49)
    )

    private val noWeightGrades = listOf(
        // standard: 0.0, arithmetic: 4.0
        getGrade(22, "Matematyka", 5.0, 0.0, 0.0, "5"),
        getGrade(22, "Matematyka", 3.0, 0.0, 0.0, "3"),
        getGrade(22, "Matematyka", 1.0, 0.0, 0.0, "np.")
    )

    private val noWeightGradesSummary = listOf(
        getSummary(23, "Matematyka", 0.0)
    )

    private val noWeightGradesArithmeticSummary = listOf(
        getSummary(23, "Matematyka", .0)
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33)
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.33)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)

        gradeAverageProvider =
            GradeAverageProvider(semesterRepository, gradeRepository, preferencesRepository)
    }

    @Test
    fun `calc current semester standard average with no weights`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(noWeightGrades, noWeightGradesSummary, emptyList())
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            0.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // from summary: 0,0
    }

    @Test
    fun `calc current semester arithmetic average with no weights`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(true)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(noWeightGrades, noWeightGradesArithmeticSummary, emptyList())
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            4.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // from summary: 4,0
    }

    @Test
    fun `calc current semester arithmetic average with no weights in second semester`() = runTest {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(true)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        coEvery {
            gradeRepository.getGrades(
                student = student,
                semester = semesters[1],
                forceRefresh = true,
            )
        } returns resourceFlow {
            Triple(
                first = noWeightGrades,
                second = noWeightGradesArithmeticSummary,
                third = emptyList(),
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student = student,
                semester = semesters[2],
                forceRefresh = true,
            )
        } returns resourceFlow {
            Triple(
                first = noWeightGrades,
                second = noWeightGradesArithmeticSummary,
                third = emptyList(),
            )
        }

        val items = gradeAverageProvider.getGradesDetailsWithAverage(
            student = student,
            semesterId = semesters[2].semesterId,
            forceRefresh = true
        ).getResult()

        assertEquals(
            4.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // from summary: 4,0
    }

    @Test
    fun `calc current semester average with load from cache sequence`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns flow {
            emit(Resource.Loading())
            emit(
                Resource.Intermediate(
                    Triple(
                        secondGradeWithModifier,
                        secondSummariesWithModifier,
                        emptyList()
                    )
                )
            )
            emit(
                Resource.Success(
                    Triple(
                        secondGradeWithModifier,
                        secondSummariesWithModifier,
                        emptyList()
                    )
                )
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).toList()
        }

        with(items[0]) {
            assertEquals(Status.LOADING, status)
            assertEquals(null, dataOrNull)
        }
        with(items[1]) {
            assertEquals(Status.LOADING, status)
            assertEquals(1, dataOrNull?.size)
        }
        with(items[2]) {
            assertEquals(Status.SUCCESS, status)
            assertEquals(1, dataOrNull?.size)
        }

        assertEquals(
            3.5,
            items[1].dataOrNull?.single { it.subject == "Język polski" }?.average ?: 0.0,
            .0
        ) // from details and after set custom plus/minus
    }

    @Test
    fun `calc all year semester average with delayed emit`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)

        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery { gradeRepository.getGrades(student, semesters[2], false) } returns flow {
            emit(Resource.Loading())
            delay(1000)
            emit(
                Resource.Success(
                    Triple(
                        secondGradeWithModifier,
                        secondSummariesWithModifier,
                        emptyList()
                    )
                )
            )
        }
        coEvery { gradeRepository.getGrades(student, semesters[1], false) } returns flow {
            emit(Resource.Loading())
            emit(
                Resource.Success(
                    Triple(
                        secondGradeWithModifier,
                        secondSummariesWithModifier,
                        emptyList()
                    )
                )
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                false
            ).toList()
        }

        with(items[0]) {
            assertEquals(Status.LOADING, status)
            assertEquals(null, dataOrNull)
        }
        with(items[1]) {
            assertEquals(Status.SUCCESS, status)
            assertEquals(1, dataOrNull?.size)
        }

        assertEquals(
            3.5,
            items[1].dataOrNull?.single { it.subject == "Język polski" }?.average ?: 0.0,
            .0
        ) // from details and after set custom plus/minus
    }

    @Test
    fun `calc both semesters average with grade without grade in second semester`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                false
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                false
            )
        } returns resourceFlow {
            Triple(
                listOf(getGrade(semesters[2].semesterId, "Język polski", .0, .0, .0)),
                listOf(getSummary(semesters[2].semesterId, "Język polski", 2.5)),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                false
            ).getResult()
        }

        assertEquals(3.5, items.single { it.subject == "Język polski" }.average, .0)
    }

    @Test
    fun `calc both semesters average with no grade in second semester but with average in first semester`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                false
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                false
            )
        } returns resourceFlow {
            Triple(
                emptyList(), listOf(
                    getSummary(
                        24,
                        "Język polski",
                        .0
                    )
                ), emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                false
            ).getResult()
        }

        assertEquals(3.49, items.single { it.subject == "Język polski" }.average, .0)
    }

    @Test
    fun `force calc average on no grades`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                emptyList(),
                emptyList(),
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(emptyList(), emptyList(), emptyList())
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(0, items.size)
    }

    @Test
    fun `force calc current semester average with default modifiers in scraper mode`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            3.5,
            items.single { it.subject == "Język polski" }.average,
            .0
        ) // from details and after set custom plus/minus
    }

    @Test
    fun `force calc current semester average with custom modifiers in scraper mode`() {
        val student = student.copy(loginMode = Sdk.Mode.SCRAPPER.name)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33)
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.33)

        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            3.5,
            items.single { it.subject == "Język polski" }.average,
            .0
        ) // from details and after set custom plus/minus
    }

    @Test
    fun `force calc current semester average with custom modifiers in api mode`() {
        val student = student.copy(loginMode = Sdk.Mode.HEBE.name)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33)  // useless in this mode
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.33)

        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            3.375,
            items.single { it.subject == "Język polski" }.average,
            .0
        ) // (from details): 3.375
    }

    @Test
    fun `force calc current semester average with custom modifiers in hybrid mode`() {
        val student = student.copy(loginMode = Sdk.Mode.HYBRID.name)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33) // useless in this mode
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.33)

        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGradeWithModifier,
                secondSummariesWithModifier,
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            3.375,
            items.single { it.subject == "Język polski" }.average,
            .0
        ) // (from details): 3.375
    }

    @Test
    fun `calc current semester average`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, secondSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            2.9,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // from summary: 2,9
        assertEquals(3.4, items.single { it.subject == "Fizyka" }.average, .0) // from details: 3,4
    }

    @Test
    fun `force calc current semester average`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ONE_SEMESTER)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, secondSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            2.5,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // from details: 2,5
        assertEquals(3.0, items.single { it.subject == "Fizyka" }.average, .0) // from details: 3,0
    }

    @Test
    fun `force calc full year average when current is first`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow { Triple(firstGrades, firstSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[1].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.5,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from summary): 3,5
        assertEquals(
            3.5,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from summary): 3,5
    }

    @Test
    fun `calc full year average when current is first with load from cache sequence`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns flow {
            emit(Resource.Loading())
            emit(Resource.Intermediate(Triple(firstGrades, firstSummaries, emptyList())))
            emit(Resource.Success(Triple(firstGrades, firstSummaries, emptyList())))
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[1].semesterId,
                true
            ).toList()
        }

        with(items[0]) {
            assertEquals(Status.LOADING, status)
            assertEquals(null, dataOrNull)
        }
        with(items[1]) {
            assertEquals(Status.LOADING, status)
            assertEquals(2, dataOrNull?.size)
        }
        with(items[2]) {
            assertEquals(Status.SUCCESS, status)
            assertEquals(2, dataOrNull?.size)
        }

        assertEquals(2, items[2].dataOrNull?.size)
        assertEquals(
            3.5,
            items[2].dataOrNull?.single { it.subject == "Matematyka" }?.average ?: 0.0,
            .0
        ) // (from summary): 3,5
        assertEquals(
            3.5,
            items[2].dataOrNull?.single { it.subject == "Fizyka" }?.average ?: 0.0,
            .0
        ) // (from summary): 3,5
    }

    @Test
    fun `calc both semesters average`() {
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                firstGrades, listOf(
                    getSummary(22, "Matematyka", 3.0),
                    getSummary(22, "Fizyka", 3.5)
                ), emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGrades,
                listOf(
                    getSummary(22, "Matematyka", 3.5),
                    getSummary(22, "Fizyka", 4.0)
                ),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.25,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from summaries ↑): 3,0 + 3,5 → 3,25
        assertEquals(
            3.75,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from summaries ↑): 3,5 + 4,0 → 3,75
    }

    @Test
    fun `calc both semesters average when current is second with load from cache sequence`() {
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns flow {
            emit(Resource.Loading())
            emit(
                Resource.Intermediate(
                    Triple(
                        firstGrades, listOf(
                            getSummary(22, "Matematyka", 3.0),
                            getSummary(22, "Fizyka", 3.5)
                        ), emptyList()
                    )
                )
            )
            emit(
                Resource.Success(
                    Triple(
                        firstGrades, listOf(
                            getSummary(22, "Matematyka", 3.0),
                            getSummary(22, "Fizyka", 3.5)
                        ), emptyList()
                    )
                )
            )
        }
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns flow {
            emit(Resource.Loading())
            emit(
                Resource.Intermediate(
                    Triple(
                        secondGrades, listOf(
                            getSummary(22, "Matematyka", 3.5),
                            getSummary(22, "Fizyka", 4.0)
                        ), emptyList()
                    )
                )
            )
            emit(
                Resource.Success(
                    Triple(
                        secondGrades, listOf(
                            getSummary(22, "Matematyka", 3.5),
                            getSummary(22, "Fizyka", 4.0)
                        ), emptyList()
                    )
                )
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).toList()
        }

        with(items[0]) {
            assertEquals(Status.LOADING, status)
            assertEquals(null, dataOrNull)
        }
        with(items[1]) {
            assertEquals(Status.LOADING, status)
            assertEquals(2, dataOrNull?.size)
        }
        with(items[2]) {
            assertEquals(Status.SUCCESS, status)
            assertEquals(2, dataOrNull?.size)
        }

        assertEquals(2, items[2].dataOrNull?.size)
        assertEquals(
            3.25,
            items[2].dataOrNull?.single { it.subject == "Matematyka" }?.average ?: 0.0,
            .0
        ) // (from summaries ↑): 3,0 + 3,5 → 3,25
        assertEquals(
            3.75,
            items[2].dataOrNull?.single { it.subject == "Fizyka" }?.average ?: 0.0,
            .0
        ) // (from summaries ↑): 3,5 + 4,0 → 3,75
    }

    @Test
    fun `force calc full year average`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow { Triple(firstGrades, firstSummaries, emptyList()) }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGrades, listOf(
                    getSummary(22, "Matematyka", 1.1),
                    getSummary(22, "Fizyka", 7.26)
                ), emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from details): 3,5 + 2,5 → 3,0
        assertEquals(
            3.25,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from details): 3,5  + 3,0 → 3,25
    }

    @Test
    fun `calc all year average`() {
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                firstGrades, listOf(
                    getSummary(22, "Matematyka", .0),
                    getSummary(22, "Fizyka", .0)
                ), emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGrades, listOf(
                    getSummary(22, "Matematyka", .0),
                    getSummary(22, "Fizyka", .0)
                ), emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(3.0, items.single { it.subject == "Matematyka" }.average, .0)
        assertEquals(3.25, items.single { it.subject == "Fizyka" }.average, .0)
    }

    @Test
    fun `force calc full year average when current is second with load from cache sequence`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        coEvery { semesterRepository.getSemesters(student) } returns semesters
        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns flow {
            emit(Resource.Loading())
            emit(Resource.Intermediate(Triple(firstGrades, firstSummaries, emptyList())))
            emit(Resource.Success(Triple(firstGrades, firstSummaries, emptyList())))
        }
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns flow {
            emit(Resource.Loading())
            emit(
                Resource.Intermediate(
                    Triple(
                        secondGrades, listOf(
                            getSummary(22, "Matematyka", 1.1),
                            getSummary(22, "Fizyka", 7.26)
                        ), emptyList()
                    )
                )
            )
            emit(
                Resource.Success(
                    Triple(
                        secondGrades, listOf(
                            getSummary(22, "Matematyka", 1.1),
                            getSummary(22, "Fizyka", 7.26)
                        ), emptyList()
                    )
                )
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).toList()
        }

        with(items[0]) {
            assertEquals(Status.LOADING, status)
            assertEquals(null, dataOrNull)
        }
        with(items[1]) {
            assertEquals(Status.LOADING, status)
            assertEquals(2, dataOrNull?.size)
        }
        with(items[2]) {
            assertEquals(Status.SUCCESS, status)
            assertEquals(2, dataOrNull?.size)
        }

        assertEquals(2, items[2].dataOrNull?.size)
        assertEquals(
            3.0,
            items[2].dataOrNull?.single { it.subject == "Matematyka" }?.average ?: 0.0,
            .0
        ) // (from details): 3,5 + 2,5 → 3,0
        assertEquals(
            3.25,
            items[2].dataOrNull?.single { it.subject == "Fizyka" }?.average ?: 0.0,
            .0
        ) // (from details): 3,5  + 3,0 → 3,25
    }

    @Test
    fun `force calc both semesters average when no summaries`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow { Triple(firstGrades, emptyList(), emptyList()) }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, emptyList(), emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from details): 3,5 + 2,5 → 3,0
        assertEquals(
            3.25,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from details): 3,5 + 3,0 → 3,25
    }

    @Test
    fun `force calc full year average when no summaries`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow { Triple(firstGrades, emptyList(), emptyList()) }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, emptyList(), emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from details): 3,5 + 2,5 → 3,0
        assertEquals(
            3.25,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from details): 3,5  + 3,0 → 3,25
    }

    @Test
    fun `calc both semesters average when missing summaries in both semesters`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                firstGrades, listOf(
                    getSummary(22, "Matematyka", 4.0)
                ), emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGrades, listOf(
                    getSummary(23, "Matematyka", 3.0)
                ), emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.5,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from summaries ↑): 4,0 + 3,0 → 3,5
        assertEquals(
            3.25,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from details): 3,5  + 3,0 → 3,25
    }

    @Test
    fun `calc both semesters average when missing summary in second semester`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow { Triple(firstGrades, firstSummaries, emptyList()) }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                secondGrades,
                secondSummaries.dropLast(1),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.4,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from summaries): 3,9 + 2,9 → 3,4
        assertEquals(
            3.05,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // 3,1 (from summary) + 3,0 (from details) → 3,05
    }

    @Test
    fun `calc both semesters average when missing summary in first semester`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                firstGrades,
                firstSummaries.dropLast(1),
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, secondSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.4,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from summaries): 3,9 + 2,9 → 3,4
        assertEquals(
            3.45,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // 3,5 (from details) + 3,4 (from summary) → 3,45
    }

    @Test
    fun `force calc full year average when missing summary in first semester`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                firstGrades,
                firstSummaries.dropLast(1),
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow { Triple(secondGrades, secondSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(2, items.size)
        assertEquals(
            3.0,
            items.single { it.subject == "Matematyka" }.average,
            .0
        ) // (from details): 3,5 + 2,5 → 3,0
        assertEquals(
            3.25,
            items.single { it.subject == "Fizyka" }.average,
            .0
        ) // (from details): 3,5  + 3,0 → 3,25
    }

    @Test
    fun `force calc both semesters average with different average from all grades and from two semesters`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(22, "Fizyka", 5.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 5.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 22, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(23, "Fizyka", 5.0, weight = 1.0),
                    getGrade(23, "Fizyka", 5.0, weight = 2.0),
                    getGrade(23, "Fizyka", 4.0, modifier = 0.3, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 23, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            5.2296,
            items.single { it.subject == "Fizyka" }.average,
            .0001
        ) // (from details): 5.72727272 + 4,732 → 5.229636363636364
    }

    @Test
    fun `force calc full year average with different average from all grades and from two semesters`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)

        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(22, "Fizyka", 5.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 5.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 22, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(23, "Fizyka", 5.0, weight = 1.0),
                    getGrade(23, "Fizyka", 5.0, weight = 2.0),
                    getGrade(23, "Fizyka", 4.0, modifier = 0.3, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 23, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            5.5429,
            items.single { it.subject == "Fizyka" }.average,
            .0001
        ) // (from details): 5.72727272 + 4,732 → .average()
    }

    @Test
    fun `force calc both semesters average with different average from all grades and from two semesters with custom modifiers`() {
        val student = student.copy(loginMode = Sdk.Mode.SCRAPPER.name)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33)
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.5)

        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        coEvery { semesterRepository.getSemesters(student) } returns semesters

        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(22, "Fizyka", 5.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 5.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 22, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(23, "Fizyka", 5.0, weight = 1.0),
                    getGrade(23, "Fizyka", 5.0, weight = 2.0),
                    getGrade(23, "Fizyka", 4.0, modifier = 0.33, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 23, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            5.2636,
            items.single { it.subject == "Fizyka" }.average,
            .0001
        ) // (from details): 5.72727272 + 4,8 → 5.26363636
    }

    @Test
    fun `force calc full year average with different average from all grades and from two semesters with custom modifiers`() {
        val student = student.copy(loginMode = Sdk.Mode.SCRAPPER.name)

        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(true)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)
        every { preferencesRepository.gradeMinusModifierFlow } returns flowOf(.33)
        every { preferencesRepository.gradePlusModifierFlow } returns flowOf(.5)

        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.ALL_YEAR)
        coEvery { semesterRepository.getSemesters(student) } returns semesters

        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[1],
                true
            )
        } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(22, "Fizyka", 5.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 5.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 4.0),
                    getGrade(22, "Fizyka", 6.0, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 22, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }
        coEvery {
            gradeRepository.getGrades(
                student,
                semesters[2],
                true
            )
        } returns resourceFlow {
            Triple(
                listOf(
                    getGrade(23, "Fizyka", 5.0, weight = 1.0),
                    getGrade(23, "Fizyka", 5.0, weight = 2.0),
                    getGrade(23, "Fizyka", 4.0, modifier = 0.33, weight = 2.0)
                ),
                listOf(getSummary(semesterId = 23, subject = "Fizyka", average = .0)),
                emptyList()
            )
        }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student,
                semesters[2].semesterId,
                true
            ).getResult()
        }

        assertEquals(
            5.5555,
            items.single { it.subject == "Fizyka" }.average,
            .0001
        ) // (from details): 5.72727272  + 4,8 → .average()
    }

    @Test
    fun `calc both semesters average when both summary have same average from vulcan and second semester has no grades`() {
        every { preferencesRepository.gradeAverageForceCalcFlow } returns flowOf(false)
        every { preferencesRepository.gradeAverageModeFlow } returns flowOf(GradeAverageMode.BOTH_SEMESTERS)
        every { preferencesRepository.isOptionalArithmeticAverageFlow } returns flowOf(false)

        coEvery { gradeRepository.getGrades(student, semesters[1], true) } returns
            resourceFlow { Triple(firstGrades, firstSummaries, emptyList()) }
        coEvery { gradeRepository.getGrades(student, semesters[2], true) } returns
            resourceFlow { Triple(listOf<Grade>(), firstSummaries, emptyList()) }

        val items = runBlocking {
            gradeAverageProvider.getGradesDetailsWithAverage(
                student = student,
                semesterId = semesters[2].semesterId,
                forceRefresh = true,
            ).getResult()
        }

        assertEquals(3.1, items.single { it.subject == "Fizyka" }.average, .0001)
    }

    private fun getGrade(
        semesterId: Int,
        subject: String,
        value: Double,
        modifier: Double = 0.0,
        weight: Double = 1.0,
        entry: String = ""
    ): Grade {
        return Grade(
            studentId = 101,
            semesterId = semesterId,
            subject = subject,
            value = value,
            modifier = modifier,
            weightValue = weight,
            teacher = "",
            date = now(),
            weight = "",
            gradeSymbol = "",
            entry = entry,
            description = "",
            comment = "",
            color = ""
        )
    }

    private fun getSummary(semesterId: Int, subject: String, average: Double): GradeSummary {
        return GradeSummary(
            studentId = 101,
            semesterId = semesterId,
            subject = subject,
            average = average,
            pointsSum = "",
            proposedPoints = "",
            finalPoints = "",
            finalGrade = "",
            predictedGrade = "",
            position = 0,
            pointsSumAllYear = null,
            averageAllYear = null,
        )
    }
}
