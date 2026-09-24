package com.getcode.buildlogic.analytics

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Reads `events.toml` into a [Catalogue]. An entry describes the event in plain words and this
 * derives the Kotlin: the builder name from the event name, each parameter from a property, and
 * the parameter order from where the properties sit.
 *
 * Anything the generator would otherwise have to guess at is rejected: unknown keys, types,
 * lists and groups, names that don't make a usable function or parameter, and duplicates.
 * The people reading these messages may not know Kotlin, so each one names the event and says
 * what to change in the file.
 *
 * Keys that come from the data (list, domain and group names) are always looked up as
 * one-element paths, since tomlj reads a plain string key as a dotted path.
 */
internal object CatalogueParser {

    private const val FILE = "events.toml"

    /** The built-in group that calls the hand-written `amount(...)` helper. */
    private const val AMOUNT_GROUP = "amount"

    /**
     * The keys `amount(...)` always writes, so a duplicate of one is caught like any other. It
     * also writes `Exchange Rate` and `Mint` when the amount has them, and those two may appear
     * beside the group: the builder sends the property, and the amount's value replaces it when
     * present. `SwapEvents` relies on that for `Mint`.
     */
    private val AMOUNT_KEYS = listOf("Fiat", "Currency", "USDC", "Quarks")

    /** The plain-word property types, in the order messages list them. */
    private val TYPE_WORDS = linkedMapOf(
        "text" to ParamType.Text,
        "yes/no" to ParamType.Flag,
        "count" to ParamType.SmallWhole,
        "decimal" to ParamType.Decimal,
        "duration" to ParamType.Whole,
    )

    /** A `duration` is sent in milliseconds, and its parameter says so. */
    private const val DURATION_SUFFIX = "Millis"

    /** Types the module already declares by hand, which a list must not shadow. */
    private val RESERVED_TYPES = setOf(
        "String", "Double", "Long", "Int", "Boolean", "Amount", "AnalyticsEvent", "PropertyValue", "PeopleCounter",
    )

    private val KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
        "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    private val TYPE_NAME = Regex("[A-Z][A-Za-z0-9]*")
    private val CASE_NAME = Regex("[A-Z][A-Z0-9_]*")
    private val MEMBER_NAME = Regex("[a-z][A-Za-z0-9]*")
    private val WORD_BREAK = Regex("[^A-Za-z0-9]+")
    private val PLACEHOLDER = Regex("""\{[^{}]*}""")

    fun parse(text: String): Catalogue {
        val root = Toml.parse(text)
        if (root.hasErrors()) {
            fail(FILE, root.errors().joinToString("; ") { "line ${it.position().line()}: ${it.message}" })
        }
        root.requireOnly(FILE, "package", "lists", "counters", "groups", "domains", "event")

        val packageName = root.string("package", FILE) ?: fail(FILE, "`package` is required")

        val lists = root.subtables("lists", FILE).map { (name, table) -> list(name, table) }
        val types = Types(lists.map { it.name })

        val counters = root.table("counters", FILE)?.let { table ->
            table.keySet().map { name -> value("counters", name, table.get(listOf(name))) }
        }.orEmpty()

        val groups = root.subtables("groups", FILE).toMap()
        groups.forEach { (name, table) ->
            val where = "group \"$name\""
            if (name == AMOUNT_GROUP) fail(where, "`$AMOUNT_GROUP` is built in, so a group can't use that name")
            table.requireOnly(where, "properties")
        }

        val descriptions = root.table("domains", FILE)?.let { table ->
            table.keySet().associateWith { name -> table.string(name, "domains") }
        }.orEmpty()

        val entries = root.tables("event", FILE).mapIndexed { index, table -> entry(index, table, types, groups) }
        val byDomain = entries.groupBy { it.first }
        descriptions.keys.firstOrNull { it !in byDomain }?.let { unused ->
            fail("domains", "\"$unused\" has a description, but no event has `domain = \"$unused\"`")
        }

        val domains = byDomain.map { (domain, found) ->
            val events = found.map { it.second }
            events.groupBy { it.builder.name }.forEach { (builder, same) ->
                if (same.size > 1) {
                    fail(
                        "domain \"$domain\"",
                        same.joinToString(" and ") { "event \"${it.eventName}\"" } +
                            " would all get the builder `$builder`. To fix it, add `builder = \"...\"` " +
                            "to all but one of them, with a short name of your own such as \"${builder}Failed\"",
                    )
                }
            }
            Domain(objectName(domain), domain, descriptions[domain], events.map { it.builder })
        }

        return Catalogue(packageName, lists, counters, domains)
    }

    private fun list(name: String, table: TomlTable): EnumDef {
        val where = "list $name"
        if (!TYPE_NAME.matches(name)) fail(where, "a list name is words run together, each starting with a capital, such as \"ChatType\"")
        if (name in RESERVED_TYPES) fail(where, "`$name` is already a type in the module, so pick another list name")
        table.requireOnly(where, "description", "values")
        val values = table.table("values", where) ?: fail(where, "add a [lists.$name.values] table with the list's values")
        if (values.isEmpty) fail(where, "a list needs at least one value")
        return EnumDef(
            name = name,
            doc = table.string("description", where),
            cases = values.keySet().map { case -> value(where, case, values.get(listOf(case))) },
        )
    }

    private fun value(owner: String, name: String, raw: Any?): EnumCase {
        val where = "$owner, value $name"
        if (!CASE_NAME.matches(name)) fail(where, "a value's name is capitals and underscores, such as CHAT_GATE")
        val (wire, drift) = when (raw) {
            is String -> raw to null
            is TomlTable -> {
                raw.requireOnly(where, "value", "drift")
                raw.string("value", where) to raw.string("drift", where)
            }
            else -> fail(where, "write it as $name = \"...\" or $name = { value = \"...\", drift = \"...\" }")
        }
        if (wire.isNullOrEmpty()) fail(where, "give it the text Mixpanel receives, such as $name = \"${titleCase(name)}\"")
        return EnumCase(name, wire, drift)
    }

    /** One `[[event]]`, paired with the domain it belongs to. */
    private fun entry(index: Int, table: TomlTable, types: Types, groups: Map<String, TomlTable>): Pair<String, Entry> {
        val eventName = table.string("name", "event #${index + 1}")
            ?: fail("event #${index + 1}", "every [[event]] needs a `name`: the event name as Mixpanel shows it")
        val where = "event \"$eventName\""
        table.requireOnly(where, "domain", "name", "builder", "placeholders", "properties", "order", "description", "drift")
        val domain = table.string("domain", where)
            ?: fail(where, "add `domain = \"...\"`, the part of the app it belongs to, such as \"Transfer\"")
        if (words(domain).isEmpty() || !objectName(domain).let(TYPE_NAME::matches)) {
            fail(where, "`domain = \"$domain\"` must be words, such as \"Add Money\"")
        }

        val placeholderTypes = table.table("placeholders", where)?.let { placeholders ->
            placeholders.keySet().associateWith { name ->
                val word = placeholders.string(name, where)!!
                val type = types.resolve(word, where, "placeholder {$name}")
                if (type != ParamType.Text && type !is ParamType.Enum) {
                    fail(where, "{$name} can only be text or a list, since it's written into the name")
                }
                type
            }
        }.orEmpty()

        val params = Params(where)
        val usedPlaceholders = mutableSetOf<String>()
        fun slots(text: String) = template(text, where, placeholderTypes, params, usedPlaceholders)

        val event = slots(eventName)
        val properties = mutableListOf<Property>()
        table.tables("properties", where).forEach { property ->
            expand(property, where, types, groups, params, ::slots, properties, groupStack = emptyList())
        }
        (placeholderTypes.keys - usedPlaceholders).firstOrNull()?.let { unused ->
            fail(where, "`placeholders` gives a type for {$unused}, but neither the name nor a format has {$unused}")
        }

        val keys = properties.flatMap { property ->
            when (property) {
                is Property.Value -> listOf(property.key)
                is Property.Format -> listOf(property.key)
                is Property.AmountBlock -> AMOUNT_KEYS
            }
        }
        keys.groupBy { it }.forEach { (key, found) ->
            if (found.size > 1) {
                val why = if (key in AMOUNT_KEYS) " (the amount group already sends \"$key\")" else ""
                fail(where, "the property \"$key\" is sent more than once$why. Remove one of them")
            }
        }

        val builderName = table.string("builder", where) ?: derivedBuilder(eventName, where)
        if (!MEMBER_NAME.matches(builderName) || builderName in KEYWORDS) {
            fail(where, "`builder = \"$builderName\"` must be one word, or words run together, starting lowercase, such as \"opened\"")
        }

        val ordered = table.array("order", where)?.let { order(it, where, params.all) } ?: params.all

        val builder = Builder(
            name = builderName,
            event = event,
            params = ordered,
            properties = properties,
            doc = table.string("description", where),
            drift = table.string("drift", where),
        )
        return domain to Entry(eventName, builder)
    }

    private class Entry(val eventName: String, val builder: Builder)

    /** The event name in camelCase, without the `Domain:` prefix and the `{placeholders}`. */
    private fun derivedBuilder(eventName: String, where: String): String {
        val name = camelCase(eventName.substringAfter(':').replace(PLACEHOLDER, " "))
        if (name.isEmpty() || !MEMBER_NAME.matches(name) || name in KEYWORDS) {
            fail(where, "no builder name can be made from this event name, so add `builder = \"...\"`, a short name such as \"opened\"")
        }
        return name
    }

    private fun order(order: TomlArray, where: String, params: List<Param>): List<Param> {
        val names = order.toList().map { it as? String ?: fail(where, "`order` is a list of parameter names in quotes") }
        val byName = params.associateBy { it.name }
        val missing = params.map { it.name }.filter { it !in names }
        val unknown = names.filter { it !in byName }
        val twice = names.groupBy { it }.filterValues { it.size > 1 }.keys
        if (missing.isNotEmpty() || unknown.isNotEmpty() || twice.isNotEmpty()) {
            val problems = listOfNotNull(
                missing.takeIf { it.isNotEmpty() }?.let { "missing ${it.joinToString()}" },
                unknown.takeIf { it.isNotEmpty() }?.let { "${it.joinToString()} isn't a parameter" },
                twice.takeIf { it.isNotEmpty() }?.let { "${it.joinToString()} is listed twice" },
            )
            fail(
                where,
                "`order` must list every parameter once: ${params.joinToString { it.name }}. " +
                    "Right now it's ${problems.joinToString("; ")}",
            )
        }
        return names.map(byName::getValue)
    }

    private fun expand(
        entry: TomlTable,
        where: String,
        types: Types,
        groups: Map<String, TomlTable>,
        params: Params,
        template: (String) -> Template,
        into: MutableList<Property>,
        groupStack: List<String>,
    ) {
        val group = entry.string("group", where)
        if (group != null) {
            entry.requireOnly(where, "group", "optional")
            val optional = entry.boolean("optional", where)
            when {
                group == AMOUNT_GROUP -> {
                    val param = params.add(Param(AMOUNT_GROUP, ParamType.Amount, optional), "the amount group")
                    into += Property.AmountBlock(param)
                }
                optional -> fail(where, "only the amount group can be `optional`; mark the properties inside group \"$group\" instead")
                group in groupStack -> fail(where, "group \"$group\" includes itself")
                else -> {
                    val table = groups[group]
                        ?: fail(where, "there's no group \"$group\". Groups: ${(groups.keys + AMOUNT_GROUP).joinToString()}")
                    table.tables("properties", "group \"$group\"").forEach { nested ->
                        expand(nested, where, types, groups, params, template, into, groupStack + group)
                    }
                }
            }
            return
        }

        entry.requireOnly(where, "name", "type", "optional", "param", "format")
        val key = entry.string("name", where)
            ?: fail(where, "every property needs a `name`, the key Mixpanel shows, or a `group`")
        val format = entry.string("format", where)
        if (format != null) {
            if (entry.keySet().any { it in setOf("type", "optional", "param") }) {
                fail(where, "the property \"$key\" has a `format`, so it's always text and can't have `type`, `optional` or `param`")
            }
            into += Property.Format(key, template(format))
            return
        }

        val word = entry.string("type", where)
            ?: fail(where, "the property \"$key\" needs a `type`: ${TYPE_WORDS.keys.joinToString()}, or a list name")
        val type = types.resolve(word, where, "the property \"$key\"")
        val name = entry.string("param", where)
            ?: (camelCase(key) + if (type == ParamType.Whole) DURATION_SUFFIX else "")
        val param = params.add(Param(name, type, entry.boolean("optional", where)), "the property \"$key\"")
        into += Property.Value(key, param)
    }

    /**
     * Splits `Token Purchase With {method}` into literal text and parameter slots. A placeholder
     * is a text parameter unless the entry's `placeholders` gives it a list, and one that appears
     * twice reads the same parameter.
     */
    private fun template(
        text: String,
        where: String,
        placeholderTypes: Map<String, ParamType>,
        params: Params,
        used: MutableSet<String>,
    ): Template {
        val parts = mutableListOf<Template.Part>()
        var index = 0
        while (index < text.length) {
            val open = text.indexOf('{', index)
            val close = text.indexOf('}', index)
            if (open < 0) {
                if (close >= 0) fail(where, "\"$text\" has a `}` with no `{` before it")
                parts += Template.Part.Literal(text.substring(index))
                break
            }
            if (close in index until open) fail(where, "\"$text\" has a `}` with no `{` before it")
            val end = text.indexOf('}', open)
            if (end < 0) fail(where, "\"$text\" has a `{` with no `}` after it")
            if (open > index) parts += Template.Part.Literal(text.substring(index, open))
            val slot = text.substring(open + 1, end)
            if (!MEMBER_NAME.matches(slot) || slot in KEYWORDS) {
                fail(where, "{$slot} in \"$text\" must be one word, or words run together, starting lowercase, such as {method}")
            }
            val param = if (slot in used) {
                params.find(slot)!!
            } else {
                params.add(Param(slot, placeholderTypes[slot] ?: ParamType.Text, false), "{$slot}")
            }
            used += slot
            parts += Template.Part.Slot(param)
            index = end + 1
        }
        return Template(parts)
    }

    /** Collects an entry's parameters in the order its name and properties produce them. */
    private class Params(private val where: String) {
        val all = mutableListOf<Param>()

        fun find(name: String) = all.firstOrNull { it.name == name }

        fun add(param: Param, source: String): Param {
            if (!MEMBER_NAME.matches(param.name) || param.name in KEYWORDS) {
                fail(where, "$source can't be passed as `${param.name}`, so add `param = \"...\"`, a name such as \"choice\"")
            }
            find(param.name)?.let {
                fail(where, "two properties would both be passed as `${param.name}`, so add `param = \"...\"` to one of them")
            }
            all += param
            return param
        }
    }

    /** The type words and the catalogue's lists. */
    private class Types(private val lists: List<String>) {
        fun resolve(word: String, where: String, subject: String): ParamType {
            TYPE_WORDS[word]?.let { return it }
            if (word in lists) return ParamType.Enum(word)
            val known = "The types are ${TYPE_WORDS.keys.joinToString()}, or one of the lists: " +
                lists.joinToString().ifEmpty { "(none yet)" }
            if (word.firstOrNull()?.isUpperCase() == true) {
                fail(where, "$subject uses the list \"$word\", which doesn't exist. Add it as [lists.$word], or check the spelling. $known")
            }
            fail(where, "$subject has the type \"$word\", which isn't a type. $known")
        }
    }

    private fun words(text: String) = text.split(WORD_BREAK).filter { it.isNotEmpty() }

    /** `CHAT_GATE` → `Chat Gate`, as an example wire string. */
    private fun titleCase(name: String) =
        words(name).joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercaseChar) }

    /** `Owner Public Key` → `ownerPublicKey`, `URL` → `url`. */
    private fun camelCase(text: String) = words(text).mapIndexed { index, word ->
        val lower = word.lowercase()
        if (index == 0) lower else lower.replaceFirstChar(Char::uppercaseChar)
    }.joinToString("")

    /** `Add Money` → `AddMoneyEvents`; a domain already run together keeps its capitals. */
    private fun objectName(domain: String) =
        words(domain).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } + "Events"

    private fun TomlTable.requireOnly(where: String, vararg allowed: String) {
        val unknown = keySet() - allowed.toSet()
        if (unknown.isNotEmpty()) {
            fail(
                where,
                "${unknown.joinToString { "`$it`" }} isn't something this entry can have. " +
                    "Check the spelling; it can have ${allowed.joinToString { "`$it`" }}",
            )
        }
    }

    private fun TomlTable.string(key: String, where: String): String? = when (val value = get(listOf(key))) {
        null -> null
        is String -> value
        else -> fail(where, "`$key` must be text in quotes")
    }

    private fun TomlTable.boolean(key: String, where: String): Boolean = when (val value = get(listOf(key))) {
        null -> false
        is Boolean -> value
        else -> fail(where, "`$key` must be true or false, without quotes")
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
        else -> fail(where, "`$key` must be a list in square brackets")
    }

    private fun TomlTable.tables(key: String, where: String): List<TomlTable> =
        array(key, where)?.toList()?.map { it as? TomlTable ?: fail(where, "every entry in `$key` must be { ... }") }
            .orEmpty()

    private fun fail(where: String, message: String): Nothing =
        throw CatalogueException(if (where == FILE) "$FILE: $message" else "$FILE: $where: $message")
}
