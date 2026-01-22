package com.flipcash.app.onramp

sealed class DeeplinkOnRampError(
    open val code: Long = -99,
    override val message: String?,
    override val cause: Throwable? = null,
) : Throwable(message) {

    class FailedToGenerateDeeplink(
        override val message: String? = null
    ) : DeeplinkOnRampError(message = message)
    class FailedToCreateTransaction(override val message: String?) :
        DeeplinkOnRampError(message = message)

    class FailedToSimulateTransaction(override val message: String?) :
        DeeplinkOnRampError(message = message)

    class FailedToSendTransaction(
        override val code: Long = -99,
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(code = code, message = message, cause = cause)

    class DecryptionError(
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(message = message, cause = cause)

    class DeserializationError(
        override val message: String?,
        override val cause: Throwable? = null
    ) : DeeplinkOnRampError(message = message, cause = cause)

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

