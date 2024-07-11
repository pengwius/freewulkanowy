package io.github.freewulkanowy.services.sync

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.repositories.PreferencesRepository
import io.github.freewulkanowy.data.repositories.SemesterRepository
import io.github.freewulkanowy.data.repositories.StudentRepository
import io.github.freewulkanowy.sdk.exception.FeatureNotAvailableException
import io.github.freewulkanowy.sdk.scrapper.exception.FeatureDisabledException
import io.github.freewulkanowy.sdk.scrapper.exception.FeatureUnavailableException
import io.github.freewulkanowy.services.sync.channels.DebugChannel
import io.github.freewulkanowy.services.sync.works.Work
import io.github.freewulkanowy.utils.DispatchersProvider
import io.github.freewulkanowy.utils.getCompatColor
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import kotlin.random.Random

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val studentRepository: StudentRepository,
    private val semesterRepository: SemesterRepository,
    private val works: Set<@JvmSuppressWildcards Work>,
    private val preferencesRepository: PreferencesRepository,
    private val notificationManager: NotificationManagerCompat,
    private val dispatchersProvider: DispatchersProvider
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = withContext(dispatchersProvider.io) {
        Timber.i("SyncWorker is starting")

        if (!studentRepository.isCurrentStudentSet()) {
            return@withContext Result.failure()
        }

        val (student, semester) = try {
            val student = studentRepository.getCurrentStudent()
            val semester = semesterRepository.getCurrentSemester(student, true)
            student to semester
        } catch (e: Throwable) {
            Timber.e(e)
            return@withContext getResultFromErrors(listOf(e))
        }

        val exceptions = works.mapNotNull { work ->
            try {
                Timber.i("${work::class.java.simpleName} is starting")
                work.doWork(student, semester, isNotificationsEnabled())
                Timber.i("${work::class.java.simpleName} result: Success")
                null
            } catch (e: Throwable) {
                Timber.w("${work::class.java.simpleName} result: An exception ${e.message} occurred")
                if (e is FeatureDisabledException || e is FeatureNotAvailableException || e is FeatureUnavailableException) {
                    null
                } else {
                    Timber.e(e)
                    e
                }
            }
        }
        val result = getResultFromErrors(exceptions)

        if (preferencesRepository.isDebugNotificationEnable) notify(result)
        Timber.i("SyncWorker result: $result")

        return@withContext result
    }

    private fun isNotificationsEnabled(): Boolean {
        val quiet = inputData.getBoolean("quiet", false)
        return preferencesRepository.isNotificationsEnable && !quiet
    }

    private fun getResultFromErrors(errors: List<Throwable>): Result = when {
        errors.isNotEmpty() && inputData.getBoolean("one_time", false) -> {
            Result.failure(
                Data.Builder()
                    .putString("error_message", errors.joinToString { it.message.toString() })
                    .putString("error_stack", errors.map { it.stackTraceToString() }.toString())
                    .build()
            )
        }

        errors.isNotEmpty() -> Result.retry()
        else -> {
            preferencesRepository.lasSyncDate = Instant.now()
            Result.success()
        }
    }

    private fun notify(result: Result) {
        notificationManager.notify(
            Random.nextInt(Int.MAX_VALUE),
            NotificationCompat.Builder(applicationContext, DebugChannel.CHANNEL_ID)
                .setContentTitle("Debug notification")
                .setSmallIcon(R.drawable.ic_stat_all)
                .setAutoCancel(true)
                .setColor(applicationContext.getCompatColor(R.color.colorPrimary))
                .setStyle(BigTextStyle().bigText("${SyncWorker::class.java.simpleName} result: $result"))
                .setPriority(PRIORITY_DEFAULT)
                .build()
        )
    }
}
