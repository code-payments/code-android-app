package com.flipcash.app.notifications

/**
 * The group a notification is posted under, or null to post it ungrouped with no summary.
 *
 * A chat is never grouped. It posts one notification, under its chat id, and folds every new
 * message into it, so a group of its own would only ever hold that one child. Android 16 force
 * groups exactly that shape: it cancels the app's summary and moves the child into a system
 * group, and every new message moves the child back out for a few seconds before it is moved in
 * again. The summary has no content, so for those seconds it shows as an empty notification.
 *
 * Other pushes keep the payload's key, because they post a new notification each time and several
 * can share one group.
 *
 * @param payloadGroupKey `NotificationPayload.groupKey`, where blank means none
 * @param isChat whether the notification is rendered as a chat conversation
 */
fun planNotificationGroup(payloadGroupKey: String?, isChat: Boolean): String? {
    if (isChat) return null
    return payloadGroupKey?.takeIf { it.isNotEmpty() }
}
