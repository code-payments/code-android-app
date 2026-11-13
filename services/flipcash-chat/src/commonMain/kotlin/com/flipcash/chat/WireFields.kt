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
