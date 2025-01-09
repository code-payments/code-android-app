package xyz.flipchat.services.extensions

import com.getcode.util.resources.ResourceHelper
import xyz.flipchat.services.chat.R
import xyz.flipchat.services.domain.model.chat.Conversation

fun Conversation.titleOrFallback(resources: ResourceHelper): String {
    if (title.startsWith("Room")) {
        return title
    }

    if (title.startsWith("#")) {
        return title
    }

    return if (title.isEmpty()) {
        resources.getString(
            R.string.title_implicitRoomTitle,
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