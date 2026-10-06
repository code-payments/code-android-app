package com.flipcash.analytics

import com.flipcash.analytics.events.ProfileEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileEventsTest {

    @Test
    fun screenEventsHaveNoProperties() {
        assertEquals(AnalyticsEvent("Profile: Settings Viewed", emptyMap()), ProfileEvents.settingsViewed())
        assertEquals(AnalyticsEvent("Profile: Edit Profile Viewed", emptyMap()), ProfileEvents.editProfileViewed())
        assertEquals(AnalyticsEvent("Profile: Bio Moderation Failed", emptyMap()), ProfileEvents.bioModerationFailed())
        assertEquals(AnalyticsEvent("Profile: Card Shown", emptyMap()), ProfileEvents.cardShown())
    }

    @Test
    fun fieldSavedCarriesEveryFieldByItsWireString() {
        assertEquals(
            listOf("Name", "Username", "Photo", "Cover", "Bio", "Minimum"),
            ProfileField.entries.map {
                val event = ProfileEvents.fieldSaved(it)
                assertEquals("Profile: Field Saved", event.name)
                (event.properties.getValue("Field") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun menuActionCarriesEveryActionByItsWireString() {
        assertEquals(
            listOf("Mute", "Report", "Block", "Unblock"),
            ProfileMenuAction.entries.map {
                val event = ProfileEvents.menuAction(it)
                assertEquals("Profile: Menu Action", event.name)
                (event.properties.getValue("Action") as PropertyValue.Text).value
            },
        )
    }

    @Test
    fun pinnedButtonTappedCarriesEveryActionByItsWireString() {
        assertEquals(
            listOf("Start Chatting", "Open Chat", "Unblock"),
            ProfilePinnedAction.entries.map {
                val event = ProfileEvents.pinnedButtonTapped(it)
                assertEquals("Profile: Pinned Button Tapped", event.name)
                (event.properties.getValue("Action") as PropertyValue.Text).value
            },
        )
    }
}
