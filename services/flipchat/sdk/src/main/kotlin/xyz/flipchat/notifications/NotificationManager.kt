package xyz.flipchat.notifications

import androidx.core.app.NotificationManagerCompat
import com.getcode.model.ID
import com.getcode.utils.base58

fun NotificationManagerCompat.getRoomNotifications(roomId: ID, roomName: String): List<Int> {
    val barNotifications = activeNotifications
    val roomNotifications = barNotifications.mapNotNull { notification ->
        val roomIdHash = roomId.base58.hashCode()
        val roomNameHash = roomName.hashCode()

        val isMatch = notification.id == roomIdHash || notification.id == roomNameHash

        if (isMatch) {
            notification.id
        } else {
            null
        }
    }

    return roomNotifications
}