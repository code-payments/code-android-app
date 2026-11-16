package com.getcode.wire.canonical

import com.squareup.wire.Message
import okio.Buffer

/**
 * The fields of every message a canonical encoding can reach, keyed by full proto name
 * (`ocp.transaction.v1.SubmitIntentRequest`). A field absent from a message's map is an unknown
 * field and is written after the known ones.
 */
public class CanonicalSchema(public val messages: Map<String, Map<Int, CanonicalField>>) {
    public operator fun plus(other: CanonicalSchema): CanonicalSchema = CanonicalSchema(messages + other.messages)
}

public sealed interface CanonicalField {
    /** Any field whose payload is copied as it is: scalars, strings, bytes, enums, packed lists. */
    public data object Scalar : CanonicalField

    /** A singular or repeated field of message type [type]. */
    public data class Message(val type: String) : CanonicalField

    /** A map field. Entries are sorted by key; [valueType] is set when values are messages. */
    public data class MapOf(val key: MapKey, val valueType: String?) : CanonicalField
}

/** How map keys compare, matching Go's protoreflect ordering. */
public enum class MapKey { Bool, Signed, ZigZag, Unsigned, Bytes }

/**
 * Encodes this message the way the OCP and Flipcash servers re-marshal a request before verifying
 * its signature (`ForceConsistentMarshal` in ocp-server `ocp/auth/encoding.go`): known fields in
 * field-number order with oneof members in place, map entries sorted by key, nested messages
 * rewritten the same way, unknown fields last.
 *
 * Wire writes a message's regular fields first and its oneof members after them, so its own
 * `encode()` differs from that order for any message with a oneof member numbered below a regular
 * field. A signature over Wire's bytes only verifies through the server's `proto.Marshal`
 * fallback, which is marked for removal. Sign and send these bytes instead.
 */
public fun Message<*, *>.encodeCanonical(schema: CanonicalSchema): ByteArray {
    val typeUrl = requireNotNull(adapter.typeUrl) { "${this::class.simpleName} has no type URL" }
    return canonicalize(encode(), typeUrl.substringAfterLast('/'), schema)
}

/** Rewrites [bytes], a serialized [type], into canonical order. See [encodeCanonical]. */
public fun canonicalize(bytes: ByteArray, type: String, schema: CanonicalSchema): ByteArray {
    val fields = requireNotNull(schema.messages[type]) { "No canonical schema for $type" }
    val records = Records(bytes).readAll()

    val out = Buffer()
    val known = records.filter { it.number in fields }.sortedBy { it.number } // stable: repeated order kept
    val unknown = records.filter { it.number !in fields }

    // Map entries are sorted within each map field, so group consecutive numbers first.
    var index = 0
    while (index < known.size) {
        val number = known[index].number
        var end = index
        while (end < known.size && known[end].number == number) end++
        val group = known.subList(index, end)
        when (val field = fields.getValue(number)) {
            CanonicalField.Scalar -> group.forEach { out.writeRecord(it, it.payload) }
            is CanonicalField.Message -> group.forEach {
                out.writeRecord(it, if (it.lengthDelimited) canonicalize(it.payload, field.type, schema) else it.payload)
            }
            is CanonicalField.MapOf -> group
                .map { it to canonicalEntry(it.payload, field, schema) }
                .sortedWith { a, b -> compareKeys(a.second.first, b.second.first, field.key) }
                .forEach { (record, entry) -> out.writeRecord(record, entry.second) }
        }
        index = end
    }
    unknown.forEach { out.writeRecord(it, it.payload) }
    return out.readByteArray()
}

/** A map entry rewritten as key (field 1) then value (field 2), with the key's raw payload. */
private fun canonicalEntry(
    bytes: ByteArray,
    field: CanonicalField.MapOf,
    schema: CanonicalSchema,
): Pair<Record?, ByteArray> {
    val records = Records(bytes).readAll()
    val key = records.lastOrNull { it.number == 1 }
    val value = records.lastOrNull { it.number == 2 }
    val out = Buffer()
    key?.let { out.writeRecord(it, it.payload) }
    value?.let {
        val payload = if (field.valueType != null && it.lengthDelimited) canonicalize(it.payload, field.valueType, schema) else it.payload
        out.writeRecord(it, payload)
    }
    return key to out.readByteArray()
}

private fun compareKeys(a: Record?, b: Record?, kind: MapKey): Int {
    // An absent key is the type's zero value.
    val x = a?.payload ?: ByteArray(0)
    val y = b?.payload ?: ByteArray(0)
    return when (kind) {
        MapKey.Bytes -> compareUnsignedBytes(x, y)
        MapKey.Bool, MapKey.Unsigned -> scalarValue(a).toULong().compareTo(scalarValue(b).toULong())
        MapKey.Signed -> signedValue(a).compareTo(signedValue(b))
        MapKey.ZigZag -> zigZag(scalarValue(a)).compareTo(zigZag(scalarValue(b)))
    }
}

private fun compareUnsignedBytes(x: ByteArray, y: ByteArray): Int {
    for (i in 0 until minOf(x.size, y.size)) {
        val c = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
        if (c != 0) return c
    }
    return x.size - y.size
}

/** The raw 64-bit value of a varint or fixed key. */
private fun scalarValue(record: Record?): Long {
    if (record == null) return 0
    val p = record.payload
    return when (record.wireType) {
        0 -> Records(p).varint()
        1 -> (0 until 8).fold(0L) { acc, i -> acc or ((p[i].toLong() and 0xff) shl (8 * i)) }
        5 -> (0 until 4).fold(0L) { acc, i -> acc or ((p[i].toLong() and 0xff) shl (8 * i)) }
        else -> 0
    }
}

private fun zigZag(raw: Long): Long = (raw ushr 1) xor -(raw and 1)

/** A signed key's value: int32/int64 varints are sign-extended already; sfixed32 needs widening. */
private fun signedValue(record: Record?): Long {
    if (record == null) return 0
    val raw = scalarValue(record)
    return when (record.wireType) {
        5 -> raw.toInt().toLong()
        else -> raw
    }
}

private class Record(
    val number: Int,
    val wireType: Int,
    val key: ByteArray,
    val payload: ByteArray,
) {
    val lengthDelimited: Boolean get() = wireType == 2
}

private class Records(private val bytes: ByteArray) {
    private var i = 0

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

    fun readAll(): List<Record> {
        val records = mutableListOf<Record>()
        while (i < bytes.size) {
            val keyStart = i
            val key = varint()
            val keyBytes = bytes.copyOfRange(keyStart, i)
            val number = (key ushr 3).toInt()
            val wireType = (key and 7).toInt()
            val payload = when (wireType) {
                0 -> { val start = i; varint(); bytes.copyOfRange(start, i) }
                1 -> bytes.copyOfRange(i, i + 8).also { i += 8 }
                5 -> bytes.copyOfRange(i, i + 4).also { i += 4 }
                2 -> { val length = varint().toInt(); bytes.copyOfRange(i, i + length).also { i += length } }
                else -> throw IllegalArgumentException("Unsupported wire type $wireType")
            }
            records += Record(number, wireType, keyBytes, payload)
        }
        return records
    }
}

private fun Buffer.writeRecord(record: Record, payload: ByteArray) {
    write(record.key)
    if (record.lengthDelimited) writeVarint(payload.size.toLong())
    write(payload)
}

private fun Buffer.writeVarint(value: Long) {
    var v = value
    while (v and 0x7fL.inv() != 0L) {
        writeByte(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
    writeByte(v.toInt())
}
