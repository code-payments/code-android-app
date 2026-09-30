package com.flipcash.app.auth.internal.accounts

import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.ProfileIdentifier
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.repository.ProfileRepository
import com.getcode.ed25519.Ed25519.KeyPair
import javax.inject.Inject

/**
 * Fetches any stored account's username and display name, signed as that account.
 *
 * `GetProfile` is keyed by user id, and the only way from an owner key to its user id is `Login`,
 * which the contract describes as a lookup for recovering an account — `Register` is the call that
 * creates one. Both requests are signed with the account's own key, the same self-signed pattern the
 * account list uses for balances, so this works for accounts that are not signed in.
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
    suspend fun fetch(owner: KeyPair, ownerAddress: String): Result<AccountProfileName> =
        accountRepository.login(owner)
            .mapCatching { userId ->
                profileRepository.getProfile(ProfileIdentifier.UserId(userId), owner)
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
}
