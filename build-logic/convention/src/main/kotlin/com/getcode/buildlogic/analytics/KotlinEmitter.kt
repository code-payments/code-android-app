package com.getcode.buildlogic.analytics

/**
 * Writes a [Catalogue] as Kotlin, in the shapes the module's builders were first written in by
 * hand: one `enum class X(val value: String)` per value enum, `PeopleCounter`, and one
 * `object XEvents` per domain whose builders call the hand-written `event(name) { ... }` DSL.
 *
 * Output is keyed by path relative to the source root. Enums go in the catalogue's package and
 * builder objects in its `events` subpackage.
 */
internal object KotlinEmitter {

    private const val HEADER = "// Generated from events.toml -- do not edit."
    private const val INDENT = "    "
    private const val LINE_LIMIT = 100

    fun emit(catalogue: Catalogue): Map<String, String> {
        val base = catalogue.packageName
        val eventsPackage = "$base.events"
        val files = linkedMapOf<String, String>()

        catalogue.enums.forEach { enum ->
            files[path(base, enum.name)] = enumFile(base, enum.name, "value", enum.doc, enum.cases)
        }
        if (catalogue.counters.isNotEmpty()) {
            files[path(base, "PeopleCounter")] = enumFile(
                packageName = base,
                name = "PeopleCounter",
                property = "key",
                doc = "Cumulative per-user counters, stored as Mixpanel people properties.",
                cases = catalogue.counters,
            )
        }
        catalogue.domains.forEach { domain ->
            files[path(eventsPackage, domain.name)] = domainFile(base, eventsPackage, domain)
        }
        return files
    }

    private fun path(packageName: String, type: String) = packageName.replace('.', '/') + "/$type.kt"

    private fun enumFile(packageName: String, name: String, property: String, doc: String?, cases: List<EnumCase>) =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine(HEADER)
            appendLine()
            append(kdoc(listOfNotNull(doc), indent = ""))
            appendLine("enum class $name(val $property: String) {")
            cases.forEachIndexed { index, case ->
                val documented = case.drift != null
                val afterDocumented = index > 0 && cases[index - 1].drift != null
                if (index > 0 && (documented || afterDocumented)) appendLine()
                append(kdoc(listOfNotNull(case.drift?.let { "DRIFT: $it" }), INDENT))
                appendLine("$INDENT${case.name}(${literal(case.value)}),")
            }
            appendLine("}")
        }

    private fun domainFile(base: String, packageName: String, domain: Domain) = buildString {
        val imports = sortedSetOf("$base.AnalyticsEvent", "$base.event")
        domain.builders.forEach { builder ->
            builder.params.forEach { param ->
                when (val type = param.type) {
                    is ParamType.Enum -> imports += "$base.${type.name}"
                    ParamType.Amount -> imports += "$base.Amount"
                    else -> Unit
                }
            }
            if (builder.properties.any { it is Property.AmountBlock }) imports += "$base.amount"
        }

        appendLine("package $packageName")
        appendLine()
        imports.forEach { appendLine("import $it") }
        appendLine()
        appendLine(HEADER)
        appendLine()
        append(kdoc(listOfNotNull(domain.doc), indent = ""))
        appendLine("object ${domain.name} {")
        domain.builders.forEachIndexed { index, builder ->
            if (index > 0) appendLine()
            append(builder(builder))
        }
        appendLine("}")
    }

    private fun builder(builder: Builder) = buildString {
        append(kdoc(listOfNotNull(builder.doc, builder.drift?.let { "DRIFT: $it" }), INDENT))
        val params = builder.params.joinToString { "${it.name}: ${it.kotlinType}" }
        val call = "event(${template(builder.event)})"
        if (builder.properties.isEmpty()) {
            appendLine("${INDENT}fun ${builder.name}($params): AnalyticsEvent = $call")
            return@buildString
        }
        appendLine("${INDENT}fun ${builder.name}($params): AnalyticsEvent =")
        appendLine("$INDENT$INDENT$call {")
        builder.properties.forEach { appendLine("$INDENT$INDENT$INDENT${property(it)}") }
        appendLine("$INDENT$INDENT}")
    }

    private fun property(property: Property): String = when (property) {
        is Property.AmountBlock -> "amount(${property.param.name})"
        is Property.Format -> "text(${literal(property.key)}, ${template(property.template)})"
        is Property.Value -> {
            val param = property.param
            val access = if (param.nullable) "?." else "."
            val key = literal(property.key)
            when (param.type) {
                ParamType.Text -> "text($key, ${param.name})"
                is ParamType.Enum -> "text($key, ${param.name}${access}value)"
                ParamType.Decimal, ParamType.Whole -> "number($key, ${param.name})"
                ParamType.SmallWhole -> "number($key, ${param.name}${access}toDouble())"
                ParamType.Flag -> "flag($key, ${param.name})"
                ParamType.Amount -> error("the parser sends an Amount only through the amount group")
            }
        }
    }

    private fun template(template: Template): String = template.parts.joinToString("", "\"", "\"") { part ->
        when (part) {
            is Template.Part.Literal -> escape(part.text)
            is Template.Part.Slot -> {
                val param = part.param
                if (param.type is ParamType.Enum) "\${${param.name}.value}" else "\${${param.name}}"
            }
        }
    }

    private fun literal(text: String) = "\"${escape(text)}\""

    private fun escape(text: String) = buildString(text.length) {
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    /**
     * One paragraph that fits goes on one line; otherwise each paragraph is word-wrapped at
     * [LINE_LIMIT] with a blank line between. A blank line inside a paragraph's text starts a
     * new paragraph, so a catalogue entry can carry an `@param` line of its own.
     */
    private fun kdoc(paragraphs: List<String>, indent: String): String {
        val blocks = paragraphs
            .flatMap { it.split(Regex("""\n\s*\n""")) }
            .map { it.trim().replace(Regex("""\s+"""), " ").replace("*/", "* /") }
            .filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return ""
        if (blocks.size == 1) {
            val single = "$indent/** ${blocks[0]} */"
            if (single.length <= LINE_LIMIT) return single + "\n"
        }

        val prefix = "$indent * "
        return buildString {
            appendLine("$indent/**")
            blocks.forEachIndexed { index, block ->
                if (index > 0) appendLine("$indent *")
                wrap(block, LINE_LIMIT - prefix.length).forEach { appendLine(prefix + it) }
            }
            appendLine("$indent */")
        }
    }

    private fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        text.split(' ').forEach { word ->
            if (line.isNotEmpty() && line.length + 1 + word.length > width) {
                lines += line.toString()
                line.clear()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }
}
