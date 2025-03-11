package xyz.flipchat.notifications

import com.getcode.model.chat.MessageContent
import com.getcode.utils.ErrorUtils
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

fun RemoteMessage.parse(): FcNotification? {
    Timber.d("data=$data")
    val type = FcNotificationType.resolve(data)
    if (type is FcNotificationType.Unknown) {
        ErrorUtils.handleError(Throwable("Unknown notification type"))
        return null
    }

    if (!type.isNotifiable()) return FcNotification(type, "", MessageContent.Localized("", false))

    val title = data["title"] ?: notification?.title.orEmpty()
    val body = data["body"] ?: notification?.body.orEmpty()
    return FcNotification(
        type,
        title,
        MessageContent.RawText(body, false)
    )
}