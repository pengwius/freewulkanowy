package io.github.freewulkanowy.ui.modules.login

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.freewulkanowy.R
import io.github.freewulkanowy.sdk.hebe.exception.InvalidPinException
import io.github.freewulkanowy.sdk.hebe.exception.InvalidTokenException
import io.github.freewulkanowy.sdk.hebe.exception.TokenDeadException
import io.github.freewulkanowy.sdk.hebe.exception.UnknownTokenException
import io.github.freewulkanowy.sdk.scrapper.login.BadCredentialsException
import io.github.freewulkanowy.ui.base.ErrorHandler
import javax.inject.Inject
import io.github.freewulkanowy.sdk.hebe.exception.InvalidSymbolException as InvalidHebeSymbolException
import io.github.freewulkanowy.sdk.scrapper.login.InvalidSymbolException as InvalidScrapperSymbolException

class LoginErrorHandler @Inject constructor(
    @ApplicationContext context: Context,
) : ErrorHandler(context) {

    var onBadCredentials: (String?) -> Unit = {}

    var onInvalidToken: (String) -> Unit = {}

    var onInvalidPin: (String) -> Unit = {}

    var onInvalidSymbol: (String) -> Unit = {}

    var onStudentDuplicate: (String) -> Unit = {}

    override fun proceed(error: Throwable) {
        val resources = context.resources
        when (error) {
            is BadCredentialsException -> onBadCredentials(error.message)
            is SQLiteConstraintException -> onStudentDuplicate(resources.getString(R.string.login_duplicate_student))
            is TokenDeadException -> onInvalidToken(resources.getString(R.string.login_expired_token))
            is UnknownTokenException,
            is InvalidTokenException -> onInvalidToken(resources.getString(R.string.login_invalid_token))
            is InvalidPinException -> onInvalidPin(resources.getString(R.string.login_invalid_pin))
            is InvalidScrapperSymbolException,
            is InvalidHebeSymbolException -> onInvalidSymbol(resources.getString(R.string.login_invalid_symbol))
            else -> super.proceed(error)
        }
    }

    override fun clear() {
        super.clear()
        onBadCredentials = {}
        onStudentDuplicate = {}
        onInvalidToken = {}
        onInvalidPin = {}
        onInvalidSymbol = {}
    }
}
