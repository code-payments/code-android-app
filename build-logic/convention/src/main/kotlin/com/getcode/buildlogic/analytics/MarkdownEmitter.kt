package com.getcode.buildlogic.analytics

/**
 * Writes a [Catalogue] as `EVENTS.md`: the same catalogue as `events.toml`, for readers who
 * don't read TOML. Types are named with the catalogue's own words, so a reader can copy one
 * back into an entry.
 */
internal object MarkdownEmitter {

    /** What `amount(...)` in the hand-written `Amount.kt` sends, in its order. */
    private val AMOUNT_ROWS = listOf(
        Triple("Fiat", "decimal", false),
        Triple("Currency", "text", false),
        Triple("USDC", "decimal", false),
        Triple("Quarks", "count", false),
        Triple("Exchange Rate", "decimal", true),
        Triple("Mint", "text", true),
    )

    fun emit(catalogue: Catalogue): String = buildString {
        appendLine(
            """
            # Analytics events

            <!-- Generated from events.toml -- do not edit. After changing events.toml, run
                 ./gradlew :libs:analytics-events:writeAnalyticsEventsPage -->

            Every event both apps can send to Mixpanel, and the properties each one carries. To add
            or change an event, edit [events.toml](events.toml) in a pull request.

            Types: **text** is words, **yes/no** is true or false, **count** is a whole number,
            **decimal** is a number with a fraction, **duration** is milliseconds, and a **list**
            is one value from the [Lists](#lists) below. An optional property is left out when the
            app has no value for it.
            """.trimIndent(),
        )

        catalogue.domains.forEach { domain ->
            appendLine()
            appendLine("## ${domain.title}")
            domain.doc?.let { appendLine(); appendLine(it) }
            // Two builders may send one name; they share a heading so the name has one anchor.
            domain.builders.groupBy { name(it.event) }.forEach { (name, forms) -> event(name, forms) }
        }

        val usesAmount = catalogue.domains.any { domain ->
            domain.builders.any { builder -> builder.properties.any { it is Property.AmountBlock } }
        }
        if (usesAmount) {
            appendLine()
            appendLine("## Groups")
            appendLine()
            appendLine("### amount")
            appendLine()
            appendLine("Sent by events that carry an amount of money. Defined in `Amount.kt`.")
            appendLine()
            appendLine("| Property | Type | Optional |")
            appendLine("|---|---|---|")
            AMOUNT_ROWS.forEach { (key, type, optional) -> appendLine(tableRow(key, type, yesNo(optional))) }
        }

        if (catalogue.enums.isNotEmpty()) {
            appendLine()
            appendLine("## Lists")
            catalogue.enums.forEach { list ->
                appendLine()
                appendLine("### ${list.name}")
                list.doc?.let { appendLine(); appendLine(it) }
                appendLine()
                appendLine("| Value sent | Name in code | Drift |")
                appendLine("|---|---|---|")
                list.cases.forEach { case ->
                    appendLine(tableRow(cell(case.value), "`${case.name}`", cell(case.drift.orEmpty())))
                }
            }
        }

        if (catalogue.counters.isNotEmpty()) {
            appendLine()
            appendLine("## People counters")
            appendLine()
            appendLine("Cumulative per-user counters, stored as Mixpanel people properties.")
            appendLine()
            appendLine("| Counter | Name in code |")
            appendLine("|---|---|")
            catalogue.counters.forEach { appendLine(tableRow(cell(it.value), "`${it.name}`")) }
        }
    }

    private fun StringBuilder.event(name: String, forms: List<Builder>) {
        appendLine()
        appendLine("### $name")
        if (forms.size > 1) {
            appendLine()
            appendLine("Sent in ${forms.size} forms.")
        }
        forms.forEach { form(it) }
    }

    private fun StringBuilder.form(builder: Builder) {
        builder.doc?.let { appendLine(); appendLine(it) }

        val listSlots = builder.event.parts
            .filterIsInstance<Template.Part.Slot>()
            .filter { it.param.type is ParamType.Enum }
        listSlots.forEach { slot ->
            val list = (slot.param.type as ParamType.Enum).name
            appendLine()
            appendLine("`{${slot.param.name}}` in the name is one of [$list](#${anchor(list)}).")
        }

        appendLine()
        if (builder.properties.isEmpty()) {
            appendLine("No properties.")
        } else {
            appendLine("| Property | Type | Optional | Values |")
            appendLine("|---|---|---|---|")
            builder.properties.forEach { appendLine(row(it)) }
        }

        builder.drift?.let { appendLine(); appendLine("> **Drift:** $it") }
    }

    private fun row(property: Property): String = when (property) {
        is Property.AmountBlock ->
            tableRow("amount", "group", yesNo(property.param.nullable), "[amount](#amount)")
        is Property.Format ->
            tableRow(cell(property.key), "text", "no", "`${cell(name(property.template))}`")
        is Property.Value -> {
            val param = property.param
            val values = (param.type as? ParamType.Enum)?.let { "[${it.name}](#${anchor(it.name)})" }.orEmpty()
            tableRow(cell(property.key), word(param.type), yesNo(param.nullable), values)
        }
    }

    private fun word(type: ParamType): String = when (type) {
        ParamType.Text -> "text"
        ParamType.Flag -> "yes/no"
        ParamType.SmallWhole -> "count"
        ParamType.Decimal -> "decimal"
        ParamType.Whole -> "duration"
        is ParamType.Enum -> "list"
        ParamType.Amount -> error("the parser sends an Amount only through the amount group")
    }

    /** The template as the catalogue writes it, with `{placeholder}` slots. */
    private fun name(template: Template) = template.parts.joinToString("") { part ->
        when (part) {
            is Template.Part.Literal -> part.text
            is Template.Part.Slot -> "{${part.param.name}}"
        }
    }

    /** An empty cell is written `|` alone, without the padding a filled one gets. */
    private fun tableRow(vararg cells: String) =
        "|" + cells.joinToString("") { if (it.isEmpty()) " |" else " $it |" }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    /** GitHub's heading anchor for a list name, which is a plain identifier. */
    private fun anchor(name: String) = name.lowercase()

    private fun cell(text: String) = text.replace("|", "\\|").replace("\n", " ")
}
