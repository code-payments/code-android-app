package com.getcode.buildlogic.analytics

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Reads `events.toml` into a [Catalogue], rejecting anything the generator would otherwise have
 * to guess at: unknown keys, types and groups, parameters nothing reads, templates naming a
 * parameter that isn't there, and duplicates.
 *
 * Keys that come from the data (enum, domain and group names) are always looked up as one-element
 * paths, since tomlj reads a plain string key as a dotted path.
 */
internal object CatalogueParser {

    private const val FILE = "events.toml"

    /** The built-in group that calls the hand-written `amount(...)` helper. */
    private const val AMOUNT_GROUP = "amount"

    /** The keys `amount(...)` writes, so a duplicate of one is caught like any other. */
    private val AMOUNT_KEYS = listOf("Fiat", "Currency", "USDC", "Quarks", "Exchange Rate", "Mint")

    private val PRIMITIVES = mapOf(
        "String" to ParamType.Text,
        "Double" to ParamType.Decimal,
        "Long" to ParamType.Whole,
        "Int" to ParamType.SmallWhole,
        "Boolean" to ParamType.Flag,
        "Amount" to ParamType.Amount,
    )

    /** Types the module already declares by hand, which an enum must not shadow. */
    private val RESERVED_TYPES = PRIMITIVES.keys + setOf("AnalyticsEvent", "PropertyValue", "PeopleCounter")

    private val KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
        "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    private val TYPE_NAME = Regex("[A-Z][A-Za-z0-9]*")
    private val CASE_NAME = Regex("[A-Z][A-Z0-9_]*")
    private val MEMBER_NAME = Regex("[a-z][A-Za-z0-9]*")
    private val PARAM = Regex("""\s*([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z_][A-Za-z0-9_]*)(\?)?\s*""")

    fun parse(text: String): Catalogue {
        val root = Toml.parse(text)
        if (root.hasErrors()) {
            fail(FILE, root.errors().joinToString("; ") { "line ${it.position().line()}: ${it.message}" })
        }
        root.requireOnly(FILE, "package", "counters", "enums", "groups", "domains")

        val packageName = root.string("package", FILE) ?: fail(FILE, "`package` is required")

        val enums = root.subtables("enums", FILE).map { (name, table) -> enumDef(name, table) }
        enums.groupBy { it.name }.forEach { (name, defs) ->
            if (defs.size > 1) fail("enums.$name", "declared more than once")
        }
        val types = PRIMITIVES + enums.associate { it.name to ParamType.Enum(it.name) }

        val counters = root.table("counters", FILE)?.let { table ->
            table.keySet().map { name -> enumCase("PeopleCounter", name, table.get(listOf(name))) }
        }.orEmpty()

        val groups = root.subtables("groups", FILE).toMap()
        groups.forEach { (name, table) ->
            val where = "groups.$name"
            if (name == AMOUNT_GROUP) fail(where, "`$AMOUNT_GROUP` is built in and cannot be redefined")
            table.requireOnly(where, "properties")
        }

        val domains = root.subtables("domains", FILE).map { (name, table) -> domain(name, table, types, groups) }

        return Catalogue(packageName, enums, counters, domains)
    }

    private fun enumDef(name: String, table: TomlTable): EnumDef {
        val where = "enums.$name"
        if (!TYPE_NAME.matches(name)) fail(where, "an enum name must be UpperCamelCase")
        if (name in RESERVED_TYPES) fail(where, "`$name` is already a type in the module")
        table.requireOnly(where, "doc", "cases")
        val cases = table.table("cases", where) ?: fail(where, "`cases` is required")
        if (cases.isEmpty) fail(where, "an enum needs at least one case")
        return EnumDef(
            name = name,
            doc = table.string("doc", where),
            cases = cases.keySet().map { case -> enumCase(name, case, cases.get(listOf(case))) },
        )
    }

    private fun enumCase(enum: String, name: String, raw: Any?): EnumCase {
        val where = "$enum.$name"
        if (!CASE_NAME.matches(name)) fail(where, "a case name must be UPPER_SNAKE_CASE")
        val (value, drift) = when (raw) {
            is String -> raw to null
            is TomlTable -> {
                raw.requireOnly(where, "value", "drift")
                raw.string("value", where) to raw.string("drift", where)
            }
            else -> fail(where, "a case is a wire string or `{ value = \"...\", drift = \"...\" }`")
        }
        if (value.isNullOrEmpty()) fail(where, "a case needs a non-empty wire string")
        return EnumCase(name, value, drift)
    }

    private fun domain(
        name: String,
        table: TomlTable,
        types: Map<String, ParamType>,
        groups: Map<String, TomlTable>,
    ): Domain {
        val where = "domains.$name"
        if (!TYPE_NAME.matches(name)) fail(where, "a domain name must be UpperCamelCase")
        table.requireOnly(where, "doc", "events")
        val entries = table.tables("events", where)
        val builders = entries.mapIndexed { index, entry -> builder(name, index, entry, types, groups) }
        builders.groupBy { it.name }.forEach { (builder, found) ->
            if (found.size > 1) fail("$name.$builder", "builder is declared more than once")
        }
        return Domain(name, table.string("doc", where), builders)
    }

    private fun builder(
        domain: String,
        index: Int,
        table: TomlTable,
        types: Map<String, ParamType>,
        groups: Map<String, TomlTable>,
    ): Builder {
        val name = table.string("builder", "$domain event #${index + 1}")
            ?: fail("$domain event #${index + 1}", "`builder` is required")
        val where = "$domain.$name"
        if (!MEMBER_NAME.matches(name) || name in KEYWORDS) fail(where, "`$name` is not a usable function name")
        table.requireOnly(where, "builder", "event", "params", "properties", "doc", "drift")

        val params = table.strings("params", where).map { param(it, where, types) }
        params.groupBy { it.name }.forEach { (param, found) ->
            if (found.size > 1) fail(where, "parameter `$param` is declared more than once")
        }
        val byName = params.associateBy { it.name }
        val used = mutableSetOf<String>()

        val eventName = table.string("event", where) ?: fail(where, "`event` is required")
        val event = template(eventName, where, byName, used)

        val properties = mutableListOf<Property>()
        table.tables("properties", where).forEach { entry ->
            expand(entry, where, byName, groups, used, properties, groupStack = emptyList())
        }
        val keys = properties.flatMap { property ->
            when (property) {
                is Property.Value -> listOf(property.key)
                is Property.Format -> listOf(property.key)
                is Property.AmountBlock -> AMOUNT_KEYS
            }
        }
        keys.groupBy { it }.forEach { (key, found) ->
            if (found.size > 1) fail(where, "property \"$key\" is sent more than once")
        }

        params.firstOrNull { it.name !in used }?.let { fail(where, "parameter `${it.name}` is never sent") }

        return Builder(
            name = name,
            event = event,
            params = params,
            properties = properties,
            doc = table.string("doc", where),
            drift = table.string("drift", where),
        )
    }

    private fun param(declaration: String, where: String, types: Map<String, ParamType>): Param {
        val match = PARAM.matchEntire(declaration)
            ?: fail(where, "parameter \"$declaration\" is not `name: Type` or `name: Type?`")
        val (name, typeName, optional) = match.destructured
        if (!MEMBER_NAME.matches(name) || name in KEYWORDS) fail(where, "`$name` is not a usable parameter name")
        val type = types[typeName] ?: fail(where, "parameter `$name` has unknown type `$typeName`")
        return Param(name, type, nullable = optional.isNotEmpty())
    }

    private fun expand(
        entry: TomlTable,
        where: String,
        params: Map<String, Param>,
        groups: Map<String, TomlTable>,
        used: MutableSet<String>,
        into: MutableList<Property>,
        groupStack: List<String>,
    ) {
        val group = entry.string("group", where)
        if (group != null) {
            entry.requireOnly(where, "group")
            when {
                group == AMOUNT_GROUP -> {
                    val param = params[AMOUNT_GROUP]
                        ?: fail(where, "the `amount` group reads a parameter `amount: Amount`, which isn't declared")
                    if (param.type != ParamType.Amount) fail(where, "the `amount` group needs `amount` to be an Amount")
                    used += param.name
                    into += Property.AmountBlock(param)
                }
                group in groupStack -> fail(where, "group `$group` includes itself")
                else -> {
                    val table = groups[group] ?: fail(where, "unknown group `$group`")
                    table.tables("properties", "groups.$group").forEach { nested ->
                        expand(nested, where, params, groups, used, into, groupStack + group)
                    }
                }
            }
            return
        }

        entry.requireOnly(where, "key", "value", "format")
        val key = entry.string("key", where) ?: fail(where, "a property needs a `key`, or a `group`")
        val value = entry.string("value", where)
        val format = entry.string("format", where)
        into += when {
            value != null && format != null -> fail(where, "property \"$key\" has both `value` and `format`")
            value != null -> {
                val param = params[value] ?: fail(where, "property \"$key\" reads `$value`, which isn't a parameter")
                if (param.type == ParamType.Amount) {
                    fail(where, "property \"$key\": an Amount is sent through the amount group, not as a property")
                }
                used += param.name
                Property.Value(key, param)
            }
            format != null -> Property.Format(key, template(format, where, params, used))
            else -> fail(where, "property \"$key\" needs a `value` or a `format`")
        }
    }

    /** Splits `Token Purchase With {method}` into literal text and parameter slots. */
    private fun template(text: String, where: String, params: Map<String, Param>, used: MutableSet<String>): Template {
        val parts = mutableListOf<Template.Part>()
        var index = 0
        while (index < text.length) {
            val open = text.indexOf('{', index)
            val close = text.indexOf('}', index)
            if (open < 0) {
                if (close >= 0) fail(where, "\"$text\" has a `}` with no `{`")
                parts += Template.Part.Literal(text.substring(index))
                break
            }
            if (close in index until open) fail(where, "\"$text\" has a `}` with no `{`")
            val end = text.indexOf('}', open)
            if (end < 0) fail(where, "\"$text\" has a `{` with no `}`")
            if (open > index) parts += Template.Part.Literal(text.substring(index, open))
            val slot = text.substring(open + 1, end)
            val param = params[slot] ?: fail(where, "\"$text\" names {$slot}, which isn't a parameter")
            if (param.nullable) fail(where, "\"$text\" names {$slot}, which is optional and would print \"null\"")
            if (param.type != ParamType.Text && param.type !is ParamType.Enum) {
                fail(where, "\"$text\" names {$slot}; only String and enum parameters can appear in text")
            }
            used += param.name
            parts += Template.Part.Slot(param)
            index = end + 1
        }
        return Template(parts)
    }

    private fun TomlTable.requireOnly(where: String, vararg allowed: String) {
        val unknown = keySet() - allowed.toSet()
        if (unknown.isNotEmpty()) {
            fail(where, "unknown key(s) ${unknown.joinToString { "`$it`" }}; expected ${allowed.joinToString { "`$it`" }}")
        }
    }

    private fun TomlTable.string(key: String, where: String): String? = when (val value = get(listOf(key))) {
        null -> null
        is String -> value
        else -> fail(where, "`$key` must be a string")
    }

    private fun TomlTable.table(key: String, where: String): TomlTable? = when (val value = get(listOf(key))) {
        null -> null
        is TomlTable -> value
        else -> fail(where, "`$key` must be a table")
    }

    private fun TomlTable.subtables(key: String, where: String): List<Pair<String, TomlTable>> {
        val parent = table(key, where) ?: return emptyList()
        return parent.keySet().map { name ->
            name to (parent.table(name, "$key.$name") ?: fail("$key.$name", "must be a table"))
        }
    }

    private fun TomlTable.array(key: String, where: String): TomlArray? = when (val value = get(listOf(key))) {
        null -> null
        is TomlArray -> value
        else -> fail(where, "`$key` must be an array")
    }

    private fun TomlTable.tables(key: String, where: String): List<TomlTable> =
        array(key, where)?.toList()?.map { it as? TomlTable ?: fail(where, "every entry in `$key` must be a table") }
            .orEmpty()

    private fun TomlTable.strings(key: String, where: String): List<String> =
        array(key, where)?.toList()?.map { it as? String ?: fail(where, "every entry in `$key` must be a string") }
            .orEmpty()

    private fun fail(where: String, message: String): Nothing =
        throw CatalogueException(if (where == FILE) "$FILE: $message" else "$FILE: $where: $message")
}
