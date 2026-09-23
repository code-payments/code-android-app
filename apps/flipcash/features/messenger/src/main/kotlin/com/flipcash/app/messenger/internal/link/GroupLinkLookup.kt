package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.tokens.isReserve
import com.flipcash.app.messenger.internal.balanceRequirement
import com.flipcash.app.messenger.internal.requiresStaff
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import javax.inject.Inject

/**
 * Reads a group's public record for an invite card.
 *
 * `GetChat` in the redacted view answers for any registered user, member or not, which is what an
 * invite needs: most people reading one are not in the group yet. The card is built from four
 * fields and no others -- title, picture, roster summary, rules. [ChatMetadata.members] is never
 * read here, so no member name or avatar can reach the card, including as a fallback title.
 *
 * Anything that is not a group -- a DM id pasted into a `/chat/` link, or a chat that has gone --
 * fails the lookup, and the card draws its unavailable state.
 *
 * The requirement is stated the way the chat's own head card states it, including the reserve
 * being named by the amount alone ("$100", not "$100 of Dollars"). An unknown mint is not a
 * failure: the amount is still true, so it is stated without a token beside it.
 */
internal class GroupLinkLookup @Inject constructor(
    private val chatController: ChatController,
    private val tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
) {
    suspend operator fun invoke(chatId: ChatId): Result<LinkCard.GroupInvite.State.Resolved> =
        runCatching {
            val chat = chatController.getChat(chatId, ViewMode.REDACTED).getOrThrow()
            require(chat.type == ChatType.GROUP) { "chat $chatId is not a group" }

            val balance = chat.rules.balanceRequirement()
            val staffOnly = chat.rules.requiresStaff()
            val requirement = if (balance != null || staffOnly) {
                val token = balance?.mints?.firstOrNull()?.let { mint ->
                    tokenCoordinator.getTokenMetadata(Mint(mint.bytes)).getOrNull()?.token
                }
                LinkCard.GroupInvite.Requirement(
                    amount = balance?.amount?.formatted(),
                    currencyName = token?.takeUnless { it.isReserve }?.brandedName(resources),
                    staffOnly = staffOnly,
                )
            } else {
                null
            }

            LinkCard.GroupInvite.State.Resolved(
                title = chat.title?.takeIf { it.isNotBlank() },
                picture = chat.picture,
                memberCount = chat.rosterSummary.memberCount,
                requirement = requirement,
            )
        }
}
