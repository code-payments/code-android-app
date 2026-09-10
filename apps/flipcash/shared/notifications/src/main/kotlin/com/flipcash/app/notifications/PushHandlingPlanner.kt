package com.flipcash.app.notifications

import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload

/**
 * Decides what a received push should cause the app to do.
 *
 * Pure by construction: no Android types, no coroutines, no injection. Every
 * rule about push handling is testable here, which is the point.
 *
 * @param title resolved push title, null for a data-only push
 * @param body resolved push body, may be null even for a visible push
 * @param payload decoded [NotificationPayload], null when absent or undecodable
 * @param silentSyncEnabled reads the `PushSilentSync` feature flag. Passed as a
 *   function because only a data-only push consults it, and the call site's
 *   read is a blocking DataStore lookup on the FCM dispatch thread — a visible
 *   push should not pay for a flag that cannot change its outcome.
 */
fun planPushHandling(
    title: String?,
    body: String?,
    payload: NotificationPayload?,
    silentSyncEnabled: () -> Boolean,
): List<PushAction> {
    if (title == null) {
        return if (silentSyncEnabled()) syncActionsFor(payload) else emptyList()
    }

    return syncActionsFor(payload) + PushAction.PostNotification(title, body, payload)
}

/**
 * The sync work an event class implies, independent of where the push
 * navigates.
 *
 * `flipcash.push.v1.Payload.category` is the event taxonomy — there is no
 * separate event field coming, so this table is keyed on the taxonomy already.
 * Adding an event class is an entry here rather than a branch in
 * [syncActionsFor].
 *
 * A category absent from the table plans no sync of its own, which is every
 * category but one today.
 */
private val syncByCategory: Map<NotificationCategory, List<PushAction>> = mapOf(
    NotificationCategory.CONTACT_JOIN to listOf(PushAction.RefreshFeed, PushAction.SyncContacts),
)

/**
 * The sync work a navigation target implies.
 *
 * This stays a `when` rather than joining the table above: each arm reads data
 * off the trigger it matched, so the actions cannot be written down in advance.
 *
 * A push at a named chat resolves to one of two shapes. When the payload
 * inlines the message, [PushAction.ApplyMessage] writes it locally; when it
 * does not — the field is optional and the body is size-limited — the plan
 * falls back to the [PushAction.LoadMessages] fetch it has always used.
 */
private fun syncForNavigation(payload: NotificationPayload): List<PushAction> =
    when (val navigation = payload.navigation) {
        is NavigationTrigger.CurrencyInfo -> listOf(PushAction.UpdateTokens)
        is NavigationTrigger.Chat.ById -> listOf(
            PushAction.RefreshFeed,
            payload.chatMetadata?.message
                ?.let { PushAction.ApplyMessage(navigation.chatId, it) }
                ?: PushAction.LoadMessages(navigation.chatId),
        )
        is NavigationTrigger.Chat.ByContact -> emptyList()
        null -> emptyList()
    }

/**
 * The sync work implied by [payload], independent of visibility.
 *
 * `distinct()` is what lets the two sources overlap without the caller
 * knowing: a contact-join push that also names a chat asks for
 * [PushAction.RefreshFeed] from both and gets one.
 */
private fun syncActionsFor(payload: NotificationPayload?): List<PushAction> {
    if (payload == null) return emptyList()
    return (syncByCategory[payload.category].orEmpty() + syncForNavigation(payload))
        .distinct()
}
