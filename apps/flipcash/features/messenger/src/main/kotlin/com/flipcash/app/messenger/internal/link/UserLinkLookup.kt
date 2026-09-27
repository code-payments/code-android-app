package com.flipcash.app.messenger.internal.link

import com.flipcash.app.messenger.internal.joinedLine
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import javax.inject.Inject

/**
 * Reads a person's public profile for a link card.
 *
 * A plain profile fetch, by id or by handle, and nothing else. The tip flow resolves the same
 * links, but it caches the profile, raises the tip bill and navigates, and a card that did any of
 * that would act on a link by scrolling past it.
 *
 * An unclaimed handle does not fail: the server answers with an empty profile. With no id in it
 * there is no account to show, so that is a failure here, the same as a transport error. Either
 * way the card draws its not-found state, and the resolver forgets the answer so a later
 * appearance asks again.
 */
internal class UserLinkLookup @Inject constructor(
    private val profiles: ProfileController,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) {
    suspend operator fun invoke(identity: LinkCard.User.Identity): Result<LinkCard.User.State.Resolved> =
        runCatching {
            val profile = when (identity) {
                is LinkCard.User.Identity.ById -> profiles.getProfileForUser(identity.userId)
                is LinkCard.User.Identity.ByUsername -> profiles.getProfileForUsername(identity.username)
            }.getOrThrow()
            userCardState(
                profile = profile,
                viewerId = userManager.accountId,
                joined = { joinedLine(it, resources) },
            ) ?: throw NoSuchAccount(identity)
        }

    class NoSuchAccount(identity: LinkCard.User.Identity) :
        IllegalStateException("no account behind $identity")
}

/**
 * What a fetched profile says on the card, or null when it names no account.
 *
 * Only the profile's own fields are read: name, handle, join date and picture. The name falls back
 * to the handle, and the handle line is then left out rather than saying the same thing twice.
 */
internal fun userCardState(
    profile: UserProfile,
    viewerId: ID?,
    joined: (kotlin.time.Instant) -> String,
): LinkCard.User.State.Resolved? {
    val userId = profile.userId ?: return null
    val displayName = profile.displayName.takeIf { it.isNotBlank() }
    return LinkCard.User.State.Resolved(
        userId = userId,
        isOwn = userId == viewerId,
        profile = profile,
        name = nameOrHandle(displayName, profile.handle),
        handle = profile.handle.takeIf { displayName != null },
        joined = profile.joinedAt?.let(joined),
    )
}
