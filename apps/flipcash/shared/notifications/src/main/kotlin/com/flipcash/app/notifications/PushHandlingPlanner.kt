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
 * @param silentSyncEnabled whether the `PushSilentSync` feature flag is on
 */
fun planPushHandling(
    title: String?,
    body: String?,
    payload: NotificationPayload?,
    silentSyncEnabled: Boolean,
): List<PushAction> {
    if (title == null) {
        return if (silentSyncEnabled) syncActionsFor(payload) else emptyList()
    }

    val actions = mutableListOf<PushAction>()
    actions += syncActionsFor(payload)
    actions += PushAction.PostNotification(title, body, payload)
    return actions
}

/** The sync work implied by [payload], independent of visibility. */
private fun syncActionsFor(payload: NotificationPayload?): List<PushAction> {
    if (payload == null) return emptyList()

    val actions = mutableListOf<PushAction>()

    if (payload.navigation is NavigationTrigger.CurrencyInfo) {
        actions += PushAction.UpdateTokens
    }

    if (payload.category == NotificationCategory.CONTACT_JOIN) {
        actions += PushAction.RefreshFeed
        actions += PushAction.SyncContacts
    }

    val navigation = payload.navigation
    if (navigation is NavigationTrigger.Chat.ById) {
        if (PushAction.RefreshFeed !in actions) actions += PushAction.RefreshFeed
        actions += PushAction.LoadMessages(navigation.chatId)
    }

    return actions
}
