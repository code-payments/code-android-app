package com.getcode.oct24.internal.db

import androidx.room.TypeConverter
import xyz.flipchat.services.data.Member
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object Converters {
    @TypeConverter
    fun membersToString(members: List<Member>) = Json.encodeToString(members)

    @TypeConverter
    fun stringToMembers(value: String) = Json.decodeFromString<List<Member>>(value)
}