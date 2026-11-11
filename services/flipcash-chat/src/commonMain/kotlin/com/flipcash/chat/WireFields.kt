package com.flipcash.chat

import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoReader
import okio.Buffer
import okio.ByteString

/**
 * The raw number of enum field [tag] when Wire could not map it to a constant.
 *
 * Wire decodes an enum number it has no constant for to the zero constant and keeps the number in
 * `unknownFields`; protobuf-java keeps it as `*Value`. Android passes that raw number through (an
 * unknown `scheme` stays distinguishable from `UNKNOWN`), so this reads it back.
 */
internal fun rawEnum(unknownFields: ByteString, tag: Int): Int? {
    if (unknownFields.size == 0) return null
    val reader = ProtoReader(Buffer().write(unknownFields))
    var value: Int? = null
    val token = reader.beginMessage()
    while (true) {
        val next = reader.nextTag()
        if (next == -1) break
        if (next == tag && reader.peekFieldEncoding() == FieldEncoding.VARINT) {
            value = reader.readVarint64().toInt()
        } else {
            reader.skip()
        }
    }
    reader.endMessageAndGetUnknownFields(token)
    return value
}

/**
 * Rewrites a serialized message with its fields in field-number order, as protobuf-java (and the
 * Go server) writes them. Wire writes a message's regular fields first and its oneof members after
 * them, so a message with a oneof member numbered below a regular field (`CashContent`'s
 * `single_receiver = 3` before `verb = 4`) comes out in a different order: still valid protobuf,
 * but not the same bytes, and a signature over Wire's bytes would not match a re-marshal.
 *
 * The sort is stable, so repeated fields keep their order. [nested] rewrites the payload of the
 * listed length-delimited fields first; fields not listed are copied as they are.
 */
internal fun inFieldOrder(bytes: ByteArray, nested: Map<Int, (ByteArray) -> ByteArray> = emptyMap()): ByteArray {
    class Field(val number: Int, val key: ByteArray, val payload: ByteArray, val lengthDelimited: Boolean)

    val fields = mutableListOf<Field>()
    var i = 0
    fun varint(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val b = bytes[i++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b < 0x80) return result
            shift += 7
        }
    }
    while (i < bytes.size) {
        val keyStart = i
        val key = varint()
        val keyBytes = bytes.copyOfRange(keyStart, i)
        val number = (key ushr 3).toInt()
        when (val wireType = (key and 7).toInt()) {
            0 -> { val start = i; varint(); fields += Field(number, keyBytes, bytes.copyOfRange(start, i), false) }
            1 -> { fields += Field(number, keyBytes, bytes.copyOfRange(i, i + 8), false); i += 8 }
            5 -> { fields += Field(number, keyBytes, bytes.copyOfRange(i, i + 4), false); i += 4 }
            2 -> {
                val length = varint().toInt()
                val payload = bytes.copyOfRange(i, i + length)
                i += length
                fields += Field(number, keyBytes, nested[number]?.invoke(payload) ?: payload, true)
            }
            else -> throw IllegalArgumentException("Unsupported wire type $wireType")
        }
    }
    val out = Buffer()
    for (field in fields.sortedBy { it.number }) {
        out.write(field.key)
        if (field.lengthDelimited) out.writeVarint(field.payload.size.toLong())
        out.write(field.payload)
    }
    return out.readByteArray()
}

private fun Buffer.writeVarint(value: Long) {
    var v = value
    while (v and 0x7fL.inv() != 0L) {
        writeByte(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
    writeByte(v.toInt())
}
