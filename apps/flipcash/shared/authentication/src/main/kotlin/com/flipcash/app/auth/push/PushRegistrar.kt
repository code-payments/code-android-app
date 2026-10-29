package com.flipcash.app.auth.push

import com.flipcash.app.push.PushTokenProvider
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.AddTokenError
import com.flipcash.services.user.UserManager
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one path that sends the push token to Flipcash. Every call records its outcome in
 * [PushRegistrationStore], so login, token refresh and the notification status page all leave
 * the same trail.
 */
@Singleton
class PushRegistrar internal constructor(
    private val pushController: PushController,
    private val pushTokenProvider: PushTokenProvider,
    private val userManager: UserManager,
    private val store: PushRegistrationStore,
    private val clock: Clock,
) {
    @Inject
    constructor(
        pushController: PushController,
        pushTokenProvider: PushTokenProvider,
        userManager: UserManager,
        store: PushRegistrationStore,
    ) : this(pushController, pushTokenProvider, userManager, store, Clock.System)

    /**
     * @param token the token to send; fetched from [PushTokenProvider] when null.
     * @param resetToken delete the local token first, so a rejected token is replaced.
     * @param timeout give up after this long and record a transport failure. It covers the token
     *   delete, the token fetch and `AddToken`.
     */
    suspend fun register(
        source: PushRegistrationSource,
        token: String? = null,
        resetToken: Boolean = false,
        timeout: Duration = Timeout,
    ): PushRegistrationAttempt {
        val previous = store.read()

        // Kept outside the attempt so a timeout still reports the token it sent.
        var sent: String? = null

        suspend fun attempt(): PushRegistrationOutcome {
            if (resetToken) pushTokenProvider.deleteToken()
            val pushToken = token ?: pushTokenProvider.getToken()
                ?: return PushRegistrationOutcome.NoToken
            sent = pushToken
            return pushController.addToken(pushToken).fold(
                onSuccess = {
                    userManager.set(pushToken = pushToken)
                    PushRegistrationOutcome.Ok
                },
                onFailure = ::classify,
            )
        }

        val outcome = withTimeoutOrNull(timeout) { attempt() } ?: PushRegistrationOutcome.Transport

        if (outcome == PushRegistrationOutcome.Ok) {
            trace("push token updated ($source)", type = TraceType.Silent)
        } else {
            trace("push token registration failed ($source): $outcome")
        }

        // A timeout during the token delete or fetch never reached AddToken, so there is nothing to save.
        outcome.toResult()?.takeIf { sent != null }?.let { result ->
            val now = clock.now()
            store.save(
                PushRegistration(
                    at = now,
                    result = result,
                    source = source,
                    lastOkAt = if (result == PushRegistrationResult.Ok) now else previous?.lastOkAt,
                ),
            )
        }

        return PushRegistrationAttempt(
            outcome = outcome,
            previous = previous,
            tokenSuffix = sent?.takeLast(6),
        )
    }

    /** The last saved result from any call site. */
    suspend fun lastSaved(): PushRegistration? = store.read()

    suspend fun clear() = store.clear()

    companion object {
        /** One limit for every call site: login, token refresh and the status page. */
        val Timeout = 10.seconds
    }

    private fun classify(error: Throwable): PushRegistrationOutcome = when {
        error is AddTokenError.InvalidPushToken -> PushRegistrationOutcome.InvalidPushToken
        error is AddTokenError.Other && error.isTransportFailure -> PushRegistrationOutcome.Transport
        else -> PushRegistrationOutcome.Failed
    }

    // NoToken never reached AddToken, so there is no server outcome to record.
    private fun PushRegistrationOutcome.toResult(): PushRegistrationResult? = when (this) {
        PushRegistrationOutcome.Ok -> PushRegistrationResult.Ok
        PushRegistrationOutcome.NoToken -> null
        PushRegistrationOutcome.InvalidPushToken -> PushRegistrationResult.InvalidPushToken
        PushRegistrationOutcome.Transport -> PushRegistrationResult.Transport
        PushRegistrationOutcome.Failed -> PushRegistrationResult.Unknown
    }
}
