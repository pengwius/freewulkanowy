package io.github.freewulkanowy.ui.modules.login.recover

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.freewulkanowy.sdk.scrapper.exception.InvalidCaptchaException
import io.github.freewulkanowy.sdk.scrapper.exception.InvalidEmailException
import io.github.freewulkanowy.sdk.scrapper.exception.NoAccountFoundException
import io.github.freewulkanowy.ui.base.ErrorHandler
import javax.inject.Inject

class RecoverErrorHandler @Inject constructor(
    @ApplicationContext context: Context,
) : ErrorHandler(context) {

    var onInvalidUsername: (String) -> Unit = {}

    var onInvalidCaptcha: (String, Throwable) -> Unit = { _, _ -> }

    override fun proceed(error: Throwable) {
        when (error) {
            is InvalidEmailException,
            is NoAccountFoundException -> onInvalidUsername(error.localizedMessage.orEmpty())
            is InvalidCaptchaException -> onInvalidCaptcha(error.localizedMessage.orEmpty(), error)
            else -> super.proceed(error)
        }
    }

    override fun clear() {
        super.clear()
        onInvalidUsername = {}
    }
}
