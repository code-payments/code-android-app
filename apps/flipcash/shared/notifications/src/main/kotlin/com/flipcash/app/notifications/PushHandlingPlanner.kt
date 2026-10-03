package com.flipcash.app.notifications

import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.shared.chat.shouldNotify

/**
 * Decides what a received push should cause the app to do.
 *
 * Pure by construction: no Android types, no coroutines, no injection. Every
 * rule about push handling is testable here, which is the point.
 *
 * @param title resolved push title, null for a data-only push
 * @param body resolved push body, may be null even for a visible push
 * @param payload decoded [NotificationPayload], null when absent or undecodable
 * @param archived whether the viewer has this chat archived, read from local storage: the push
 *   payload carries no archive flag yet
 * @param mentionsViewer whether the message text @mentions the viewer; false when it cannot be told
 * @param repliesToViewer whether the message replies to one of the viewer's messages; false when
 *   the cited message is not stored locally
 */
fun planPushHandling(
    title: String?,
    body: String?,
    payload: NotificationPayload?,
    archived: Boolean = false,
    mentionsViewer: Boolean = false,
    repliesToViewer: Boolean = false,
): List<PushAction> {
    // The sync half is the same either way; a title adds the notification on top. Archived chats
    // still sync: the message must reach the database either way.
    val sync = syncActionsFor(payload)
    val post = planNotification(title, body, payload, archived, mentionsViewer, repliesToViewer)
    return if (post == null) sync else sync + post
}

/**
 * The notification half of [planPushHandling] on its own, for the caller that can only learn
 * [archived], [mentionsViewer] and [repliesToViewer] later: the per-user database they are read
 * from opens with authentication, which a cold start from a push has not done yet.
 *
 * - Muted when the push was sent: nothing, archived or not.
 * - Otherwise, when [shouldNotify] allows it: a normal notification.
 * - An archived chat's message not addressed to the viewer: a [PushAction.PostNotification.silent]
 *   one rather than none, as iOS delivers it passive.
 *
 * `null` for a data-only push ([title] null).
 */
fun planNotification(
    title: String?,
    body: String?,
    payload: NotificationPayload?,
    archived: Boolean = false,
    mentionsViewer: Boolean = false,
    repliesToViewer: Boolean = false,
): PushAction.PostNotification? {
    if (title == null) return null
    val muted = isMuted(payload)
    val notify = shouldNotify(
        archived = archived,
        muted = muted,
        mentionsViewer = mentionsViewer,
        repliesToViewer = repliesToViewer,
    )
    return when {
        notify -> PushAction.PostNotification(title, body, payload)
        muted -> null
        else -> PushAction.PostNotification(title, body, payload, silent = true)
    }
}

/**
 * Whether this push arrived for a chat the recipient has silenced.
 *
 * Read off the payload rather than off the local mute, because the flag is what the server decided
 * when it sent: a timed mute lapses with nothing sent to say so, so the two can disagree, and the
 * send is the moment that matters. The server delivers a muted push anyway, deliberately, so the
 * message still reaches the database — which is why suppression lands here, on the one action that
 * is user-visible, and not on the push as a whole.
 */
private fun isMuted(payload: NotificationPayload?): Boolean =
    payload?.chatMetadata?.muted == true

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
