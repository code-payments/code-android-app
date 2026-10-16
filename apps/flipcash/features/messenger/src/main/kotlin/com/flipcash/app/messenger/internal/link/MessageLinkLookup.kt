package com.flipcash.app.messenger.internal.link

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.ui.media.photoBody
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads what a message link's card may show, which depends on the source chat and the viewer:
 *
 * - The viewer is in the chat: the message itself, opened on this device if it is end-to-end
 *   encrypted, with its reaction count. ([LinkCard.Message.State.Resolved])
 * - A public group they aren't in: `GetMessage` in `FULL_OR_REDACTED`, which answers with the text
 *   when the group's rules let them read and a placeholder when they don't.
 *   ([LinkCard.Message.State.Gated])
 * - A private group they aren't in: the group's public record only, never the message.
 *   ([LinkCard.Message.State.LockedGroup])
 * - A DM they aren't in: that it is one. ([LinkCard.Message.State.LockedDirect])
 *
 * A deleted message, a message or chat the server doesn't have, or one this device can't open
 * answers [LinkCard.Message.State.Unresolved]; a card never draws resolution failing as an error.
 * A request that didn't complete fails, so the resolver asks again on the next draw.
 *
 * Membership is read from the stored chat. A chat with no stored row, or one whose membership is
 * unknown, is treated as one the viewer is not in: what the card shows is then what the server
 * hands a stranger, so an unknown can never show more than it should.
 */
internal class MessageLinkLookup @Inject constructor(
    private val chats: ChatCoordinator,
    private val chatController: ChatController,
    private val messaging: ChatMessagingController,
    private val profiles: ProfileController,
    private val knownProfiles: UserProfileDataSource,
    private val userManager: UserManager,
    private val groups: GroupLinkLookup,
) {
    suspend operator fun invoke(card: LinkCard.Message): Result<LinkCard.Message.State> = runCatching {
        val stored = chats.observeMetadata(card.chatId).first()
        if (stored?.isMember == true) {
            member(stored.metadata, card.messageId)
        } else if (card.isGroup) {
            nonMemberGroup(card.chatId, card.messageId)
        } else {
            LinkCard.Message.State.LockedDirect
        }
    }.recoverCatching { error ->
        if (error.meansNothingThere()) LinkCard.Message.State.Unresolved else throw error
    }

    private suspend fun member(chat: ChatMetadata, messageId: Long): LinkCard.Message.State {
        val message = chats.readMessage(chat.chatId, messageId).getOrThrow()
        if (message.isDeleted) return LinkCard.Message.State.Deleted(source(chat))
        val body = message.body() ?: return LinkCard.Message.State.Unresolved
        return LinkCard.Message.State.Resolved(
            source = source(chat),
            message = excerpt(message, body, chat, chat.chatId),
            reactionCount = message.reactions?.reactions?.sumOf { it.count } ?: 0,
        )
    }

    private suspend fun nonMemberGroup(chatId: ChatId, messageId: Long): LinkCard.Message.State {
        val chat = chatController.getChat(chatId, ViewMode.REDACTED).getOrThrow()
        if (chat.type != ChatType.GROUP) return LinkCard.Message.State.Unresolved
        if (chat.isPrivate) {
            return LinkCard.Message.State.LockedGroup(
                source = source(chat),
                memberCount = chat.rosterSummary.memberCount,
                host = chat.creator?.let { name(it) },
            )
        }
        val message = messaging.getMessage(chatId, messageId, ViewMode.FULL_OR_REDACTED).getOrThrow()
        if (message.isDeleted) return LinkCard.Message.State.Deleted(source(chat))
        val body = message.body() ?: return LinkCard.Message.State.Unresolved
        return LinkCard.Message.State.Gated(
            source = source(chat),
            message = excerpt(message, body, chat = null, chatId = chatId),
            requirement = groups.requirement(chat.rules),
        )
    }

    private fun source(chat: ChatMetadata): LinkCard.Message.Source = when (chat.type) {
        ChatType.GROUP -> LinkCard.Message.Source(
            title = chat.title?.takeIf { it.isNotBlank() },
            kind = if (chat.isPrivate) LinkCard.Message.Kind.PrivateGroup else LinkCard.Message.Kind.PublicGroup,
            picture = chat.picture,
            pictureAccess = BlobAccessContext.ChatProfile(chat.chatId),
        )
        else -> {
            val peer = chat.members.firstOrNull { it.userId != userManager.accountId }
            LinkCard.Message.Source(
                title = peer?.userProfile?.displayLabel(),
                kind = LinkCard.Message.Kind.DirectMessage,
                picture = peer?.userProfile?.profilePicture,
                pictureAccess = BlobAccessContext.profile(peer?.userId),
            )
        }
    }

    /**
     * The sender, named from the chat's stored roster when [chat] has one and from their public
     * profile otherwise. An unreadable name is left off rather than failing the card.
     */
    private suspend fun excerpt(
        message: ChatMessage,
        body: LinkCard.Message.Body,
        chat: ChatMetadata?,
        chatId: ChatId,
    ): LinkCard.Message.Excerpt {
        val sender = message.senderId
        val isFromSelf = message.isFromSelf || (sender != null && sender == userManager.accountId)
        val author = sender?.let { id ->
            chat?.members?.firstOrNull { it.userId == id }?.userProfile?.displayLabel() ?: name(id)
        }
        return LinkCard.Message.Excerpt(
            author = author,
            isFromSelf = isFromSelf,
            sentAt = message.timestamp,
            body = body.forReader(message, chatId),
        )
    }

    /**
     * Marks redacted text, and points a photo at its blob the way the transcript does (a sealed DM
     * photo decrypts on load). A redacted message has no blob to point at, so it stays BlurHash only.
     */
    private fun LinkCard.Message.Body.forReader(message: ChatMessage, chatId: ChatId): LinkCard.Message.Body =
        when (this) {
            is LinkCard.Message.Body.Text -> if (message.redacted) copy(redacted = true) else this
            is LinkCard.Message.Body.Photo -> copy(
                caption = caption?.let { if (message.redacted) it.copy(redacted = true) else it },
                photo = if (message.redacted) null else message.thumbnail()?.let { rendition ->
                    ChatPhoto(
                        chatId = chatId,
                        rendition = rendition,
                        senderId = message.senderId,
                        sealed = message.encryption != null,
                    )
                },
            )
            else -> this
        }

    /** The smallest rendition that fills the card's thumbnail, as the transcript picks for a bubble. */
    private fun ChatMessage.thumbnail(): MediaItemRendition? =
        content.firstNotNullOfOrNull { it.photoBody() }
            ?.items?.firstOrNull()
            ?.let { it.renditionForSize(THUMBNAIL_PX) ?: it.rendition() }
            ?.takeIf { it.blobId.bytes.isNotEmpty() }

    /**
     * A name the app already knows (any chat or saved profile) before a profile fetch, the order
     * iOS uses, so a sender met elsewhere is named without a round-trip.
     */
    private suspend fun name(userId: ID): String? =
        knownProfiles.getCachedProfile(userId)?.displayLabel()
            ?: profiles.getProfileForUser(userId).getOrNull()
                ?.also { knownProfiles.store(userId, it) }
                ?.displayLabel()

    private fun UserProfile.displayLabel(): String? = nameOrHandle(displayName, handle)

    /** The card's body for this message, or null when there is nothing to show: deleted or sealed. */
    private fun ChatMessage.body(): LinkCard.Message.Body? = content.singleOrNull()?.body()

    private val ChatMessage.isDeleted: Boolean get() = content.singleOrNull() is MessageContent.Deleted

    private fun MessageContent.body(): LinkCard.Message.Body? = when (this) {
        is MessageContent.Text -> LinkCard.Message.Body.Text(text)
        is MessageContent.Reply -> content.singleOrNull()?.body()
        is MessageContent.Media -> LinkCard.Message.Body.Photo(
            caption = caption?.text?.takeIf { it.isNotEmpty() }?.let { LinkCard.Message.Body.Text(it) },
            photo = null,
            blurHash = items.firstOrNull()?.renditions?.firstNotNullOfOrNull { it.blob?.image?.blurhash?.takeIf(String::isNotEmpty) },
        )
        is MessageContent.Cash -> LinkCard.Message.Body.Cash(
            amount = amount.formatted(),
            tipped = action == MessageContent.Cash.Action.TIPPED,
        )
        is MessageContent.Widget, is MessageContent.System -> LinkCard.Message.Body.Other
        is MessageContent.Deleted, is MessageContent.Encrypted -> null
    }

    private fun Throwable.meansNothingThere(): Boolean = when (this) {
        is GetMessageError.NotFound,
        is GetMessageError.Denied,
        is GetChatError.NotFound,
        is GetChatError.Denied -> true
        else -> false
    }
}

/** The card's 56dp thumbnail at up to 3x density. */
private const val THUMBNAIL_PX = 168
