package com.flipcash.analytics

import com.flipcash.analytics.events.CommunityChatEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityChatEventsTest {

    @Test
    fun joinedCarriesStateMemberCountAndGateMint() {
        assertEquals(
            AnalyticsEvent(
                "Community Chat: Joined",
                mapOf(
                    "State" to PropertyValue.Text("Success"),
                    "Member Count" to PropertyValue.Number(12.0),
                    "Gate Mint" to PropertyValue.Text("mint"),
                ),
            ),
            CommunityChatEvents.joined(State.SUCCESS, null, 12, "mint"),
        )
    }

    @Test
    fun joinedFailureCarriesTheError() {
        assertEquals(
            AnalyticsEvent(
                "Community Chat: Joined",
                mapOf(
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("RulesNotSatisfied"),
                    "Member Count" to PropertyValue.Number(3.0),
                ),
            ),
            CommunityChatEvents.joined(State.FAILURE, "RulesNotSatisfied", 3, null),
        )
    }
}
