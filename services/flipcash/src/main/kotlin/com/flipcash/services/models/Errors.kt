package com.flipcash.services.models

import com.getcode.utils.CodeServerError

sealed class LoginError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidTimestamp : LoginError("Invalid timestamp")
    class Denied : LoginError("Denied")
    class Unrecognized : LoginError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : LoginError(cause = cause)
}

sealed class RegisterError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidSignature : RegisterError("Invalid signature")
    class Denied: RegisterError("Denied")
    class Unrecognized : RegisterError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : RegisterError(cause = cause)
}

sealed class GetUserFlagsError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : GetUserFlagsError("Unrecognized")
    class Denied : GetUserFlagsError("Denied")
    data class Other(override val cause: Throwable? = null) : GetUserFlagsError(cause = cause)
}

sealed class PurchaseAckError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : PurchaseAckError("Unrecognized")
    class Denied : PurchaseAckError("Denied")
    class InvalidReceipt: PurchaseAckError("Invalid receipt")
    class InvalidMetadata: PurchaseAckError("Invalid metadata")
    data class Other(override val cause: Throwable? = null) : PurchaseAckError(cause = cause)
}

sealed class AddTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class InvalidPushToken : AddTokenError("Invalid push token")
    class Unrecognized : AddTokenError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : AddTokenError(cause = cause)
}

sealed class DeleteTokenError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Unrecognized : DeleteTokenError("Unrecognized")
    data class Other(override val cause: Throwable? = null) : DeleteTokenError(cause = cause)
}

sealed class GetActivityFeedMessagesError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    class Denied : GetActivityFeedMessagesError("Denied")
    class Unrecognized : GetActivityFeedMessagesError("Unrecognized")
    class NotFound: GetActivityFeedMessagesError("Not found")
    data class Other(override val cause: Throwable? = null) : GetActivityFeedMessagesError(cause = cause)
}