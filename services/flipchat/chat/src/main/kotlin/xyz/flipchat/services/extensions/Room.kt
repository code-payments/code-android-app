package xyz.flipchat.services.extensions

import com.getcode.oct24.services.chat.R
import xyz.flipchat.services.data.Room
import com.getcode.util.resources.ResourceHelper

fun Room.titleOrFallback(resources: ResourceHelper): String {
    return title ?: resources.getString(
        R.string.title_implicitRoomTitle,
        roomNumber
    )
}