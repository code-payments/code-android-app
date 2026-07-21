package com.flipcash.app.myaccount.internal

import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.VerifiableContactMethod
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
        assertNull(state.phone)
        assertNull(state.email)
        assertFalse(state.phoneLinkedForPayment)
        assertTrue(state.socialAccounts.isEmpty())
        assertNull(state.publicKey)
        assertNull(state.accountId)
        assertNull(state.pushToken)
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
                profilePicture = null,
                phone = VerifiableContactMethod("+15551234567", verified = true),
                email = VerifiableContactMethod("test@example.com", verified = false),
                linkedForPayment = true,
                socialAccounts = listOf(xAccount),
            )
        )(UserProfileViewModel.State())
        assertEquals("Alice", updated.displayName)
        assertEquals(VerifiableContactMethod("+15551234567", verified = true), updated.phone)
        assertEquals(VerifiableContactMethod("test@example.com", verified = false), updated.email)
        assertTrue(updated.phoneLinkedForPayment)
        assertEquals(1, updated.socialAccounts.size)
        assertEquals(xAccount, updated.socialAccounts.first())
    }

    @Test
    fun `OnProfileUpdated with null values`() {
        val updated = reduce(
            UserProfileViewModel.Event.OnProfileUpdated(
                displayName = null,
                profilePicture = null,
                phone = null,
                email = null,
                linkedForPayment = false,
                socialAccounts = emptyList(),
            )
        )(UserProfileViewModel.State())
        assertNull(updated.displayName)
        assertNull(updated.phone)
        assertNull(updated.email)
        assertFalse(updated.phoneLinkedForPayment)
        assertTrue(updated.socialAccounts.isEmpty())
    }

    @Test
    fun `OnAccountInfoUpdated sets account fields`() {
        val updated = reduce(
            UserProfileViewModel.Event.OnAccountInfoUpdated(
                publicKey = "pk-abc",
                accountId = "user-123",
                pushToken = "token-xyz",
            )
        )(UserProfileViewModel.State())
        assertEquals("pk-abc", updated.publicKey)
        assertEquals("user-123", updated.accountId)
        assertEquals("token-xyz", updated.pushToken)
    }

    @Test
    fun `OnAccountInfoUpdated with null values`() {
        val updated = reduce(
            UserProfileViewModel.Event.OnAccountInfoUpdated(
                publicKey = null,
                accountId = null,
                pushToken = null,
            )
        )(UserProfileViewModel.State())
        assertNull(updated.publicKey)
        assertNull(updated.accountId)
        assertNull(updated.pushToken)
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
            phone = VerifiableContactMethod("+15551234567", verified = true),
            email = VerifiableContactMethod("test@example.com", verified = true),
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
            UserProfileViewModel.Event.CopyPublicKey,
            UserProfileViewModel.Event.CopyAccountId,
            UserProfileViewModel.Event.CopyPushToken,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }
}
