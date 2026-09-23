package com.getcode.buildlogic.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnalyticsCatalogueTest {

    private fun generate(toml: String): Map<String, String> =
        AnalyticsCatalogue.emit(AnalyticsCatalogue.parse("package = \"com.example\"\n$toml"))

    private fun rejected(toml: String): String =
        assertFailsWith<CatalogueException> { generate(toml) }.message.orEmpty()

    @Test
    fun valueEnumsKeepCaseOrderAndCarryDriftAsKDoc() {
        val source = generate(
            """
            [enums.Method]
            doc = "How the user paid."
            [enums.Method.cases]
            ZULU = "Zulu"
            ALPHA = { value = "Alpha", drift = "iOS has no Alpha." }
            """.trimIndent(),
        ).getValue("com/example/Method.kt")

        assertEquals(
            """
            package com.example

            // Generated from events.toml -- do not edit.

            /** How the user paid. */
            enum class Method(val value: String) {
                ZULU("Zulu"),

                /** DRIFT: iOS has no Alpha. */
                ALPHA("Alpha"),
            }

            """.trimIndent(),
            source,
        )
    }

    @Test
    fun eventMapsEachParameterTypeToItsPropertyCall() {
        val source = generate(
            """
            [enums.Kind.cases]
            ONE = "One"

            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["kind: Kind", "label: String", "rate: Double", "millis: Long", "tier: Int", "done: Boolean"]
            properties = [
              { key = "Kind", value = "kind" },
              { key = "Label", value = "label" },
              { key = "Rate", value = "rate" },
              { key = "Time", value = "millis" },
              { key = "Tier", value = "tier" },
              { key = "Done", value = "done" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertEquals(
            """
            package com.example.events

            import com.example.AnalyticsEvent
            import com.example.Kind
            import com.example.event

            // Generated from events.toml -- do not edit.

            object ThingEvents {
                fun happened(kind: Kind, label: String, rate: Double, millis: Long, tier: Int, done: Boolean): AnalyticsEvent =
                    event("Thing Happened") {
                        text("Kind", kind.value)
                        text("Label", label)
                        number("Rate", rate)
                        number("Time", millis)
                        number("Tier", tier.toDouble())
                        flag("Done", done)
                    }
            }

            """.trimIndent(),
            source,
        )
    }

    @Test
    fun optionalPropertiesPassNullThroughSoTheBuilderOmitsThem() {
        val source = generate(
            """
            [enums.Kind.cases]
            ONE = "One"

            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["kind: Kind?", "error: String?", "tier: Int?"]
            properties = [
              { key = "Kind", value = "kind" },
              { key = "Error", value = "error" },
              { key = "Tier", value = "tier" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "fun happened(kind: Kind?, error: String?, tier: Int?): AnalyticsEvent =")
        assertContains(source, "text(\"Kind\", kind?.value)")
        assertContains(source, "text(\"Error\", error)")
        assertContains(source, "number(\"Tier\", tier?.toDouble())")
    }

    @Test
    fun anEventWithNoPropertiesHasNoBlock() {
        val source = generate(
            """
            [[domains.ThingEvents.events]]
            builder = "started"
            event = "Thing Started"
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "    fun started(): AnalyticsEvent = event(\"Thing Started\")\n")
    }

    @Test
    fun groupsExpandInPlaceAndTheAmountGroupCallsTheHandWrittenHelper() {
        val source = generate(
            """
            [enums.State.cases]
            SUCCESS = "Success"

            [groups.outcome]
            properties = [
              { key = "State", value = "state" },
              { group = "amount" },
              { key = "Error", value = "error" },
            ]

            [[domains.ThingEvents.events]]
            builder = "grabbed"
            event = "Grabbed"
            params = ["state: State", "amount: Amount?", "millis: Long?", "error: String?"]
            properties = [
              { key = "Time", value = "millis" },
              { group = "outcome" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "import com.example.Amount\n")
        assertContains(source, "import com.example.amount\n")
        assertContains(
            source,
            """
                fun grabbed(state: State, amount: Amount?, millis: Long?, error: String?): AnalyticsEvent =
                    event("Grabbed") {
                        number("Time", millis)
                        text("State", state.value)
                        amount(amount)
                        text("Error", error)
                    }
            """.trimIndent().prependIndent("    "),
        )
    }

    @Test
    fun nameTemplatesInterpolateEnumWireValues() {
        val source = generate(
            """
            [enums.Method.cases]
            RESERVES = "Reserves"

            [[domains.ThingEvents.events]]
            builder = "purchase"
            event = "Token Purchase With {method}"
            params = ["method: Method"]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "fun purchase(method: Method): AnalyticsEvent = event(\"Token Purchase With \${method.value}\")")
    }

    @Test
    fun fixedFormatValuesInterpolateAndEscapeLiterals() {
        val source = generate(
            """
            [[domains.ThingEvents.events]]
            builder = "parseFailed"
            event = "Deeplink: Parse"
            params = ["url: String"]
            properties = [{ key = "Error", format = "Failed to parse ${'$'}deeplink \"=>\" {url}" }]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, """text("Error", "Failed to parse \${'$'}deeplink \"=>\" ${'$'}{url}")""")
    }

    @Test
    fun twoBuildersMaySendOneEventName() {
        val source = generate(
            """
            [[domains.ThingEvents.events]]
            builder = "parsed"
            event = "Deeplink: Parse"
            params = ["type: String"]
            properties = [{ key = "Type", value = "type" }]

            [[domains.ThingEvents.events]]
            builder = "parseFailed"
            event = "Deeplink: Parse"
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "fun parsed(type: String): AnalyticsEvent =")
        assertContains(source, "fun parseFailed(): AnalyticsEvent = event(\"Deeplink: Parse\")")
    }

    @Test
    fun docAndDriftBecomeKDocWrappedAtTheLineLimit() {
        val source = generate(
            """
            [domains.ThingEvents]
            doc = "Things."

            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            doc = "The user had no display name before this submission."
            drift = "iOS sends Exchange Rate and no USDC, and fires on the request rather than after connecting to the wallet."
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(
            source,
            """
            /** Things. */
            object ThingEvents {
                /**
                 * The user had no display name before this submission.
                 *
                 * DRIFT: iOS sends Exchange Rate and no USDC, and fires on the request rather than after
                 * connecting to the wallet.
                 */
                fun happened(): AnalyticsEvent = event("Thing Happened")
            """.trimIndent(),
        )
    }

    @Test
    fun peopleCountersBecomeAKeyedEnum() {
        val source = generate(
            """
            [counters]
            TIPS = "Tips Received"
            MESSAGES = "Messages Received"
            """.trimIndent(),
        ).getValue("com/example/PeopleCounter.kt")

        assertContains(
            source,
            """
            /** Cumulative per-user counters, stored as Mixpanel people properties. */
            enum class PeopleCounter(val key: String) {
                TIPS("Tips Received"),
                MESSAGES("Messages Received"),
            }
            """.trimIndent(),
        )
    }

    @Test
    fun anEmptyCatalogueEmitsNothing() {
        assertEquals(emptyMap(), generate(""))
    }

    // Rejections. Each message names the entry at fault.

    @Test
    fun rejectsTomlSyntaxErrors() {
        assertContains(rejected("[enums.Method\n"), "events.toml")
    }

    @Test
    fun rejectsAnUnknownParameterType() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["kind: Knid"]
            properties = [{ key = "Kind", value = "kind" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "Knid")
    }

    @Test
    fun rejectsADuplicateBuilderInADomain() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"

            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened Again"
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "more than once")
    }

    @Test
    fun rejectsATemplateNamingAMissingParameter() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "purchase"
            event = "Token Purchase With {method}"
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.purchase")
        assertContains(message, "{method}")
    }

    @Test
    fun rejectsAFormatNamingAMissingParameter() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "parseFailed"
            event = "Deeplink: Parse"
            properties = [{ key = "Error", format = "Failed => {url}" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.parseFailed")
        assertContains(message, "{url}")
    }

    @Test
    fun rejectsAnEnumCaseWithNoWireString() {
        val empty = rejected("[enums.Method.cases]\nRESERVES = \"\"")
        assertContains(empty, "Method.RESERVES")

        val missing = rejected("[enums.Method.cases]\nRESERVES = { drift = \"iOS has none.\" }")
        assertContains(missing, "Method.RESERVES")
    }

    @Test
    fun rejectsAPropertyReadingAnUndeclaredParameter() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            properties = [{ key = "Label", value = "label" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "label")
    }

    @Test
    fun rejectsAParameterNoPropertyReads() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["label: String"]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "label")
    }

    @Test
    fun rejectsAnUnknownKeySoATypoIsNotSilentlyIgnored() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            drfit = "iOS sends nothing."
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "drfit")
    }

    @Test
    fun rejectsAnUnknownGroup() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            properties = [{ group = "outcom" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "outcom")
    }

    @Test
    fun rejectsAnAmountSentAsAPlainProperty() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["amount: Amount"]
            properties = [{ key = "Amount", value = "amount" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "amount group")
    }

    @Test
    fun rejectsTheSameKeyTwiceInOneEvent() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "happened"
            event = "Thing Happened"
            params = ["a: String", "b: String"]
            properties = [{ key = "Label", value = "a" }, { key = "Label", value = "b" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.happened")
        assertContains(message, "Label")
    }

    @Test
    fun rejectsANullableParameterInATemplate() {
        val message = rejected(
            """
            [[domains.ThingEvents.events]]
            builder = "parseFailed"
            event = "Deeplink: Parse"
            params = ["url: String?"]
            properties = [{ key = "Error", format = "Failed => {url}" }]
            """.trimIndent(),
        )
        assertContains(message, "ThingEvents.parseFailed")
        assertContains(message, "url")
    }

    private fun assertContains(actual: String, expected: String) =
        assertTrue(expected in actual, "expected to find:\n$expected\n\nin:\n$actual")
}
