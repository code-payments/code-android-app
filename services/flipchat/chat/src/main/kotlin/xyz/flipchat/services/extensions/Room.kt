package xyz.flipchat.services.extensions

import xyz.flipchat.services.data.Room
import com.getcode.util.resources.ResourceHelper
import xyz.flipchat.services.chat.R

fun Room.titleOrFallback(resources: ResourceHelper, includeRoomPrefix: Boolean = true): String {
    return if (title != null) {
        resources.getString(
            R.string.title_explicitRoomTitle,
            roomNumber,
            title!!
        )
    } else {
        if (includeRoomPrefix) {
            resources.getString(
                R.string.title_implicitRoomTitle,
                roomNumber
            )
        } else {
            resources.getString(
                R.string.title_implicitRoomTitleWithoutPrefix,
                roomNumber
            )
        }
    }
}