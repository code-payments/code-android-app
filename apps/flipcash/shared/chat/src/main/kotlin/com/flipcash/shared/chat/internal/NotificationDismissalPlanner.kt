package com.flipcash.shared.chat.internal

/** A notification this app has on screen, reduced to what deciding a dismissal needs. */
internal data class PostedNotification(
    val id: Int,
    val group: String?,
    val isGroupSummary: Boolean,
)

/**
 * The notification ids to cancel when a chat's notification is dismissed.
 *
 * Always the chat's own [chatNotificationId], and its group's summary too when the chat was the
 * last member of that group. The summary a chat push posts has no title or text of its own: the
 * platform only folds it away while it has a child to show, and cancelling the child from the app
 * does not take the summary with it. Left behind, it renders as an empty notification until the
 * next push in the group gives it a child again.
 *
 * The summary is found by its flag rather than by recomputing the id it was posted under, so this
 * does not depend on how the push side numbers summaries.
 *
 * @param posted the app's active notifications, as read just before the cancel
 */
internal fun planNotificationDismissal(
    chatNotificationId: Int,
    posted: List<PostedNotification>,
): List<Int> {
    val group = posted.firstOrNull { it.id == chatNotificationId && !it.isGroupSummary }?.group
        ?: return listOf(chatNotificationId)

    val inGroup = posted.filter { it.group == group }
    val othersRemain = inGroup.any { !it.isGroupSummary && it.id != chatNotificationId }
    if (othersRemain) return listOf(chatNotificationId)

    return listOf(chatNotificationId) + inGroup.filter { it.isGroupSummary }.map { it.id }
}
