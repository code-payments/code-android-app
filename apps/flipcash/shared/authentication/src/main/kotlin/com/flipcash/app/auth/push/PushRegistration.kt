package com.flipcash.app.auth.push

import kotlin.time.Instant

/**
 * The last `AddToken` outcome for the signed-in account. Written by every call site (login, token
 * refresh, the notification status page) so the status page and its report can say when push
 * registration last worked. Never holds the token itself.
 */
data class PushRegistration(
    val at: Instant,
    val result: PushRegistrationResult,
    val source: PushRegistrationSource,
)

enum class PushRegistrationResult(val key: String) {
    Ok("ok"),
    InvalidPushToken("invalidPushToken"),
    Transport("transport"),
    Unknown("unknown"),
}

enum class PushRegistrationSource(val key: String) {
    Login("login"),
    Refresh("refresh"),
    StatusPage("statusPage"),
}

/** What one registration attempt found, before it is reduced to a saved [PushRegistrationResult]. */
enum class PushRegistrationOutcome {
    Ok,

    /** The token provider returned no token, so `AddToken` was never called. */
    NoToken,
    InvalidPushToken,

    /** No answer from the server: offline, unreachable, or the attempt timed out. */
    Transport,
    Failed,
}

data class PushRegistrationAttempt(
    val outcome: PushRegistrationOutcome,
    /** The record saved before this attempt, if any. */
    val previous: PushRegistration?,
    /** Last six characters of the token sent, for the support report. */
    val tokenSuffix: String?,
)

interface PushRegistrationStore {
    suspend fun read(): PushRegistration?
    suspend fun save(registration: PushRegistration)
    suspend fun clear()
}
