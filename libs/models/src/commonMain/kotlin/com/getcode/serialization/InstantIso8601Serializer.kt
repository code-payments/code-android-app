package com.getcode.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

class InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.Companion.parse(fixBasicTz(decoder.decodeString()))
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}

/**
 * Insert a colon in the time zone offset if not provided.
 *
 * iOS seems to return a string formatted '2022-02-01T04:01:14.874+0000' which is a mix of basic and
 * extended format ISO8601. Java only supports extended format at this stage so appends a ":00" at
 * the end and then fails to parse.
 *
 * Ref https://github.com/Kotlin/kotlinx-datetime/issues/139
 */
fun fixBasicTz(timeString: String): String {
    if (timeString.contains(basicFormatTimeZoneRegex)) {
        val pos = timeString.length - 2
        return timeString.replaceRange(pos, pos, ":")
    }
    if (timeString.contains(badFormatTimeZoneRegex)) {
        return timeString.dropLast(1)
    }
    return timeString
}

@Suppress("RegExpSimplifiable") // clearer with consistent numeric range style
private val basicFormatTimeZoneRegex = Regex("[+-][0-2][0-9][0-5][0-9]$")

@Suppress("RegExpSimplifiable") // clearer with consistent numeric range style
private val badFormatTimeZoneRegex = Regex("[+-][0-2][0-9]:[0-5][0-9]Z$")