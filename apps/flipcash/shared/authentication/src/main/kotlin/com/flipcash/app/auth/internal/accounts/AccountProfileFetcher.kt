package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

/**
 * Fetches a stored account's username and display name by the user id [AccountProfileCache] kept
 * from when that account was signed in.
 *
 * `GetProfile` is a read, signed with the account's own key the same way the account list signs its
 * balance lookups. There is deliberately no path from an owner key to a user id here: that takes the
 * Login RPC, and the app must not sign in as an account the user has not switched to. A caller with
 * no stored user id has no fetch to make.
 *
 * Every answer is written to [AccountProfileCache] so the list can still name the account offline.
 */
class AccountProfileFetcher @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val cache: AccountProfileCache,
) {
    /**
     * The account's current names. An account the server has no profile for succeeds with both
     * names null — it has none — rather than failing, so a stale cached name does not outlive it.
     */
    suspend fun fetch(owner: KeyPair, ownerAddress: String, userId: ID): Result<AccountProfileName> =
        profileRepository.getProfile(ProfileIdentifier.UserId(userId), owner)
            .map { profile ->
                AccountProfileName(
                    username = profile.username?.takeIf { it.isNotBlank() },
                    displayName = profile.displayName.takeIf { it.isNotBlank() },
                )
            }
            .recoverCatching { error ->
                if (error !is GetUserProfileError.NotFound) throw error
                AccountProfileName(username = null, displayName = null)
            }
            .onSuccess { name -> cache.put(ownerAddress, name) }
}
