package com.getcode.buildlogic.analytics

/** The catalogue is malformed. The message names the entry at fault. */
class CatalogueException(message: String) : IllegalArgumentException(message)

/** `events.toml`, parsed and checked. Every reference in it resolves. */
internal data class Catalogue(
    val packageName: String,
    val enums: List<EnumDef>,
    val counters: List<EnumCase>,
    val domains: List<Domain>,
)

/** A value list: a Kotlin enum whose cases carry the string sent to Mixpanel. */
internal data class EnumDef(val name: String, val doc: String?, val cases: List<EnumCase>)

internal data class EnumCase(val name: String, val value: String, val drift: String?)

/** One generated `object`, e.g. `ChatEvents`, named from the [title] entries give, e.g. `Chat`. */
internal data class Domain(val name: String, val title: String, val doc: String?, val builders: List<Builder>)

internal data class Builder(
    val name: String,
    val event: Template,
    val params: List<Param>,
    val properties: List<Property>,
    val doc: String?,
    val drift: String?,
)

internal data class Param(val name: String, val type: ParamType, val nullable: Boolean) {
    val kotlinType: String get() = type.kotlinName + if (nullable) "?" else ""
}

internal sealed class ParamType(val kotlinName: String) {
    data object Text : ParamType("String")
    data object Decimal : ParamType("Double")
    data object Whole : ParamType("Long")
    data object SmallWhole : ParamType("Int")
    data object Flag : ParamType("Boolean")

    /** The hand-written `Amount`, sent only through the `amount` group. */
    data object Amount : ParamType("Amount")
    data class Enum(val name: String) : ParamType(name)
}

internal sealed interface Property {
    /** A parameter sent as it is, typed by the parameter. */
    data class Value(val key: String, val param: Param) : Property

    /** A text value built from literal text and parameters. */
    data class Format(val key: String, val template: Template) : Property

    /** The hand-written `amount(...)` block. */
    data class AmountBlock(val param: Param) : Property
}

/** Text with `{param}` slots, used for event names and fixed-format values. */
internal data class Template(val parts: List<Part>) {
    sealed interface Part {
        data class Literal(val text: String) : Part
        data class Slot(val param: Param) : Part
    }
}
