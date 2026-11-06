package com.flipcash.libs.reflex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * An action's input schema, as a small subset of JSON Schema read off its serializer's descriptor:
 *
 * - a class or object: `{"type":"object","properties":{...},"required":[...]}`; `required` lists the
 *   properties without a default
 * - an enum: `{"type":"string","enum":[...]}`
 * - `string`, `integer`, `number`, `boolean`; a string with its own serializer (a chat id) adds
 *   `"format"` with that serializer's name
 * - a list: `{"type":"array","items":...}`; a map: `{"type":"object","additionalProperties":...}`
 * - a nullable value adds `"nullable":true`
 */
@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.inputSchema(): JsonObject = buildJsonObject {
    when (val kind = kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            put("type", "object")
            put("properties", buildJsonObject {
                elementNames.zip(elementDescriptors.toList()).forEach { (name, element) ->
                    put(name, element.inputSchema())
                }
            })
            putJsonArray("required") {
                (0 until elementsCount).filterNot(::isElementOptional).forEach { add(getElementName(it)) }
            }
        }
        SerialKind.ENUM -> {
            put("type", "string")
            putJsonArray("enum") { elementNames.forEach { add(it) } }
        }
        StructureKind.LIST -> {
            put("type", "array")
            put("items", getElementDescriptor(0).inputSchema())
        }
        StructureKind.MAP -> {
            put("type", "object")
            put("additionalProperties", getElementDescriptor(1).inputSchema())
        }
        is PrimitiveKind -> {
            put("type", kind.jsonType())
            val ownName = serialName.removeSuffix("?")
            if (!ownName.startsWith("kotlin.")) put("format", ownName)
        }
        is PolymorphicKind, SerialKind.CONTEXTUAL -> put("type", "any")
    }
    if (isNullable) put("nullable", true)
}

private fun PrimitiveKind.jsonType(): String = when (this) {
    PrimitiveKind.BOOLEAN -> "boolean"
    PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> "integer"
    PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> "number"
    PrimitiveKind.CHAR, PrimitiveKind.STRING -> "string"
}
