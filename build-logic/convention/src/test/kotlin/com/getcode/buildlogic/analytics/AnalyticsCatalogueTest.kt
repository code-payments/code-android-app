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
    fun listsKeepValueOrderAndCarryDescriptionAndDriftAsKDoc() {
        val source = generate(
            """
            [lists.Method]
            description = "How the user paid."
            [lists.Method.values]
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
    fun eachTypeWordBecomesAParameterAndItsPropertyCall() {
        val source = generate(
            """
            [lists.Kind.values]
            ONE = "One"

            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [
              { name = "Kind", type = "Kind" },
              { name = "Label", type = "text" },
              { name = "Rate", type = "decimal" },
              { name = "Time", type = "duration" },
              { name = "Tier", type = "count" },
              { name = "Done", type = "yes/no" },
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
                fun thingHappened(kind: Kind, label: String, rate: Double, timeMillis: Long, tier: Int, done: Boolean): AnalyticsEvent =
                    event("Thing Happened") {
                        text("Kind", kind.value)
                        text("Label", label)
                        number("Rate", rate)
                        number("Time", timeMillis)
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
            [lists.Kind.values]
            ONE = "One"

            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [
              { name = "Kind", type = "Kind", optional = true },
              { name = "Error", type = "text", optional = true },
              { name = "Tier", type = "count", optional = true },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "fun thingHappened(kind: Kind?, error: String?, tier: Int?): AnalyticsEvent =")
        assertContains(source, "text(\"Kind\", kind?.value)")
        assertContains(source, "text(\"Error\", error)")
        assertContains(source, "number(\"Tier\", tier?.toDouble())")
    }

    @Test
    fun anEventWithNoPropertiesHasNoBlock() {
        val source = generate(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Started"
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "    fun thingStarted(): AnalyticsEvent = event(\"Thing Started\")\n")
    }

    @Test
    fun theBuilderNameDropsTheDomainPrefixAndTheDomainNamesTheObject() {
        val files = generate(
            """
            [[event]]
            domain = "Add Money"
            name = "Add Money: Method Selected"
            """.trimIndent(),
        )

        val source = files.getValue("com/example/events/AddMoneyEvents.kt")
        assertContains(source, "object AddMoneyEvents {")
        assertContains(source, "fun methodSelected(): AnalyticsEvent = event(\"Add Money: Method Selected\")")
    }

    @Test
    fun theParameterNameIsTheKeyInCamelCase() {
        val source = generate(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [
              { name = "URL", type = "text" },
              { name = "Call Site", type = "text" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "fun thingHappened(url: String, callSite: String): AnalyticsEvent =")
        assertContains(source, "text(\"Call Site\", callSite)")
    }

    @Test
    fun builderParamAndOrderOverrideWhatIsDerived() {
        val source = generate(
            """
            [[event]]
            domain = "Account"
            name = "Create Account Payment"
            builder = "payment"
            order = ["owner", "price"]
            properties = [
              { name = "Fiat", type = "decimal", param = "price" },
              { name = "Owner Public Key", type = "text", param = "owner" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/AccountEvents.kt")

        assertContains(
            source,
            """
                fun payment(owner: String, price: Double): AnalyticsEvent =
                    event("Create Account Payment") {
                        number("Fiat", price)
                        text("Owner Public Key", owner)
                    }
            """.trimIndent().prependIndent("    "),
        )
    }

    @Test
    fun groupsExpandInPlaceAndTheAmountGroupCallsTheHandWrittenHelper() {
        val source = generate(
            """
            [lists.State.values]
            SUCCESS = "Success"

            [groups.outcome]
            properties = [
              { name = "State", type = "State" },
              { group = "amount", optional = true },
              { name = "Error", type = "text", optional = true },
            ]

            [[event]]
            domain = "Thing"
            name = "Grabbed"
            properties = [
              { name = "Grab Time", type = "duration", optional = true },
              { group = "outcome" },
            ]
            """.trimIndent(),
        ).getValue("com/example/events/ThingEvents.kt")

        assertContains(source, "import com.example.Amount\n")
        assertContains(source, "import com.example.amount\n")
        assertContains(
            source,
            """
                fun grabbed(grabTimeMillis: Long?, state: State, amount: Amount?, error: String?): AnalyticsEvent =
                    event("Grabbed") {
                        number("Grab Time", grabTimeMillis)
                        text("State", state.value)
                        amount(amount)
                        text("Error", error)
                    }
            """.trimIndent().prependIndent("    "),
        )
    }

    @Test
    fun aPropertyMayRepeatOneTheAmountSendsOnlyWhenItHasIt() {
        val source = generate(
            """
            [[event]]
            domain = "Swap"
            name = "Token Sell"
            properties = [{ name = "Mint", type = "text" }, { group = "amount" }]
            """.trimIndent(),
        ).getValue("com/example/events/SwapEvents.kt")

        assertContains(source, "fun tokenSell(mint: String, amount: Amount): AnalyticsEvent =")
        assertContains(source, "text(\"Mint\", mint)\n            amount(amount)")
    }

    @Test
    fun namePlaceholdersAreTextUnlessTypedAsAList() {
        val source = generate(
            """
            [lists.Method.values]
            RESERVES = "Reserves"

            [[event]]
            domain = "Swap"
            name = "Token Purchase With {method} From {place}"
            builder = "purchase"
            placeholders = { method = "Method" }
            """.trimIndent(),
        ).getValue("com/example/events/SwapEvents.kt")

        assertContains(
            source,
            "fun purchase(method: Method, place: String): AnalyticsEvent = " +
                "event(\"Token Purchase With \${method.value} From \${place}\")",
        )
    }

    @Test
    fun fixedFormatValuesInterpolateAndEscapeLiterals() {
        val source = generate(
            """
            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            properties = [{ name = "Error", format = "Failed to parse ${'$'}deeplink \"=>\" {url}" }]
            """.trimIndent(),
        ).getValue("com/example/events/DeeplinkEvents.kt")

        assertContains(source, "fun parse(url: String): AnalyticsEvent =")
        assertContains(source, """text("Error", "Failed to parse \${'$'}deeplink \"=>\" ${'$'}{url}")""")
    }

    @Test
    fun twoBuildersMaySendOneEventName() {
        val source = generate(
            """
            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            builder = "parsed"
            properties = [{ name = "Type", type = "text" }]

            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            builder = "parseFailed"
            """.trimIndent(),
        ).getValue("com/example/events/DeeplinkEvents.kt")

        assertContains(source, "fun parsed(type: String): AnalyticsEvent =")
        assertContains(source, "fun parseFailed(): AnalyticsEvent = event(\"Deeplink: Parse\")")
    }

    @Test
    fun descriptionAndDriftBecomeKDocWrappedAtTheLineLimit() {
        val source = generate(
            """
            [domains]
            Thing = "Things."

            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            description = "The user had no display name before this submission."
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
                fun thingHappened(): AnalyticsEvent = event("Thing Happened")
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

    // Rejections. Each message names the event at fault and says what to change.

    @Test
    fun rejectsTomlSyntaxErrors() {
        assertContains(rejected("[lists.Method\n"), "events.toml")
    }

    @Test
    fun rejectsAnUnknownTypeWordAndListsTheWords() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [{ name = "Label", type = "strng" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "strng")
        assertContains(message, "text, yes/no, count, decimal, duration")
    }

    @Test
    fun rejectsAnUnknownListAndSaysWhereListsGo() {
        val message = rejected(
            """
            [lists.ChatType.values]
            TIP = "Tip"

            [[event]]
            domain = "Chat"
            name = "Sent Message"
            properties = [{ name = "Chat Type", type = "ChatTyp" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Sent Message\"")
        assertContains(message, "ChatTyp")
        assertContains(message, "[lists.ChatTyp]")
        assertContains(message, "ChatType")
    }

    @Test
    fun rejectsTwoEventsThatWouldGetTheSameBuilderName() {
        val message = rejected(
            """
            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"

            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            properties = [{ name = "Type", type = "text" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Deeplink: Parse\"")
        assertContains(message, "`parse`")
        assertContains(message, "add `builder")
    }

    @Test
    fun rejectsANameThatGivesNoBuilderName() {
        val message = rejected(
            """
            [lists.Step.values]
            ONE = "One"

            [[event]]
            domain = "Onramp"
            name = "Onramp: {step}"
            placeholders = { step = "Step" }
            """.trimIndent(),
        )
        assertContains(message, "\"Onramp: {step}\"")
        assertContains(message, "add `builder")
    }

    @Test
    fun rejectsTwoPropertiesThatWouldGetTheSameParameterName() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [{ name = "Call Site", type = "text" }, { name = "Call-Site", type = "text" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "`callSite`")
        assertContains(message, "add `param")
    }

    @Test
    fun rejectsAnOrderThatDoesNotNameEveryParameter() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            order = ["label", "labl"]
            properties = [{ name = "Label", type = "text" }, { name = "Tier", type = "count" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "missing tier")
        assertContains(message, "labl")
    }

    @Test
    fun rejectsAListValueWithNoWireString() {
        val empty = rejected("[lists.Method.values]\nRESERVES = \"\"")
        assertContains(empty, "Method")
        assertContains(empty, "RESERVES")
        assertContains(empty, "the text Mixpanel receives")

        val missing = rejected("[lists.Method.values]\nRESERVES = { drift = \"iOS has none.\" }")
        assertContains(missing, "RESERVES")
    }

    @Test
    fun rejectsAPlaceholderThatIsNotTextOrAList() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing {tier}"
            builder = "thing"
            placeholders = { tier = "count" }
            """.trimIndent(),
        )
        assertContains(message, "\"Thing {tier}\"")
        assertContains(message, "{tier}")
    }

    @Test
    fun rejectsAPlaceholderTypeNothingUses() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            placeholders = { method = "text" }
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "method")
    }

    @Test
    fun rejectsAnUnknownKeySoATypoIsNotSilentlyIgnored() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            drfit = "iOS sends nothing."
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "drfit")
    }

    @Test
    fun rejectsAnEventWithNoDomain() {
        val message = rejected("[[event]]\nname = \"Thing Happened\"")
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "domain")
    }

    @Test
    fun rejectsAnUnknownGroup() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [{ group = "outcom" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "outcom")
    }

    @Test
    fun rejectsAPropertyRepeatingOneTheAmountAlwaysSends() {
        val message = rejected(
            """
            [[event]]
            domain = "Swap"
            name = "Token Sell"
            properties = [{ name = "Fiat", type = "decimal" }, { group = "amount" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Token Sell\"")
        assertContains(message, "Fiat")
    }

    @Test
    fun rejectsTheSameKeyTwiceInOneEvent() {
        val message = rejected(
            """
            [[event]]
            domain = "Thing"
            name = "Thing Happened"
            properties = [{ name = "Label", type = "text" }, { name = "Label", type = "text", param = "other" }]
            """.trimIndent(),
        )
        assertContains(message, "\"Thing Happened\"")
        assertContains(message, "Label")
    }

    @Test
    fun rejectsADomainDescriptionNoEventUses() {
        val message = rejected("[domains]\nWalet = \"The Phantom flow.\"")
        assertContains(message, "Walet")
    }

    private fun assertContains(actual: String, expected: String) =
        assertTrue(expected in actual, "expected to find:\n$expected\n\nin:\n$actual")
}
