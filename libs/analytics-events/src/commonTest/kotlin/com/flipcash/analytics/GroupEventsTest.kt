package com.flipcash.analytics

import com.flipcash.analytics.events.GroupEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupEventsTest {

    @Test
    fun newOpenedCarriesNoProperties() {
        assertEquals(AnalyticsEvent("Group: New Opened", emptyMap()), GroupEvents.newOpened())
    }

    @Test
    fun createdCarriesStateGateMintAndPicture() {
        assertEquals(
            AnalyticsEvent(
                "Group: Created",
                mapOf(
                    "State" to PropertyValue.Text("Success"),
                    "Gate Mint" to PropertyValue.Text("mint"),
                    "Has Picture" to PropertyValue.Flag(true),
                ),
            ),
            GroupEvents.created(State.SUCCESS, null, "mint", true),
        )
    }

    @Test
    fun createdLeavesOutAnAbsentGateMint() {
        assertEquals(
            AnalyticsEvent(
                "Group: Created",
                mapOf(
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("RulesNotSatisfied"),
                    "Has Picture" to PropertyValue.Flag(false),
                ),
            ),
            GroupEvents.created(State.FAILURE, "RulesNotSatisfied", null, false),
        )
    }

    @Test
    fun editedCarriesEveryFieldByItsWireString() {
        assertEquals(
            listOf("Name", "Picture"),
            GroupField.entries.map {
                val event = GroupEvents.edited(it, State.SUCCESS, null)
                assertEquals("Group: Edited", event.name)
                (event.properties.getValue("Field") as PropertyValue.Text).value
            },
        )
        assertEquals(
            AnalyticsEvent(
                "Group: Edited",
                mapOf(
                    "Field" to PropertyValue.Text("Name"),
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("Denied"),
                ),
            ),
            GroupEvents.edited(GroupField.NAME, State.FAILURE, "Denied"),
        )
    }

    @Test
    fun inviteSheetOpenedCarriesSourceAndMemberCount() {
        assertEquals(
            listOf("Chat", "Profile"),
            GroupInviteSheetSource.entries.map {
                (GroupEvents.inviteSheetOpened(it, 0).properties.getValue("Source") as PropertyValue.Text).value
            },
        )
        assertEquals(
            AnalyticsEvent(
                "Group: Invite Sheet Opened",
                mapOf("Source" to PropertyValue.Text("Profile"), "Member Count" to PropertyValue.Number(12.0)),
            ),
            GroupEvents.inviteSheetOpened(GroupInviteSheetSource.PROFILE, 12),
        )
    }

    @Test
    fun inviteSharedCarriesEveryMethodByItsWireString() {
        assertEquals(
            listOf("Share", "Copy"),
            GroupInviteMethod.entries.map {
                val event = GroupEvents.inviteShared(it)
                assertEquals(AnalyticsEvent("Group: Invite Shared", mapOf("Method" to event.properties.getValue("Method"))), event)
                (event.properties.getValue("Method") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun inviteFollowedCarriesEverySourceByItsWireString() {
        assertEquals(
            listOf("Link", "QR", "Chat Card"),
            GroupInviteSource.entries.map {
                val event = GroupEvents.inviteFollowed(it)
                assertEquals(AnalyticsEvent("Group: Invite Followed", mapOf("Source" to event.properties.getValue("Source"))), event)
                (event.properties.getValue("Source") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun gateShownCarriesAccessGateMintAndMemberCount() {
        assertEquals(
            listOf("Eligible", "Blocked"),
            GroupAccess.entries.map {
                (GroupEvents.gateShown(it, null, 0).properties.getValue("Access") as PropertyValue.Text).value
            },
        )
        assertEquals(
            AnalyticsEvent(
                "Group: Gate Shown",
                mapOf(
                    "Access" to PropertyValue.Text("Blocked"),
                    "Gate Mint" to PropertyValue.Text("mint"),
                    "Member Count" to PropertyValue.Number(3.0),
                ),
            ),
            GroupEvents.gateShown(GroupAccess.BLOCKED, "mint", 3),
        )
    }

    @Test
    fun gateFundingTappedCarriesMethodAndGateMint() {
        assertEquals(
            listOf("Buy Token", "Add Cash"),
            GroupGateFunding.entries.map {
                (GroupEvents.gateFundingTapped(it, null).properties.getValue("Method") as PropertyValue.Text).value
            },
        )
        assertEquals(
            AnalyticsEvent(
                "Group: Gate Funding Tapped",
                mapOf("Method" to PropertyValue.Text("Buy Token"), "Gate Mint" to PropertyValue.Text("mint")),
            ),
            GroupEvents.gateFundingTapped(GroupGateFunding.BUY_TOKEN, "mint"),
        )
        assertEquals(
            AnalyticsEvent("Group: Gate Funding Tapped", mapOf("Method" to PropertyValue.Text("Add Cash"))),
            GroupEvents.gateFundingTapped(GroupGateFunding.ADD_CASH, null),
        )
    }

    @Test
    fun joinedCarriesStateMemberCountAndGated() {
        assertEquals(
            AnalyticsEvent(
                "Group: Joined",
                mapOf(
                    "State" to PropertyValue.Text("Failure"),
                    "Error" to PropertyValue.Text("Network"),
                    "Member Count" to PropertyValue.Number(7.0),
                    "Gated" to PropertyValue.Flag(true),
                ),
            ),
            GroupEvents.joined(State.FAILURE, "Network", 7, true),
        )
    }

    @Test
    fun leftCarriesStateAndMemberCount() {
        assertEquals(
            AnalyticsEvent(
                "Group: Left",
                mapOf("State" to PropertyValue.Text("Success"), "Member Count" to PropertyValue.Number(4.0)),
            ),
            GroupEvents.left(State.SUCCESS, null, 4),
        )
    }

    @Test
    fun infoOpenedCarriesMemberCountAndMembership() {
        assertEquals(
            AnalyticsEvent(
                "Group: Info Opened",
                mapOf("Member Count" to PropertyValue.Number(9.0), "Is Member" to PropertyValue.Flag(false)),
            ),
            GroupEvents.infoOpened(9, false),
        )
    }
}
