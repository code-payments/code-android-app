package com.flipcash.services.models.chat

import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate

data class ChatUpdate(
    val chatId: ChatId,
    val pointerUpdates: List<MessagePointer> = emptyList(),
    val typingNotifications: List<TypingNotification> = emptyList(),
    val metadataUpdates: List<MetadataUpdate> = emptyList(),
    val events: List<ChatEvent> = emptyList(),
    val reactionUpdates: List<ReactionUpdate> = emptyList(),
    // Convergent, like reactionUpdates and unlike events: applied by RosterSummary.version,
    // never gap-filled. See RosterChange.
    val rosterUpdates: List<RosterChange> = emptyList(),
    // Creator-only, best-effort lobby changes for a private group. Unversioned; see LobbyUpdate.
    val lobbyUpdates: List<LobbyUpdate> = emptyList(),
    // Best-effort real-time payment request changes; not part of the gap-detected event log.
    // Apply per PaymentRequestUpdate's rules; see its docs.
    val paymentRequestUpdates: List<PaymentRequestUpdate> = emptyList(),
)
