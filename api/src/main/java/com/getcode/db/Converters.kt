package com.getcode.db

import androidx.room.TypeConverter
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun stringToByteList(value: String): List<Byte> = value.decodeBase64().toList()

    @TypeConverter
    fun byteListToString(list: List<Byte>): String = list.toByteArray().encodeBase64()

    @TypeConverter
    fun intListToString(list: List<Int>) = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String) = value.split(",")

    @TypeConverter
    fun currencyCodeToString(value: CurrencyCode) = value.name
    @TypeConverter
    fun stringToCurrencyCode(value: String) = CurrencyCode.tryValueOf(value)

    @TypeConverter
    fun ratesToString(value: List<Rate>) = Json.encodeToString(value)
    @TypeConverter
    fun stringToRates(value: String) = Json.decodeFromString<List<Rate>>(value)
}