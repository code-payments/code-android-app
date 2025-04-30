package com.flipcash.services.models

import com.getcode.utils.CodeServerError

sealed class LoginError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidTimestamp : LoginError()
    class Denied : LoginError()
    class Unrecognized : LoginError()
    data class Other(override val cause: Throwable? = null) : LoginError(cause = cause)
}

sealed class RegisterError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidSignature : RegisterError()
    class Denied: RegisterError()
    class Unrecognized : RegisterError()
    data class Other(override val cause: Throwable? = null) : RegisterError(cause = cause)
}

sealed class GetUserFlagsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetUserFlagsError()
    class Denied : GetUserFlagsError()
    data class Other(override val cause: Throwable? = null) : GetUserFlagsError(cause = cause)
}

sealed class PurchaseAckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : PurchaseAckError()
    class Denied : PurchaseAckError()
    class InvalidReceipt: PurchaseAckError()
    class InvalidMetadata: PurchaseAckError()
    data class Other(override val cause: Throwable? = null) : PurchaseAckError(cause = cause)
}

sealed class AddTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidPushToken : AddTokenError()
    class Unrecognized : AddTokenError()
    data class Other(override val cause: Throwable? = null) : AddTokenError(cause = cause)
}

sealed class DeleteTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : DeleteTokenError()
    data class Other(override val cause: Throwable? = null) : DeleteTokenError(cause = cause)
}

sealed class GetActivityFeedMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetActivityFeedMessagesError()
    class Unrecognized : GetActivityFeedMessagesError()
    class NotFound: GetActivityFeedMessagesError()
    data class Other(override val cause: Throwable? = null) : GetActivityFeedMessagesError(cause = cause)
}