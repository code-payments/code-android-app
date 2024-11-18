package xyz.flipchat.notifications

import com.getcode.model.chat.MessageContent
import com.getcode.utils.ErrorUtils
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

fun RemoteMessage.parse(): FcNotification? {
    Timber.d("data=$data")
    val type = FcNotificationType.resolve(data)
    if (type == FcNotificationType.Unknown) {
        ErrorUtils.handleError(Throwable("Unknown notification type"))
        return null
    }

    if (!type.isNotifiable()) return FcNotification(type, "", MessageContent.Localized("", false))

    return FcNotification(type,
        this.notification?.title.orEmpty(),
        MessageContent.RawText(this.notification?.body.orEmpty(), false)
    )
}