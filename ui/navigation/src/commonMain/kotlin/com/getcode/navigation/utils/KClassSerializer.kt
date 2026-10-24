package com.getcode.navigation.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

/**
 * A serializer for [KClass] that serializes the class by its Java class name.
 *
 * Note: This serializer does not handle generic types or inner classes. This takes the approach of
 * stringifying the class name. It should be able to handle obfuscation but won't be stable across
 * app versions if obfuscation mappings change. It won't is also unlikely to be stable with local or
 * anonymous classes.
 */
@OptIn(ExperimentalSerializationApi::class)
object KClassSerializer : KSerializer<KClass<*>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "KClass",
        PrimitiveKind.STRING,
    )

    private val primitiveByJavaName: Map<String, Class<*>> = mapOf(
        "boolean" to java.lang.Boolean.TYPE,
        "byte" to java.lang.Byte.TYPE,
        "char" to java.lang.Character.TYPE,
        "short" to java.lang.Short.TYPE,
        "int" to java.lang.Integer.TYPE,
        "long" to java.lang.Long.TYPE,
        "float" to java.lang.Float.TYPE,
        "double" to java.lang.Double.TYPE,
        "void" to java.lang.Void.TYPE
    )

    override fun serialize(encoder: Encoder, value: KClass<*>) {
        encoder.encodeString(value.java.name)
    }

    override fun deserialize(decoder: Decoder): KClass<*> {
        val name = decoder.decodeString()
        val jClass = primitiveByJavaName[name]
            ?: Class.forName(name)
        return jClass.kotlin
    }
}
