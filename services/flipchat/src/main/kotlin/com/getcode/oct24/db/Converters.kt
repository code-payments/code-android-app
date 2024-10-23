package com.getcode.oct24.db

import androidx.room.TypeConverter
import com.getcode.oct24.data.Member
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Converters {
    @TypeConverter
    fun membersToString(members: List<Member>) = Json.encodeToString(members)

    @TypeConverter
    fun stringToMembers(value: String) = Json.decodeFromString<List<Member>>(value)
}