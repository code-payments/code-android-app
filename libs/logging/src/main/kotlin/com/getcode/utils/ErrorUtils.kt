package com.getcode.utils

import android.database.SQLException
import com.bugsnag.android.Bugsnag
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.getcode.libs.logging.BuildConfig
import com.getcode.manager.TopBarManager
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object ErrorUtils {
    private var isDisplayErrors = false

    fun setDisplayErrors(isDisplayErrors: Boolean) {
        ErrorUtils.isDisplayErrors = isDisplayErrors
    }

    private val ignoredErrors = listOf(
        UnknownHostException::class,
        TimeoutException::class,
        TimeoutCancellationException::class,
        ConnectException::class,
    )

    fun handleError(throwable: Throwable) {
        if (isNetworkError(throwable)) return

        val throwableCause: Throwable =
            if (throwable.cause != null && (throwable is UndeliverableException || throwable is OnErrorNotImplementedException || throwable is CodeServerError))
                throwable.cause ?: throwable
            else throwable

        if (throwableCause is StatusRuntimeException) {
            when (throwableCause.status.code) {
                Status.Code.UNAVAILABLE -> return
                Status.Code.CANCELLED -> return
                else -> { /* fall through */ }
            }
        }

        Timber.e(throwable)

        if (isRuntimeError(throwable)) return

        if ((isDisplayErrors && !isSuppressibleError(throwable))) {

            TopBarManager.showMessage(
                "[Error] ${throwableCause.javaClass.simpleName}",
                "${throwableCause.message}",
                TopBarManager.TopBarMessageType.NEUTRAL
            )
        }

        if (
            BuildConfig.NOTIFY_ERRORS &&
            ignoredErrors.none { it.isInstance(throwable) } &&
            ignoredErrors.none { it.isInstance(throwableCause) }
        ) {
            FirebaseCrashlytics.getInstance().recordException(throwable)
            if (Bugsnag.isStarted()) {
                Bugsnag.notify(throwable) { event ->
                    val isNotifiable = throwable is NotifiableError
                            || throwableCause is NotifiableError
                            || throwableCause !is CodeServerError
                    if (isNotifiable) {
                        event.addMetadata("alert", "slack_notify", true)
                        event.addMetadata("alert", "error_type", throwableCause.javaClass.simpleName)
                        event.addMetadata("alert", "error_family", throwableCause.javaClass.enclosingClass?.simpleName ?: "Unknown")
                    }
                    true
                }
            }
        }
    }

    fun notifyUnexpected(throwable: Throwable, context: String? = null) {
        if (!BuildConfig.NOTIFY_ERRORS) return
        Timber.e(throwable)
        FirebaseCrashlytics.getInstance().recordException(throwable)
        if (Bugsnag.isStarted()) {
            Bugsnag.notify(throwable) { event ->
                event.addMetadata("alert", "slack_notify", true)
                event.addMetadata("alert", "error_type", throwable.javaClass.simpleName)
                context?.let { event.addMetadata("alert", "context", it) }
                true
            }
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
        throwable is SQLException || throwable is SuppressibleException || throwable is TimeoutCancellationException
}

data class SuppressibleException(override val message: String, override val cause: Throwable? = null) : Throwable(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}