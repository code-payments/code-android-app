package com.flipcash.shared.chat.invite

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.toInviteSecretEnvelope
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.GetInviteError
import com.flipcash.services.models.GetRosterError
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteParameters
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.chatcipher.InviteCipher
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import javax.inject.Inject

/** Why a link no longer admits anyone. Each has its own line under the group's description (node 12846:126). */
enum class DeadInviteReason { EXHAUSTED, EXPIRED, REVOKED }

/** What opening an invite link found. */
sealed interface InviteLookup {
    /** The viewer is already in the group, so the link has nothing to offer: open the chat. */
    data class AlreadyMember(val chatId: ChatId) : InviteLookup

    /** The group as the link shows it. [dead] is set when the link no longer admits anyone. */
    data class Found(
        val token: InviteToken,
        val chat: ChatMetadata,
        val dead: DeadInviteReason?,
    ) : InviteLookup

    /** The link matches no invite (node 12846:148). */
    data object NotFound : InviteLookup

    /** The host removed the viewer from this group (node 12846:160). */
    data object Banned : InviteLookup

    /** The lookup itself failed; nothing is known about the link. */
    data object Failed : InviteLookup
}

/** What joining through a link came to. */
sealed interface InviteJoinResult {
    data class Joined(val chatId: ChatId) : InviteJoinResult

    /** The link died between preview and join; the screen switches to the dead state in place. */
    data class Dead(val reason: DeadInviteReason) : InviteJoinResult

    /** INVITE_NOT_FOUND: the link matches no invite. */
    data object NotFound : InviteJoinResult

    /** NOT_FOUND: the link is real but the group is gone. */
    data object GroupGone : InviteJoinResult

    data object Banned : InviteJoinResult

    /**
     * DENIED, or RULES_NOT_SATISFIED. Neither should happen with an invite: the server answers
     * DENIED to a private group joined without one, and a private group has no rules. The screen
     * stays on the preview with the generic alert, not the connection one.
     */
    data object Refused : InviteJoinResult

    /** A transport failure: the connection alert (node 12846:220). */
    data object Failed : InviteJoinResult
}

/** One page of the preview roster, or the news that the link stopped granting it. */
sealed interface InviteRosterResult {
    data class Page(val members: List<ChatMember>, val next: PagingToken?, val memberCount: Long) : InviteRosterResult

    /** `GetRoster` answered DENIED: the invite is no longer valid. Rows already loaded stay. */
    data object Denied : InviteRosterResult

    data object Failed : InviteRosterResult
}

/**
 * The joiner's side of an invite link: derive the token, look the link up, page the preview
 * roster with it, and join. The secret is held for the length of one call and never kept.
 */
class GroupInviteLanding @Inject constructor(
    private val inviteCipher: InviteCipher,
    private val keys: ChatKeySource,
    private val chatController: ChatController,
    private val metadata: ChatMetadataDataSource,
    private val chatCoordinator: ChatCoordinator,
) {
    suspend fun lookup(secret: ByteArray): InviteLookup {
        val token = runCatching { InviteToken(inviteCipher.inviteToken(secret)) }
            .getOrElse { return InviteLookup.NotFound }
        val preview = chatController.getInvite(token).getOrElse { error ->
            return when (error) {
                is GetInviteError.NotFound -> InviteLookup.NotFound
                is GetInviteError.Banned -> InviteLookup.Banned
                else -> InviteLookup.Failed
            }
        }
        if (metadata.isMember(preview.chat.chatId)) return InviteLookup.AlreadyMember(preview.chat.chatId)
        return InviteLookup.Found(token, preview.chat, preview.status.deadReason())
    }

    /** The invite's own token can fetch the roster of a group the viewer is not in. */
    suspend fun roster(chatId: ChatId, token: InviteToken, page: PagingToken?): InviteRosterResult =
        chatController.getRoster(chatId, QueryOptions(token = page, descending = false), token).fold(
            onSuccess = { InviteRosterResult.Page(it.members, it.pagingToken.takeIf { _ -> it.hasMore }, it.rosterSummary.memberCount) },
            onFailure = { if (it is GetRosterError.Denied) InviteRosterResult.Denied else InviteRosterResult.Failed },
        )

    suspend fun join(secret: ByteArray, chatId: ChatId, token: InviteToken): InviteJoinResult {
        val owner = keys.ownKeyPair() ?: return InviteJoinResult.Failed
        val parameters = runCatching {
            val inviteId = inviteCipher.inviteId(secret)
            val sealed = inviteCipher.sealInviteSecretEnvelope(owner, secret, chatId.bytes, inviteId)
            InviteParameters(token, sealed.toInviteSecretEnvelope())
        }.getOrElse { return InviteJoinResult.Failed }

        return chatCoordinator.joinWithInvite(chatId, parameters).fold(
            onSuccess = { InviteJoinResult.Joined(chatId) },
            onFailure = { error ->
                when (error) {
                    is JoinChatError.InviteExhausted -> InviteJoinResult.Dead(DeadInviteReason.EXHAUSTED)
                    is JoinChatError.InviteExpired -> InviteJoinResult.Dead(DeadInviteReason.EXPIRED)
                    is JoinChatError.InviteRevoked -> InviteJoinResult.Dead(DeadInviteReason.REVOKED)
                    is JoinChatError.InviteNotFound -> InviteJoinResult.NotFound
                    is JoinChatError.NotFound -> InviteJoinResult.GroupGone
                    is JoinChatError.Banned -> InviteJoinResult.Banned
                    is JoinChatError.Denied, is JoinChatError.RulesNotSatisfied -> {
                        // Points to a client bug (see InviteJoinResult.Refused). The repository has
                        // already reported the error; this line says it was an invite join. The
                        // chat id is the only identifier here, never the link or its secret.
                        trace(tag = TAG, message = "JoinChat refused an invite join (${error.message}) for $chatId", type = TraceType.Error)
                        InviteJoinResult.Refused
                    }
                    else -> InviteJoinResult.Failed
                }
            },
        )
    }
}

private fun Invite.Status.deadReason(): DeadInviteReason? = when (this) {
    Invite.Status.EXHAUSTED -> DeadInviteReason.EXHAUSTED
    Invite.Status.EXPIRED -> DeadInviteReason.EXPIRED
    Invite.Status.REVOKED -> DeadInviteReason.REVOKED
    Invite.Status.ACTIVE, Invite.Status.UNKNOWN -> null
}

private const val TAG = "GroupInviteLanding"
