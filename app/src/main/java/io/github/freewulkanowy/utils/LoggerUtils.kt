package io.github.freewulkanowy.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import fr.bipi.treessence.common.filters.Filter
import io.github.freewulkanowy.sdk.exception.FeatureNotAvailableException
import io.github.freewulkanowy.sdk.scrapper.exception.FeatureDisabledException
import timber.log.Timber
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

class DebugLogTree : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, "Wulkanowy", message, t)
    }
}

object ExceptionFilter : Filter {

    override fun isLoggable(priority: Int, tag: String?) = true

    override fun skipLog(priority: Int, tag: String?, message: String, t: Throwable?) =
        when (t) {
            is FeatureDisabledException,
            is FeatureNotAvailableException,
            is UnknownHostException,
            is SocketTimeoutException,
            is InterruptedIOException -> true
            else -> false
        }
}

class ActivityLifecycleLogger : Application.ActivityLifecycleCallbacks {

    override fun onActivityPaused(activity: Activity) {
        Timber.d("${activity::class.java.simpleName} PAUSED")
    }

    override fun onActivityResumed(activity: Activity) {
        Timber.d("${activity::class.java.simpleName} RESUMED")
    }

    override fun onActivityStarted(activity: Activity) {
        Timber.d("${activity::class.java.simpleName} STARTED")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Timber.d("${activity::class.java.simpleName} DESTROYED")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Timber.d("${activity::class.java.simpleName} SAVED INSTANCE STATE")
    }

    override fun onActivityStopped(activity: Activity) {
        Timber.d("${activity::class.java.simpleName} STOPPED")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Timber.d("${activity::class.java.simpleName} CREATED ${savedInstanceState.checkSavedState()}")
    }
}

@Singleton
class FragmentLifecycleLogger @Inject constructor() :
    FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?
    ) {
        Timber.d("${f::class.java.simpleName} VIEW CREATED ${savedInstanceState.checkSavedState()}")
    }

    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} STOPPED")
    }

    override fun onFragmentCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?
    ) {
        Timber.d("${f::class.java.simpleName} CREATED ${savedInstanceState.checkSavedState()}")
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} RESUMED")
    }

    override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
        Timber.d("${f::class.java.simpleName} ATTACHED")
    }

    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} DESTROYED")
    }

    override fun onFragmentSaveInstanceState(
        fm: FragmentManager,
        f: Fragment,
        outState: Bundle
    ) {
        Timber.d("${f::class.java.simpleName} SAVED INSTANCE STATE")
    }

    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} STARTED")
    }

    override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} VIEW DESTROYED")
    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} PAUSED")
    }

    override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
        Timber.d("${f::class.java.simpleName} DETACHED")
    }
}

private fun Bundle?.checkSavedState() =
    if (this == null) "(STATE IS NULL)" else "(RESTORE STATE)"

