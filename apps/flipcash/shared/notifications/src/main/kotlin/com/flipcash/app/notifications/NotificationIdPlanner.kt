package com.flipcash.app.notifications

/**
 * The id the test push posts under, every time. A new test replaces the last one in the shade
 * rather than posting beside it: Android 16 auto-groups a second notification from the app and
 * posts the newcomer silently, so a test sent while the last is still showing would not pop up.
 * Replacing the notification still alerts, because the builder does not set `onlyAlertOnce`.
 */
const val TEST_PUSH_NOTIFICATION_ID = 0x7E57

/**
 * The id a notification that is not a chat posts under: [TEST_PUSH_NOTIFICATION_ID] on
 * [NotificationChannels.CHANNEL_TEST_PUSH], and [randomId] otherwise, so other pushes each post a
 * notification of their own. A chat posts under its chat id and does not come through here.
 *
 * @param channelId the id of the channel the notification posts on
 */
fun planNotificationId(channelId: String, randomId: () -> Int): Int =
    if (channelId == NotificationChannels.CHANNEL_TEST_PUSH) TEST_PUSH_NOTIFICATION_ID else randomId()
