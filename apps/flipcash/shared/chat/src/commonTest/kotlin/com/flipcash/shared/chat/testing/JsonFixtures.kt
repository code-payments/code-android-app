package com.flipcash.shared.chat.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

/**
 * The slice of `org.json` the cross-platform vector tests read fixtures with, over
 * kotlinx.serialization, so those tests compile for the JVM and Android alike. `org.json` itself
 * only exists on Android.
 */
class JSONObject(private val obj: JsonObject) {
    constructor(json: String) : this(Json.parseToJsonElement(json) as JsonObject)

    fun getString(key: String): String = (value(key) as JsonPrimitive).content
    fun getInt(key: String): Int = (value(key) as JsonPrimitive).int
    fun getLong(key: String): Long = (value(key) as JsonPrimitive).long
    fun getBoolean(key: String): Boolean = (value(key) as JsonPrimitive).boolean
    fun getJSONObject(key: String): JSONObject = JSONObject(value(key) as JsonObject)
    fun getJSONArray(key: String): JSONArray = JSONArray(value(key) as JsonArray)
    fun optJSONObject(key: String): JSONObject? = (obj[key] as? JsonObject)?.let(::JSONObject)
    fun optString(key: String, default: String = ""): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: default
    fun isNull(key: String): Boolean = obj[key] == null || obj[key] is JsonNull
    /** The value as `org.json` hands it back: a Boolean, an Int (or Long), a Double or a String. */
    fun get(key: String): Any {
        val p = value(key) as JsonPrimitive
        return when {
            p.isString -> p.content
            p.content == "true" || p.content == "false" -> p.content.toBoolean()
            else -> p.content.toIntOrNull() ?: p.content.toLongOrNull() ?: p.content.toDouble()
        }
    }
    fun has(key: String): Boolean = key in obj
    fun keys(): Iterator<String> = obj.keys.iterator()

    private fun value(key: String): JsonElement = obj[key] ?: error("no `$key` in fixture")
}

class JSONArray(private val array: JsonArray) {
    fun length(): Int = array.size
    fun getString(index: Int): String = (array[index] as JsonPrimitive).content
    fun getInt(index: Int): Int = (array[index] as JsonPrimitive).int
    fun getJSONObject(index: Int): JSONObject = JSONObject(array[index] as JsonObject)
    fun getJSONArray(index: Int): JSONArray = JSONArray(array[index] as JsonArray)
}

/** A fixture bundled as a test resource; every test target here runs on the JVM. */
fun readFixture(name: String): String =
    JSONObject::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
