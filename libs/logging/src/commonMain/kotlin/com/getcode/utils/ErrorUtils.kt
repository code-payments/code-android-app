package com.getcode.utils

import android.database.SQLException
import com.getcode.libs.logging.BuildConfig
import com.getcode.manager.TopBarManager
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

object ErrorUtils {
    private var isDisplayErrors = false
    private val reporters = mutableListOf<ErrorReporter>()

    fun addReporter(reporter: ErrorReporter) { reporters.add(reporter) }
    fun removeReporter(reporter: ErrorReporter) { reporters.remove(reporter) }

    fun setDisplayErrors(isDisplayErrors: Boolean) {
        ErrorUtils.isDisplayErrors = isDisplayErrors
    }

    private val ignoredErrors = listOf(
        UnknownHostException::class,
        TimeoutException::class,
        TimeoutCancellationException::class,
        CancellationException::class,
        ConnectException::class,
        SSLException::class,
        SocketException::class,
    )

    fun handleError(throwable: Throwable) {
        if (throwable is CancellationException) return
        if (isNetworkError(throwable)) return
        if (isGmsTransientError(throwable)) return

        val throwableCause: Throwable =
            if (throwable.cause != null && throwable is CodeServerError)
                throwable.cause ?: throwable
            else throwable

        if (isIgnoredGrpcStatus(throwableCause)) return

        Timber.e(throwable)

        if (isRuntimeError(throwable)) return

        if ((isDisplayErrors && !isSuppressibleError(throwable))) {

            TopBarManager.showMessage(
                "[Error] ${throwableCause.javaClass.simpleName}",
                "${throwableCause.message}",
                TopBarManager.TopBarMessageType.NEUTRAL
            )
        }

        if (BuildConfig.NOTIFY_ERRORS && shouldReport(throwable, throwableCause)) {
            val isNotifiable = when {
                throwable is ConditionallyNotifiable -> throwable.isNotifiable
                throwableCause is ConditionallyNotifiable -> throwableCause.isNotifiable
                else -> throwableCause !is CodeServerError
            }

            reporters.forEach { it.report(throwable, throwableCause, isNotifiable) }
        }
    }

    /**
     * Whether [throwable] is worth handing to the reporters at all. [UnreportedError] marks an
     * expected server result the caller already handles, so it stays in the log without opening a
     * Bugsnag error group.
     */
    internal fun shouldReport(throwable: Throwable, cause: Throwable): Boolean =
        throwable !is UnreportedError &&
                cause !is UnreportedError &&
                ignoredErrors.none { it.isInstance(throwable) } &&
                ignoredErrors.none { it.isInstance(cause) }

    private fun isNetworkError(throwable: Throwable): Boolean =
        throwable is TimeoutException ||
                throwable.cause is TimeoutException ||
                throwable is UnknownHostException ||
                throwable.cause is UnknownHostException ||
                throwable is SSLException ||
                throwable.cause is SSLException ||
                throwable is SocketException ||
                throwable.cause is SocketException

    /**
     * Error strings Google Play Services returns for a failed FCM registration. They describe the
     * device's GMS state or the FCM backend, not app code, so they stay out of Bugsnag. Firebase
     * wraps them as `IOException("FCM Registration failed!")` -> `ExecutionException` ->
     * `IOException(<code>)`, which is why the cause chain is walked.
     */
    private val gmsTransientMessages = setOf(
        "SERVICE_NOT_AVAILABLE",
        "FIS_AUTH_ERROR",
        "MISSING_INSTANCEID_SERVICE",
        "TOO_MANY_REGISTRATIONS",
        "AUTHENTICATION_FAILED",
        "INTERNAL_SERVER_ERROR",
        "InternalServerError",
        "PHONE_REGISTRATION_ERROR",
    )

    internal fun isGmsTransientError(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }
            .any { it is java.io.IOException && it.message in gmsTransientMessages }

    val ignoredGrpcStatusCodes = setOf(
        // Transport/transient
        Status.Code.UNAVAILABLE,
        Status.Code.CANCELLED,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.UNIMPLEMENTED,
        // Client-error / validation (not bugs)
        Status.Code.NOT_FOUND,
        Status.Code.ALREADY_EXISTS,
        Status.Code.PERMISSION_DENIED,
        Status.Code.UNAUTHENTICATED,
        Status.Code.FAILED_PRECONDITION,
        Status.Code.OUT_OF_RANGE,
        Status.Code.RESOURCE_EXHAUSTED,
    )

    private fun isIgnoredGrpcStatus(throwable: Throwable): Boolean {
        val code = when (throwable) {
            is StatusRuntimeException -> throwable.status.code
            is StatusException -> throwable.status.code
            else -> return false
        }
        return code in ignoredGrpcStatusCodes
    }

    private fun isRuntimeError(throwable: Throwable): Boolean =
        throwable is StatusRuntimeException ||
                throwable is StatusException ||
                throwable.cause is StatusRuntimeException ||
                throwable.cause is StatusException

    private fun isSuppressibleError(throwable: Throwable): Boolean =
        throwable is SQLException || throwable is SuppressibleException || throwable is TimeoutCancellationException
}

fun Throwable.isNetworkError(): Boolean =
    this is UnknownHostException || cause is UnknownHostException ||
    this is ConnectException || cause is ConnectException ||
    this is TimeoutException || cause is TimeoutException ||
    this is SSLException || cause is SSLException ||
    this is SocketException || cause is SocketException

data class SuppressibleException(override val message: String, override val cause: Throwable? = null) : Throwable(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}