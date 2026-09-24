package com.getcode.buildlogic.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsPageTest {

    private fun page(toml: String): String =
        AnalyticsCatalogue.document(AnalyticsCatalogue.parse("package = \"com.example\"\n$toml"))

    @Test
    fun thePageHasADomainSectionPerEventTablesListsGroupsAndCounters() {
        val page = page(
            """
            [lists.Method]
            description = "How the user paid."
            [lists.Method.values]
            CARD = "Card"
            BANK = { value = "Bank", drift = "iOS has no Bank." }

            [lists.State.values]
            SUCCESS = "Success"

            [groups.outcome]
            properties = [
              { name = "State", type = "State" },
              { group = "amount", optional = true },
              { name = "Error", type = "text", optional = true },
            ]

            [counters]
            TIPS = "Tips Received"

            [domains]
            Thing = "Things the user does."

            [[event]]
            domain = "Thing"
            name = "Thing: Bought With {method}"
            placeholders = { method = "Method" }
            description = "The user bought a thing."
            drift = "iOS sends this without State."
            properties = [
              { name = "Label", type = "text" },
              { name = "Tier", type = "count", optional = true },
              { name = "Rate", type = "decimal" },
              { name = "Time", type = "duration" },
              { name = "Done", type = "yes/no" },
              { group = "outcome" },
            ]

            [[event]]
            domain = "Thing"
            name = "Thing: Parse"
            properties = [{ name = "Error", format = "Failed to parse => {url}" }]

            [[event]]
            domain = "Thing"
            name = "Thing: Started"
            """.trimIndent(),
        )

        assertEquals(
            """
            # Analytics events

            <!-- Generated from events.toml -- do not edit. After changing events.toml, run
                 ./gradlew :libs:analytics-events:writeAnalyticsEventsPage -->

            Every event both apps can send to Mixpanel, and the properties each one carries. To add
            or change an event, edit [events.toml](events.toml) in a pull request, or open an issue
            with the "Analytics event" form.

            Types: **text** is words, **yes/no** is true or false, **count** is a whole number,
            **decimal** is a number with a fraction, **duration** is milliseconds, and a **list**
            is one value from the [Lists](#lists) below. An optional property is left out when the
            app has no value for it.

            ## Thing

            Things the user does.

            ### Thing: Bought With {method}

            The user bought a thing.

            `{method}` in the name is one of [Method](#method).

            | Property | Type | Optional | Values |
            |---|---|---|---|
            | Label | text | no | |
            | Tier | count | yes | |
            | Rate | decimal | no | |
            | Time | duration | no | |
            | Done | yes/no | no | |
            | State | list | no | [State](#state) |
            | amount | group | yes | [amount](#amount) |
            | Error | text | yes | |

            > **Drift:** iOS sends this without State.

            ### Thing: Parse

            | Property | Type | Optional | Values |
            |---|---|---|---|
            | Error | text | no | `Failed to parse => {url}` |

            ### Thing: Started

            No properties.

            ## Groups

            ### amount

            Sent by events that carry an amount of money. Defined in `Amount.kt`.

            | Property | Type | Optional |
            |---|---|---|
            | Fiat | decimal | no |
            | Currency | text | no |
            | USDC | decimal | no |
            | Quarks | count | no |
            | Exchange Rate | decimal | yes |
            | Mint | text | yes |

            ## Lists

            ### Method

            How the user paid.

            | Value sent | Name in code | Drift |
            |---|---|---|
            | Card | `CARD` | |
            | Bank | `BANK` | iOS has no Bank. |

            ### State

            | Value sent | Name in code | Drift |
            |---|---|---|
            | Success | `SUCCESS` | |

            ## People counters

            Cumulative per-user counters, stored as Mixpanel people properties.

            | Counter | Name in code |
            |---|---|
            | Tips Received | `TIPS` |

            """.trimIndent(),
            page,
        )
    }

    @Test
    fun pipesInCellsAreEscapedSoTheTableHoldsItsShape() {
        val page = page(
            """
            [[event]]
            domain = "Thing"
            name = "Thing: Parse"
            properties = [{ name = "Error", format = "a | b {url}" }]
            """.trimIndent(),
        )

        assertTrue("| Error | text | no | `a \\| b {url}` |" in page, page)
    }

    @Test
    fun twoBuildersSendingOneNameShareOneHeadingSoItHasOneAnchor() {
        val page = page(
            """
            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            builder = "parsed"
            description = "The link parsed."
            properties = [{ name = "Type", type = "text" }]

            [[event]]
            domain = "Deeplink"
            name = "Deeplink: Parse"
            builder = "parseFailed"
            description = "The link didn't parse."
            """.trimIndent(),
        )

        assertTrue(
            """
            ### Deeplink: Parse

            Sent in 2 forms.

            The link parsed.

            | Property | Type | Optional | Values |
            |---|---|---|---|
            | Type | text | no | |

            The link didn't parse.

            No properties.

            """.trimIndent() in page,
            page,
        )
    }

    @Test
    fun anEmptyCatalogueStillWritesTheIntroduction() {
        assertTrue(page("").startsWith("# Analytics events\n"))
    }
}
