package com.getcode.utils

import android.database.SQLException
import com.bugsnag.android.Bugsnag
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.getcode.api.BuildConfig
import com.getcode.manager.TopBarManager
import io.grpc.StatusRuntimeException
import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import timber.log.Timber
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {
    private var isDisplayErrors = false

    fun setDisplayErrors(isDisplayErrors: Boolean) {
        this.isDisplayErrors = isDisplayErrors
    }

    fun handleError(throwable: Throwable) {
        throwable.printStackTrace()
        if (isNetworkError(throwable) || isRuntimeError(throwable)) return

        val throwableCause: Throwable =
            if (throwable.cause != null && (throwable is UndeliverableException || throwable is OnErrorNotImplementedException))
                throwable.cause ?: throwable
            else throwable

        if (BuildConfig.DEBUG || (isDisplayErrors && !isSuppressibleError(throwable))) {
            Timber.e(throwable)

            TopBarManager.showMessage(
                "[Error] ${throwableCause.javaClass.simpleName}",
                "${throwableCause.message}",
                TopBarManager.TopBarMessageType.NEUTRAL
            )
        }

        if (
            BuildConfig.NOTIFY_ERRORS &&
            throwable !is UnknownHostException &&
            throwable !is TimeoutException &&
            throwable !is ConnectException
        ) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
            Bugsnag.notify(throwable)
        }
    }

    private fun isNetworkError(throwable: Throwable): Boolean =
        throwable is TimeoutException ||
                throwable.cause is TimeoutException ||
                throwable is UnknownHostException ||
                throwable.cause is UnknownHostException

    private fun isRuntimeError(throwable: Throwable): Boolean =
        throwable is StatusRuntimeException ||
                throwable.cause is StatusRuntimeException

    private fun isSuppressibleError(throwable: Throwable): Boolean =
        throwable is SQLException || throwable is net.sqlcipher.SQLException || throwable is SuppressibleException
}

data class SuppressibleException(override val message: String): Throwable(message)