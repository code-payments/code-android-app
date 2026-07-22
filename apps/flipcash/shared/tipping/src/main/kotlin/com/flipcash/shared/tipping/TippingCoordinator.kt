package com.flipcash.shared.tipping

import com.flipcash.app.core.bill.Scannable
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.core.UserId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates tipping flows.
 *
 * Resolves the [UserProfile] needed to render a tip card and assembles the scannable
 * [Scannable.TipCard] itself — mirroring how the cash screen builds a [Scannable.Payable]
 * bill to present. The tip code encodes the recipient's [ID] (a 16-byte user id).
 */
@Singleton
class TippingCoordinator @Inject constructor(
    private val profileController: ProfileController,
    private val userManager: UserManager,
    private val resolverController: ResolverController,
) {
    /** The signed-in user's id ([UserManager.accountId]), or null if unavailable. */
    val currentUserId: ID?
        get() = userManager.accountId

    /**
     * Resolves the [UserProfile] for [userId] — e.g. a tip counterparty identified by a
     * scanned code — so a tip card can be rendered for them. Delegates to the server-backed
     * profile fetch and propagates its [Result].
     */
    suspend fun resolveProfile(userId: ID): Result<UserProfile> =
        profileController.getProfileForUser(userId)

    /**
     * The current user's profile, used to build their own tip card. Prefers the cached
     * [UserManager.profile] and falls back to a server refresh when it isn't available yet.
     */
    suspend fun currentUserProfile(): Result<UserProfile> =
        userManager.profile?.let { Result.success(it) }
            ?: profileController.updateUserProfile()

    /**
     * Builds the current user's own scannable tip card — their [currentUserId] encoded into the
     * tip code, plus their profile for rendering — the way the cash screen builds a bill to
     * present. Fails if there's no signed-in user or their profile can't be resolved.
     */
    suspend fun resolveTipCard(): Result<Scannable.TipCard> {
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException("No signed-in user to build a tip card for"))
        return currentUserProfile().map { tipCard(userId, it) }
    }

    /**
     * Resolves [userId]'s profile and builds their scannable tip card — for generating a card
     * for another user (e.g. a scanned counterparty).
     */
    suspend fun resolveTipCard(userId: ID): Result<Scannable.TipCard> =
        resolveProfile(userId).map { tipCard(userId, it) }

    /**
     * Assembles the scannable [Scannable.TipCard]: the tip [OpenCodePayload] encoding [userId]
     * as the scannable code data, plus [profile] for rendering.
     */
    private fun tipCard(userId: ID, profile: UserProfile): Scannable.TipCard {
        val payload = OpenCodePayload(kind = PayloadKind.Tip, value = UserId(userId))
        return Scannable.TipCard(data = payload.codeData.toList(), user = profile)
    }
}
