package io.github.freewulkanowy.data

import android.content.Context
import android.os.Build
import androidx.javascriptengine.JavaScriptSandbox
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.freewulkanowy.data.db.dao.StudentDao
import io.github.freewulkanowy.data.db.entities.Semester
import io.github.freewulkanowy.data.db.entities.Student
import io.github.freewulkanowy.data.db.entities.StudentIsEduOne
import io.github.freewulkanowy.data.repositories.WulkanowyRepository
import io.github.freewulkanowy.sdk.Sdk
import io.github.freewulkanowy.sdk.scrapper.EvaluateHandler
import io.github.freewulkanowy.utils.RemoteConfigHelper
import io.github.freewulkanowy.utils.WebkitCookieManagerProxy
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WulkanowySdkFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chuckerInterceptor: ChuckerInterceptor,
    private val remoteConfig: RemoteConfigHelper,
    private val webkitCookieManagerProxy: WebkitCookieManagerProxy,
    private val studentDb: StudentDao,
    private val wulkanowyRepository: WulkanowyRepository,
) {

    private val eduOneMutex = Mutex()
    private val migrationFailedStudentIds = mutableSetOf<Long>()
    private val sandbox: ListenableFuture<JavaScriptSandbox>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && JavaScriptSandbox.isSupported())
            runCatching { JavaScriptSandbox.createConnectedInstanceAsync(context) }
                .onFailure { Timber.e(it) }
                .getOrNull()
        else null

    private val sdk = Sdk().apply {
        androidVersion = Build.VERSION.RELEASE
        buildTag = Build.MODEL
        userAgentTemplate = remoteConfig.userAgentTemplate
        setSimpleHttpLogger { Timber.d(it) }
        setAdditionalCookieManager(webkitCookieManagerProxy)

        // for debug only
        addInterceptor(chuckerInterceptor, network = true)
    }

    fun createBase() = sdk

    suspend fun create(): Sdk {
        val mapping = wulkanowyRepository.getMapping()

        return createBase().apply {
            if (mapping != null) {
                endpointsMapping = mapping.endpoints
                vTokenMapping = mapping.vTokens
                vHeaders = mapping.vHeaders
                responseMapping = mapping.responseMap
                vParamsEvaluation = createIsolate()
            }
        }
    }

    private suspend fun createIsolate(): suspend () -> EvaluateHandler {
        return {
            val isolate = sandbox?.await()?.createIsolate()
            object : EvaluateHandler {
                override suspend fun evaluate(code: String): String? {
                    return isolate?.evaluateJavaScriptAsync(code)?.await()
                }

                override fun close() {
                    isolate?.close()
                }
            }
        }
    }

    suspend fun create(student: Student, semester: Semester? = null): Sdk {
        val overrideIsEduOne = checkEduOneAndMigrateIfNecessary(student)
        return buildSdk(student, semester, overrideIsEduOne)
    }

    private suspend fun buildSdk(
        student: Student,
        semester: Semester?,
        isStudentEduOne: Boolean
    ): Sdk {
        return create().apply {
            email = student.email
            password = student.password
            symbol = student.symbol
            schoolSymbol = student.schoolSymbol
            studentId = student.studentId
            classId = student.classId
            emptyCookieJarInterceptor = true
            isEduOne = isStudentEduOne

//            if (Sdk.Mode.valueOf(student.loginMode) == Sdk.Mode.HEBE) {
//                mobileBaseUrl = student.mobileBaseUrl
//            } else {
            scrapperBaseUrl = student.scrapperBaseUrl
            domainSuffix = student.scrapperDomainSuffix
            loginType = Sdk.ScrapperLoginType.valueOf(student.loginType)
//            }

//            mode = Sdk.Mode.valueOf(student.loginMode)
            mode = Sdk.Mode.HYBRID
//            mode = Sdk.Mode.SCRAPPER
            mobileBaseUrl = student.mobileBaseUrl
            keyId = student.certificateKey
            privatePem = student.privateKey

            if (semester != null) {
                diaryId = semester.diaryId
                kindergartenDiaryId = semester.kindergartenDiaryId
                schoolYear = semester.schoolYear
                unitId = semester.unitId
            }
        }
    }

    private suspend fun checkEduOneAndMigrateIfNecessary(student: Student): Boolean {
        if (student.isEduOne != null) return student.isEduOne

        if (student.id in migrationFailedStudentIds) {
            Timber.i("Migration eduOne: skipping because of previous failure")
            return false
        }

        eduOneMutex.withLock {
            if (student.id in migrationFailedStudentIds) {
                Timber.i("Migration eduOne: skipping because of previous failure")
                return false
            }

            val studentFromDatabase = studentDb.loadById(student.id)
            if (studentFromDatabase?.isEduOne != null) {
                Timber.i("Migration eduOne: already done")
                return studentFromDatabase.isEduOne
            }

            Timber.i("Migration eduOne: flag missing. Running migration...")
            val initializedSdk = buildSdk(
                student = student,
                semester = null,
                isStudentEduOne = false, // doesn't matter
            )
            val newCurrentStudent = runCatching { initializedSdk.getCurrentStudent() }
                .onFailure { Timber.e(it, "Migration eduOne: can't get current student") }
                .getOrNull()

            if (newCurrentStudent == null) {
                Timber.i("Migration eduOne: failed, so skipping")
                migrationFailedStudentIds.add(student.id)
                return false
            }

            Timber.i("Migration eduOne: success. New isEduOne flag: ${newCurrentStudent.isEduOne}")

            val studentIsEduOne = StudentIsEduOne(
                id = student.id,
                isEduOne = newCurrentStudent.isEduOne
            )
            studentDb.update(studentIsEduOne)
            return newCurrentStudent.isEduOne
        }
    }
}
