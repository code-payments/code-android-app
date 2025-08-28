package com.flipcash.app.onramp

sealed class PhantomOnRampError(
    open val code: Int = -99,
    override val message: String?,
    override val cause: Throwable? = null,
) : Throwable(message) {

    class FailedToGenerateDeeplink(
        override val message: String? = null
    ) : PhantomOnRampError(message = message)
    class FailedToCreateTransaction(override val message: String?) :
        PhantomOnRampError(message = message)

    class FailedToSendTransaction(
        override val code: Int = -99,
        override val message: String?,
        override val cause: Throwable? = null
    ) : PhantomOnRampError(code = code, message = message, cause = cause)

    class DecryptionError(
        override val message: String?,
        override val cause: Throwable? = null
    ) : PhantomOnRampError(message = message, cause = cause)

    class DeserializationError(
        override val message: String?,
        override val cause: Throwable? = null
    ) : PhantomOnRampError(message = message, cause = cause)

    class PhantomProvidedError(
        val error: PhantomError,
        override val message: String?,
    ): PhantomOnRampError(code = error.code, message = message)
}

enum class PhantomError(val code: Int) {
    Unknown(-999),
    Disconnected(4900),
    Unauthorized(4100),
    UserRejectedRequest(4001),
    InvalidInput(-32000),
    RequestedResourceNotAvailable(-32002),
    TransactionRejected(-32003),
    MethodNotFound(-32601),
    InternalError(-32603);

    companion object {
        fun fromCode(code: Int?) = entries.firstOrNull { it.code == code } ?: Unknown
        fun fromCode(code: String?) = fromCode(code?.toIntOrNull())
    }
}

