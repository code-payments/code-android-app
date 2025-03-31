package xyz.flipchat.services.extensions

import com.getcode.util.resources.ResourceHelper
import xyz.flipchat.services.chat.R
import xyz.flipchat.services.data.Room
import xyz.flipchat.services.domain.model.chat.Conversation

fun Conversation.titleOrFallback(resources: ResourceHelper): String {
    return if (title.isEmpty()) {
        resources.getString(
            R.string.title_implicitRoomTitleWithoutPrefix,
            roomNumber
        )
    } else {
        resources.getString(
            R.string.title_explicitRoomTitle,
            roomNumber,
            title
        )
    }
}

fun Room.titleOrFallback(resources: ResourceHelper): String {
    return if (title == null) {
        resources.getString(
            R.string.title_implicitRoomTitleWithoutPrefix,
            roomNumber
        )
    } else {
        resources.getString(
            R.string.title_explicitRoomTitle,
            roomNumber,
            title!!
        )
    }
}