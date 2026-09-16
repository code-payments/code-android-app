package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves the profiles of message senders the roster does not cover.
 *
 * `ChatMetadata.members` is a subset for a large group, so a transcript bubble can carry a
 * `senderId` no local table has a name or picture for. This fetches that profile once and writes
 * it to `user_profiles` — the table every other avatar in the app already reads — rather than to
 * `chat_members`, because the fact is about the user, not about their membership of this chat.
 * `ChatMemberWithProfile.profile` is already nullable, so nothing needs a migration.
 *
 * [request] never blocks and never returns the answer. A bubble whose fetch is outstanding renders
 * a neutral avatar with no name line; when the write lands, [profiles] re-emits and the bubble
 * fills in. That is why there is no suspend `resolve(userId)` here: the transcript is a Paging
 * stream, and a per-item suspend call would stall page composition on the network.
 *
 * `GetProfile` takes one user id — there is no batch RPC, and no chat RPC that lists members past
 * the page `GetChat` returns — so one call per unknown sender is the floor. What this class owns is
 * making sure it is also the ceiling: the caller re-asks on every emission of the transcript's
 * `combine`, which is once per page load, per pending mutation, and per write to `user_profiles`.
 * Three things keep that from turning into a call per emission:
 *
 * - **A resolved sender stays marked.** Their profile is in `user_profiles`, so the transcript
 *   reads it from [profiles] and never reaches [request] again.
 * - **An unresolvable sender stays marked.** See [unresolvable].
 * - **A sender whose fetch failed for any other reason is held for [RETRY_BACKOFF].** See
 *   [inFlight].
 *
 * Owns its own scope because [com.flipcash.shared.chat.internal.delegates.MessagingDelegate] — its
 * only caller — has no `initialize(scope)` and giving it one would churn `RealChatCoordinator` and
 * every construction of it in tests.
 */
@Singleton
class SenderResolver @Inject constructor(
    private val profileController: ProfileController,
    private val userProfileDataSource: UserProfileDataSource,
    private val dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    /** Guards [inFlight] and [unresolvable], which are read and written together in [request]. */
    private val lock = Any()

    /**
     * User-id hexes with a fetch in flight, or cooling down after a failure. Marked *before* the
     * launch, so two requests arriving in the same frame collapse on the calling thread instead of
     * racing to the network.
     *
     * A transient failure — being offline is the common one — keeps the id marked for
     * [RETRY_BACKOFF] rather than releasing it immediately. Releasing it immediately makes the
     * retry cadence "however often the transcript happens to re-emit", which is neither a policy
     * nor bounded: a scroll while offline is a burst of retries that cannot succeed.
     */
    private val inFlight = mutableSetOf<String>()

    /**
     * User-id hexes the server has no profile for, which are never asked for again.
     *
     * `NOT_FOUND` is an answer, not a failure to get one — the account is gone, or was never
     * visible to this viewer. Treating it as transient is what made a single deleted sender in a
     * group cost a `GetProfile` on every re-emission for the life of the process.
     */
    private val unresolvable = mutableSetOf<String>()

    /**
     * Caps how many profile fetches are on the network at once.
     *
     * Opening a group whose loaded pages carry senders past the roster page asks for all of them in
     * one frame. [inFlight] collapses duplicates but says nothing about the fan-out across distinct
     * ids, and none of these are urgent — a bubble renders without its name either way. The permit
     * is held for the call alone, so a backing-off id occupies nothing.
     */
    private val fetchLimit = Semaphore(MAX_CONCURRENT_FETCHES)

    /** Every profile this device holds, keyed by user-id hex. The transcript indexes into it. */
    val profiles: Flow<Map<String, UserProfile>> = userProfileDataSource.observeProfiles()

    /**
     * Asks for [userId]'s profile if nothing has asked already. Returns immediately.
     */
    fun request(userId: ID) {
        val hex = userId.hexEncodedString()
        synchronized(lock) {
            if (hex in unresolvable) return
            if (!inFlight.add(hex)) return
        }
        scope.launch {
            fetchLimit.withPermit { profileController.getProfileForUser(userId) }
                .onSuccess { profile ->
                    userProfileDataSource.store(userId, profile)
                    // Kept marked on success: the profile is in `user_profiles` now, so the
                    // transcript reads it from [profiles] and never asks again.
                }
                .onFailure { error ->
                    if (error is GetUserProfileError.NotFound) {
                        synchronized(lock) { unresolvable.add(hex) }
                        trace(
                            tag = TAG,
                            message = "no profile exists for sender $hex; will not ask again",
                            type = TraceType.Silent,
                        )
                        return@onFailure
                    }

                    trace(
                        tag = TAG,
                        message = "failed to resolve sender $hex",
                        error = error,
                        type = TraceType.Error,
                    )
                    // Held, not released: the next emission of the transcript is not a signal that
                    // the network came back, and asking again on it would just fail again.
                    delay(RETRY_BACKOFF)
                    synchronized(lock) { inFlight.remove(hex) }
                }
        }
    }

    /**
     * Drops what this process has asked for, so a re-login re-resolves against the new account.
     *
     * Outstanding work is cancelled rather than left to finish. A fetch in flight was authorized by
     * the account that just went away, and a backoff still sleeping would wake to release an id the
     * next account may already be fetching.
     */
    fun clear() {
        synchronized(lock) {
            inFlight.clear()
            unresolvable.clear()
        }
        scope.coroutineContext.cancelChildren()
    }

    private companion object {
        const val TAG = "SenderResolver"

        /**
         * Enough to fill a screen of unfamiliar senders without opening a connection per bubble.
         */
        const val MAX_CONCURRENT_FETCHES = 6

        /** How long a sender whose fetch failed is left alone before anything asks again. */
        val RETRY_BACKOFF: Duration = 30.seconds
    }
}
