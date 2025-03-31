package xyz.flipchat.services.user

import com.getcode.model.Kin
import com.getcode.solana.keys.PublicKey

data class UserFlags(
    val isStaff: Boolean,
    val createCost: Kin,
    val feeDestination: PublicKey,
    val isRegistered: Boolean,
    val typingNotifications: TypingNotificationsConstraints,
)

data class TypingNotificationsConstraints(
    // Can this user call NotifyIsTyping at all?
    val canSendAtAll: Boolean,
    // Can this user call NotifyIsTyping in chats where they are a listener?
    val canSendAsListener: Boolean,
    // Interval between calling NotifyIsTyping
    val interval: Long,
    // Client-side timeout for when they haven't seen an IsTyping event from a user.
    // After this timeout has elapsed, client should assume the user has stopped typing.
    val timeout: Long,
)