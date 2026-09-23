package com.flipcash.app.notifications

import com.flipcash.app.core.util.Linkify
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType

/** Where tapping a chat notification lands. */
sealed interface ChatTapTarget {
    /**
     * The conversation itself, opened through [link].
     *
     * The link is carried rather than rebuilt at the call site so that the decision of *which*
     * link form a chat gets is made in one place, next to the type that decides it.
     */
    data class Conversation(val link: String) : ChatTapTarget

    /**
     * A plain launch, which opens the app on the camera.
     *
     * The fallback for a chat with no screen to open, rather than firing a link nothing routes:
     * an unrouted link still leaves the app on whatever it was showing, having taken the tap.
     */
    data object AppLauncher : ChatTapTarget
}

/**
 * Whether a chat push can open its conversation, and by which link.
 *
 * The Chats tab lists tip DMs and groups (`ChatsViewModel`: `feed(TIP_DM, GROUP)`), and both open
 * by chat id from there. A `CONTACT_DM` is not: the Send tab and direct-send flow that used to open
 * one were removed, and `AppRouter` deliberately routes nothing for them.
 *
 * A group takes its invite link, `app.flipcash.com/chat/{uuid}` — the same link a member shares —
 * rather than `/tip/chat/{id}`. Both resolve to the chat screen today, so this is not a fix for a
 * broken tap; it is what keeps the two entry points from being two things. **A push for a group the
 * viewer has not joined has to land on the gated preview**, which is the chat screen deciding what
 * to render from `GroupAccess`, and pointing both entry points at one link is what makes that a
 * property of the destination instead of an agreement between two callers.
 *
 * The invite form needs a 16-byte id, which every group id is (`common.v1.ChatId.value` is a UUID
 * for a group, a 32-byte hash for a DM). A group whose id is not UUID-shaped should not exist; if
 * one arrives, `/tip/chat/{id}` still reaches the same screen, so the tap degrades to the older
 * link rather than to the camera.
 *
 * [chatType] is the type resolved by [planConversationStyling], not what the payload declared.
 * `chat_metadata` is optional on the wire, and a group push that omits it would otherwise land on
 * the camera. Null and [ChatType.UNKNOWN] read the same: neither the push nor the device could say
 * what the chat is, and a chat screen opened for a type the app has no surface for would dead-end.
 */
fun planChatTapTarget(chatType: ChatType?, chatId: ChatId): ChatTapTarget = when (chatType) {
    ChatType.GROUP -> ChatTapTarget.Conversation(
        Linkify.groupChatInvite(chatId) ?: Linkify.tipChatById(chatId)
    )

    ChatType.TIP_DM -> ChatTapTarget.Conversation(Linkify.tipChatById(chatId))

    ChatType.CONTACT_DM, ChatType.UNKNOWN, null -> ChatTapTarget.AppLauncher
}
