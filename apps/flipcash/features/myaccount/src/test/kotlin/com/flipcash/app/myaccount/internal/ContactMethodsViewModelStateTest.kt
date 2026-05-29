package com.flipcash.app.myaccount.internal

import com.flipcash.services.models.SocialAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactMethodsViewModelStateTest {

    private val reduce = UserProfileViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has null profile fields`() {
        val state = UserProfileViewModel.State()
        assertNull(state.displayName)
        assertNull(state.phoneNumber)
        assertNull(state.emailAddress)
        assertFalse(state.phoneLinkedForPayment)
        assertTrue(state.socialAccounts.isEmpty())
    }

    @Test
    fun `OnProfileUpdated sets all profile fields`() {
        val xAccount = SocialAccount.TwitterX(
            id = "123",
            username = "testuser",
            name = "Test User",
            description = "desc",
            profilePicUrl = "https://example.com/pic.jpg",
            verifiedType = SocialAccount.TwitterX.VerifiedType.BLUE,
            followerCount = 100,
        )
        val updated = reduce(
            UserProfileViewModel.Event.OnProfileUpdated(
                displayName = "Alice",
                phone = "+15551234567",
                email = "test@example.com",
                linkedForPayment = true,
                socialAccounts = listOf(xAccount),
            )
        )(UserProfileViewModel.State())
        assertEquals("Alice", updated.displayName)
        assertEquals("+15551234567", updated.phoneNumber)
        assertEquals("test@example.com", updated.emailAddress)
        assertTrue(updated.phoneLinkedForPayment)
        assertEquals(1, updated.socialAccounts.size)
        assertEquals(xAccount, updated.socialAccounts.first())
    }

    @Test
    fun `OnProfileUpdated with null values`() {
        val updated = reduce(
            UserProfileViewModel.Event.OnProfileUpdated(
                displayName = null,
                phone = null,
                email = null,
                linkedForPayment = false,
                socialAccounts = emptyList(),
            )
        )(UserProfileViewModel.State())
        assertNull(updated.displayName)
        assertNull(updated.phoneNumber)
        assertNull(updated.emailAddress)
        assertFalse(updated.phoneLinkedForPayment)
        assertTrue(updated.socialAccounts.isEmpty())
    }

    @Test
    fun `no-op events return state unchanged`() {
        val xAccount = SocialAccount.TwitterX(
            id = "123",
            username = "testuser",
            name = "Test User",
            description = "desc",
            profilePicUrl = "https://example.com/pic.jpg",
            verifiedType = null,
            followerCount = 0,
        )
        val state = UserProfileViewModel.State(
            displayName = "Alice",
            phoneNumber = "+15551234567",
            emailAddress = "test@example.com",
            phoneLinkedForPayment = true,
            socialAccounts = listOf(xAccount),
        )
        val noOpEvents = listOf(
            UserProfileViewModel.Event.UnlinkPhoneClicked,
            UserProfileViewModel.Event.UnlinkEmailClicked,
            UserProfileViewModel.Event.ConnectPhoneClicked,
            UserProfileViewModel.Event.ConnectEmailClicked,
            UserProfileViewModel.Event.ReplacePhoneClicked,
            UserProfileViewModel.Event.ReplaceEmailClicked,
            UserProfileViewModel.Event.UnlinkSocialAccountClicked(xAccount),
            UserProfileViewModel.Event.NavigateToPhoneVerification,
            UserProfileViewModel.Event.NavigateToEmailVerification,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }
}
