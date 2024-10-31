package com.getcode.oct24.extensions

import com.getcode.oct24.data.Room
import com.getcode.oct24.services.R
import com.getcode.util.resources.ResourceHelper

fun Room.titleOrFallback(resources: ResourceHelper): String {
    return title ?: resources.getString(
        R.string.title_implicitRoomTitle,
        roomNumber
    )
}