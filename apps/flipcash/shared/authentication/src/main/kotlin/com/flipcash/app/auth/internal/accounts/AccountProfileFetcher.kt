package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

/**
 * Fetches a stored account's username and display name, signed with that account's own key the
 * same way the account list signs its balance lookups.
 *
 * `GetProfile` is keyed by user id. The fetch uses the user id [AccountProfileCache] kept from when
 * the account was signed in. Only an account with no stored user id goes through the Login RPC,
 * the one call that maps an owner key to its user id; the id it returns is cached so that account
 * never needs Login again on this device.
 *
 * Every answer is written to [AccountProfileCache] so the list can still name the account offline.
 */
class AccountProfileFetcher @Inject constructor(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val cache: AccountProfileCache,
) {
    /**
     * The account's current names. An account the server has no profile for succeeds with both
     * names null — it has none — rather than failing, so a stale cached name does not outlive it.
     */
    suspend fun fetch(owner: KeyPair, ownerAddress: String, userId: ID?): Result<AccountProfileName> =
        resolveUserId(owner, ownerAddress, userId)
            .mapCatching { id ->
                profileRepository.getProfile(ProfileIdentifier.UserId(id), owner)
                    .map { profile ->
                        AccountProfileName(
                            username = profile.username?.takeIf { it.isNotBlank() },
                            displayName = profile.displayName.takeIf { it.isNotBlank() },
                        )
                    }
                    .recover { error ->
                        if (error !is GetUserProfileError.NotFound) throw error
                        AccountProfileName(username = null, displayName = null)
                    }
                    .getOrThrow()
            }
            .onSuccess { name -> cache.put(ownerAddress, name) }

    private suspend fun resolveUserId(owner: KeyPair, ownerAddress: String, userId: ID?): Result<ID> =
        if (userId != null) {
            Result.success(userId)
        } else {
            accountRepository.login(owner).onSuccess { id -> cache.putUserId(ownerAddress, id) }
        }
}
