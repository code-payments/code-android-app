package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import javax.inject.Inject

/**
 * Reads the profile behind a tip card link. `GetProfile` answers to either way of naming its owner,
 * so the fork the link arrived with survives all the way to the request and no handle has to be
 * turned into an id first.
 *
 * A profile that names nobody is treated as a miss. `UserProfile` requires neither a display name
 * nor a claimed handle — a `TIP_DM` counterparty routinely has only one of them, and an account can
 * have neither — and a card with an empty line where the name goes says less than the URL it
 * replaced. A miss leaves the card unresolved, which still draws, and the link still opens.
 */
internal class TipCardLookup @Inject constructor(
    private val profiles: ProfileController,
) {
    suspend operator fun invoke(owner: TipCardOwner): Result<UserProfile> = runCatching {
        val profile = when (owner) {
            is TipCardOwner.ById -> profiles.getProfileForUser(owner.userId)
            is TipCardOwner.ByUsername -> profiles.getProfileForUsername(owner.username)
        }.getOrThrow()

        requireNotNull(nameOrHandle(profile.displayName, profile.handle)) {
            "nothing to name the owner of $owner by"
        }
        profile
    }
}
