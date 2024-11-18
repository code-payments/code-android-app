package xyz.flipchat.services.extensions

import xyz.flipchat.services.data.Room
import com.getcode.util.resources.ResourceHelper
import xyz.flipchat.services.chat.R

fun Room.titleOrFallback(resources: ResourceHelper): String {
    return title ?: resources.getString(
        R.string.title_implicitRoomTitle,
        roomNumber
    )
}