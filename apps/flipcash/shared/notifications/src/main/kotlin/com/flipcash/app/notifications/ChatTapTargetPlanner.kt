package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType

/** Where tapping a chat notification lands. */
enum class ChatTapTarget {
    /** The conversation itself, through the app's chat-by-id link. */
    Conversation,

    /**
     * A plain launch, which opens the app on the camera.
     *
     * The fallback for a chat with no screen to open, rather than firing a link nothing routes:
     * an unrouted link still leaves the app on whatever it was showing, having taken the tap.
     */
    AppLauncher,
}

/**
 * Whether a chat push can open its conversation.
 *
 * The Chats tab lists tip DMs and groups (`TipFlowViewModel`: `feed(TIP_DM, GROUP)`), and both
 * open by chat id from there, so both are reachable through the `/tip/chat/{id}` link the router
 * resolves to that tab. A `CONTACT_DM` is not: the Send tab and direct-send flow that used to
 * open one were removed, and `AppRouter` deliberately routes nothing for them.
 *
 * [chatType] is the type resolved by [planConversationStyling], not what the payload declared.
 * `chat_metadata` is optional on the wire, and a group push that omits it would otherwise land on
 * the camera. Null and [ChatType.UNKNOWN] read the same: neither the push nor the device could
 * say what the chat is, and a chat screen opened for a type the app has no surface for would
 * dead-end.
 */
fun planChatTapTarget(chatType: ChatType?): ChatTapTarget = when (chatType) {
    ChatType.TIP_DM, ChatType.GROUP -> ChatTapTarget.Conversation
    ChatType.CONTACT_DM, ChatType.UNKNOWN, null -> ChatTapTarget.AppLauncher
}
