package com.getcode.db

import androidx.room.TypeConverter
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64

class Converters {
    @TypeConverter
    fun stringToByteList(value: String): List<Byte> = value.decodeBase64().toList()

    @TypeConverter
    fun byteListToString(list: List<Byte>): String = list.toByteArray().encodeBase64()

    @TypeConverter
    fun intListToString(list: List<Int>) = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String) = value.split(",")

}