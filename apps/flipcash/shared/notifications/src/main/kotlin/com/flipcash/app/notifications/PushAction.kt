package com.flipcash.app.notifications

import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.chat.ChatId

/**
 * A single unit of work a received push asks the app to perform.
 *
 * Modelling this as data rather than as control flow inside
 * [NotificationService.onMessageReceived] is what makes the push handling
 * rules unit-testable without Robolectric or a live FirebaseMessagingService.
 */
sealed interface PushAction {
    /** Server-side feed sync. Safe to request redundantly. */
    data object RefreshFeed : PushAction

    /** Fetch and persist full message history for one chat. */
    data class LoadMessages(val chatId: ChatId) : PushAction

    /** Refresh token/mint state. */
    data object UpdateTokens : PushAction

    /** Refresh the contact list. */
    data object SyncContacts : PushAction

    /** Post a user-visible notification. Absent for a silent push. */
    data class PostNotification(
        val title: String,
        val body: String?,
        val payload: NotificationPayload?,
    ) : PushAction
}
