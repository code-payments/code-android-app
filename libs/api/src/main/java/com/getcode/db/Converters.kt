package com.getcode.db

import androidx.room.TypeConverter
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Pointer
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
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
    fun currencyCodeToString(value: com.getcode.model.CurrencyCode) = value.name
    @TypeConverter
    fun stringToCurrencyCode(value: String) = com.getcode.model.CurrencyCode.tryValueOf(value)

    @TypeConverter
    fun ratesToString(value: List<Rate>) = Json.encodeToString(value)
    @TypeConverter
    fun stringToRates(value: String) = Json.decodeFromString<List<Rate>>(value)

    @TypeConverter
    fun messageContentToString(value: MessageContent) = Json.encodeToString(value)
    @TypeConverter
    fun stringToMessageContent(value: String) = Json.decodeFromString<MessageContent>(value)

    @TypeConverter
    fun kinAmountToString(value: KinAmount) = Json.encodeToString(KinAmount.serializer(), value)

    @TypeConverter
    fun stringToKinAmount(value: String) = Json.decodeFromString(KinAmount.serializer(), value)

    @TypeConverter
    fun chatMemberToString(member: ChatMember) = Json.encodeToString(ChatMember.serializer(), member)

    @TypeConverter
    fun stringToChatMember(value: String) = Json.decodeFromString(ChatMember.serializer(), value)

    @TypeConverter
    fun chatMembersToString(members: List<ChatMember>) = Json.encodeToString(members)

    @TypeConverter
    fun stringToChatMembers(value: String) = Json.decodeFromString<List<ChatMember>>(value)

    @TypeConverter
    fun pointerToString(pointer: Pointer) = Json.encodeToString(pointer)

    @TypeConverter
    fun stringToPointer(value: String) = Json.decodeFromString<Pointer>(value)

    @TypeConverter
    fun pointersToString(pointer: List<Pointer>) = Json.encodeToString(pointer)

    @TypeConverter
    fun stringToPointers(value: String) = Json.decodeFromString<List<Pointer>>(value)
}