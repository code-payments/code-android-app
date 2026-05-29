package com.flipcash.app.onramp

import com.flipcash.shared.onramp.deeplinks.R
import com.getcode.utils.isNetworkError

sealed class DeeplinkOnRampError(
    open val code: Long = -99,
    override val message: String?,
    override val cause: Throwable? = null,
) : Throwable(message) {

    class FailedToCreateTransaction(
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(message = message, cause = cause)

    class FailedToSimulateTransaction(
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(message = message, cause = cause)

    class FailedToSendTransaction(
        override val code: Long = -99,
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(code = code, message = message, cause = cause)

    class TransactionExpired(
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(message = message, cause = cause)

    class FailedToSubmitBuyToServer(
        override val code: Long = -100,
        override val message: String?,
        override val cause: Throwable? = null
    ): DeeplinkOnRampError(message = message, cause = cause)

    class InsufficientSol(
        val requiredLamports: Long,
        val actualLamports: Long,
    ) : DeeplinkOnRampError(message = "Insufficient SOL: required $requiredLamports lamports, have $actualLamports")

    class InsufficientUsdc(
        val requiredQuarks: Long,
        val actualQuarks: Long,
    ) : DeeplinkOnRampError(message = "Insufficient USDC: required $requiredQuarks quarks, have $actualQuarks")

    class WalletProvidedError(
        val error: DeeplinkError,
        override val message: String?,
    ): DeeplinkOnRampError(code = error.code, message = message)
}

enum class DeeplinkError(val code: Long) {
    Unknown(-999),
    Disconnected(4900),
    Unauthorized(4100),
    UserRejectedRequest(4001),
    InvalidInput(-32000),
    RequestedResourceNotAvailable(-32002),
    TransactionRejected(-32003),
    MethodNotFound(-32601),
    InternalError(-32603);

    companion object Companion {
        fun fromCode(code: Long?) = entries.firstOrNull { it.code == code } ?: Unknown
        fun fromCode(code: String?) = fromCode(code?.toLongOrNull())
    }
}

val DeeplinkOnRampError.isAlert: Boolean
    get() = this is DeeplinkOnRampError.WalletProvidedError && error in listOf(
        DeeplinkError.UserRejectedRequest,
        DeeplinkError.Disconnected,
        DeeplinkError.TransactionRejected,
    ) || this is DeeplinkOnRampError.FailedToSendTransaction
        || this is DeeplinkOnRampError.TransactionExpired
        || this is DeeplinkOnRampError.InsufficientSol
        || this is DeeplinkOnRampError.InsufficientUsdc
        || (this is DeeplinkOnRampError.FailedToSimulateTransaction && cause?.isNetworkError() == true)
        || (this is DeeplinkOnRampError.FailedToCreateTransaction && cause?.isNetworkError() == true)

val DeeplinkOnRampError.isNetworkCause: Boolean
    get() = (this is DeeplinkOnRampError.FailedToSimulateTransaction || this is DeeplinkOnRampError.FailedToCreateTransaction)
        && cause?.isNetworkError() == true

fun DeeplinkOnRampError.messaging(getString: (Int) -> String, provider: String): Pair<String, String> = when (this) {
    is DeeplinkOnRampError.FailedToCreateTransaction -> getString(R.string.error_title_deeplinkOnRampFailedToCreateTransaction) to getString(R.string.error_description_deeplinkOnRampFailedToCreateTransaction)
    is DeeplinkOnRampError.FailedToSimulateTransaction -> getString(R.string.error_title_deeplinkOnRampFailedToSimulateTransaction) to getString(R.string.error_description_deeplinkOnRampFailedToSimulateTransaction)
    is DeeplinkOnRampError.FailedToSendTransaction -> getString(R.string.error_title_deeplinkOnRampFailedToSendTransaction) to getString(R.string.error_description_deeplinkOnRampFailedToSendTransaction).format(provider)
    is DeeplinkOnRampError.TransactionExpired -> getString(R.string.error_title_deeplinkOnRampTransactionExpired) to getString(R.string.error_description_deeplinkOnRampTransactionExpired)
    is DeeplinkOnRampError.FailedToSubmitBuyToServer -> getString(R.string.error_title_deeplinkOnRampExternalFundBuy) to getString(R.string.error_description_deeplinkOnRampExternalFundBuy).format(provider)
    is DeeplinkOnRampError.InsufficientSol -> getString(R.string.error_title_deeplinkOnRampInsufficientSol) to getString(R.string.error_description_deeplinkOnRampInsufficientSol).format(provider)
    is DeeplinkOnRampError.InsufficientUsdc -> getString(R.string.error_title_deeplinkOnRampInsufficientUsdc) to getString(R.string.error_description_deeplinkOnRampInsufficientUsdc).format(provider)
    is DeeplinkOnRampError.WalletProvidedError -> when (this.error) {
        DeeplinkError.Disconnected -> getString(R.string.error_title_deeplinkOnRampDisconnected) to getString(R.string.error_description_deeplinkOnRampDisconnected).format(provider)
        DeeplinkError.Unauthorized -> getString(R.string.error_title_deeplinkOnRampUnauthorized) to getString(R.string.error_description_deeplinkOnRampUnauthorized)
        DeeplinkError.UserRejectedRequest -> getString(R.string.error_title_deeplinkOnRampUserRejected).format(provider) to getString(R.string.error_description_deeplinkOnRampUserRejected).format(provider)
        DeeplinkError.InvalidInput -> getString(R.string.error_title_deeplinkOnRampInvalidInput) to getString(R.string.error_description_deeplinkOnRampInvalidInput)
        DeeplinkError.RequestedResourceNotAvailable -> getString(R.string.error_title_deeplinkOnRampRequestedResourceNotAvailable) to getString(R.string.error_description_deeplinkOnRampRequestedResourceNotAvailable).format(provider)
        DeeplinkError.TransactionRejected -> getString(R.string.error_title_deeplinkOnRampTransactionRejected) to getString(R.string.error_description_deeplinkOnRampTransactionRejected).format(provider)
        DeeplinkError.MethodNotFound -> getString(R.string.error_title_deeplinkOnRampMethodNotFound) to getString(R.string.error_description_deeplinkOnRampMethodNotFound).format(provider)
        DeeplinkError.InternalError -> getString(R.string.error_title_deeplinkOnRampInternalError) to getString(R.string.error_description_deeplinkOnRampInternalError).format(provider)
        DeeplinkError.Unknown -> getString(R.string.error_title_deeplinkOnRampUnknown) to getString(R.string.error_description_deeplinkOnRampUnknown)
    }
}
