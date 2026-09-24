package com.flipcash.analytics

/**
 * One event, as both apps hand it to Mixpanel. The builders in `events/` are the only
 * place a name or a property key is written; nothing else in either app should spell one.
 */
data class AnalyticsEvent(val name: String, val properties: Map<String, PropertyValue> = emptyMap())

/**
 * A property's value with its type. Mixpanel treats `"12"` and `12` as different
 * values, so the type is part of the contract, not a detail of either platform's sender.
 */
sealed interface PropertyValue {
    data class Text(val value: String) : PropertyValue
    data class Number(val value: Double) : PropertyValue
    data class Flag(val value: Boolean) : PropertyValue
}

/** A null value omits the key rather than sending an empty value. */
internal class PropertiesBuilder {
    private val map = LinkedHashMap<String, PropertyValue>()
    fun text(key: String, value: String?) { if (value != null) map[key] = PropertyValue.Text(value) }
    fun number(key: String, value: Double?) { if (value != null) map[key] = PropertyValue.Number(value) }
    fun number(key: String, value: Long?) = number(key, value?.toDouble())
    fun flag(key: String, value: Boolean?) { if (value != null) map[key] = PropertyValue.Flag(value) }
    fun build(): Map<String, PropertyValue> = map
}

internal fun event(name: String, block: PropertiesBuilder.() -> Unit = {}) =
    AnalyticsEvent(name, PropertiesBuilder().apply(block).build())
