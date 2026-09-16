package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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

    /**
     * User-id hexes with a fetch in flight. Marked *before* the launch, so two requests arriving
     * in the same frame collapse on the calling thread instead of racing to the network.
     */
    private val inFlight = mutableSetOf<String>()

    /** Every profile this device holds, keyed by user-id hex. The transcript indexes into it. */
    val profiles: Flow<Map<String, UserProfile>> = userProfileDataSource.observeProfiles()

    /**
     * Asks for [userId]'s profile if nothing has asked already. Returns immediately.
     *
     * A failure releases the id rather than caching the miss: the common cause is being offline,
     * and a permanently poisoned id would leave the sender nameless for the life of the process.
     */
    fun request(userId: ID) {
        val hex = userId.hexEncodedString()
        synchronized(inFlight) {
            if (!inFlight.add(hex)) return
        }
        scope.launch {
            profileController.getProfileForUser(userId)
                .onSuccess { profile ->
                    userProfileDataSource.store(userId, profile)
                    // Kept marked on success: the profile is in `user_profiles` now, so the
                    // transcript reads it from [profiles] and never asks again.
                }
                .onFailure { error ->
                    synchronized(inFlight) { inFlight.remove(hex) }
                    trace(
                        tag = TAG,
                        message = "failed to resolve sender $hex",
                        error = error,
                        type = TraceType.Error,
                    )
                }
        }
    }

    /** Drops what this process has asked for, so a re-login re-resolves against the new account. */
    fun clear() {
        synchronized(inFlight) { inFlight.clear() }
    }

    private companion object {
        const val TAG = "SenderResolver"
    }
}
