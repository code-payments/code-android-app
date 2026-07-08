package com.flipcash.app.myaccount.internal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.VerifiableContactMethod
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UserProfileScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var lastEvent: UserProfileViewModel.Event? = null

    private fun setScreen(state: UserProfileViewModel.State = UserProfileViewModel.State()) {
        lastEvent = null
        composeTestRule.setContent {
            DesignSystem {
                UserProfileScreenContent(state = state, dispatch = { lastEvent = it })
            }
        }
    }

    // ---------------------------------------------------------------
    // Display name
    // ---------------------------------------------------------------

    @Test
    fun `display name shown when present`() {
        setScreen(UserProfileViewModel.State(displayName = "Alice"))
        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test
    fun `no display name placeholder when null`() {
        setScreen(UserProfileViewModel.State(displayName = null))
        composeTestRule.onNodeWithText("No display name set").assertIsDisplayed()
    }

    @Test
    fun `no display name placeholder when empty`() {
        setScreen(UserProfileViewModel.State(displayName = ""))
        composeTestRule.onNodeWithText("No display name set").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Phone
    // ---------------------------------------------------------------

    @Test
    fun `phone number shown when present`() {
        setScreen(UserProfileViewModel.State(phone = VerifiableContactMethod("+1 555-1234", verified = true)))
        composeTestRule.onNodeWithText("+1 555-1234").assertIsDisplayed()
    }

    @Test
    fun `verified badge shown for verified phone`() {
        setScreen(UserProfileViewModel.State(phone = VerifiableContactMethod("+1 555-1234", verified = true)))
        composeTestRule.onNodeWithText("Verified").assertIsDisplayed()
    }

    @Test
    fun `unverified badge and value shown for unverified phone`() {
        setScreen(UserProfileViewModel.State(phone = VerifiableContactMethod("+1 555-1234", verified = false)))
        composeTestRule.onNodeWithText("+1 555-1234").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unverified").assertIsDisplayed()
    }

    @Test
    fun `add phone shown when phone is null`() {
        setScreen(UserProfileViewModel.State(phone = null))
        composeTestRule.onNodeWithText("Add Phone Number").assertIsDisplayed()
    }

    @Test
    fun `linked for payments indicator shown when true`() {
        setScreen(
            UserProfileViewModel.State(
                phone = VerifiableContactMethod("+1 555-1234", verified = true),
                phoneLinkedForPayment = true,
            )
        )
        composeTestRule.onNodeWithContentDescription("Linked for payments").assertIsDisplayed()
    }

    @Test
    fun `linked for payments indicator not shown when false`() {
        setScreen(
            UserProfileViewModel.State(
                phone = VerifiableContactMethod("+1 555-1234", verified = true),
                phoneLinkedForPayment = false,
            )
        )
        composeTestRule.onNodeWithContentDescription("Linked for payments").assertDoesNotExist()
    }

    // ---------------------------------------------------------------
    // Email
    // ---------------------------------------------------------------

    @Test
    fun `email shown when present`() {
        setScreen(UserProfileViewModel.State(email = VerifiableContactMethod("alice@example.com", verified = true)))
        composeTestRule.onNodeWithText("alice@example.com").assertIsDisplayed()
    }

    @Test
    fun `add email shown when email is null`() {
        setScreen(UserProfileViewModel.State(email = null))
        composeTestRule.onNodeWithText("Add Email Address").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Social accounts
    // ---------------------------------------------------------------

    @Test
    fun `social account username and name displayed`() {
        val account = SocialAccount.TwitterX(
            id = "1",
            username = "testuser",
            name = "Test User",
            description = "",
            profilePicUrl = "",
            verifiedType = null,
            followerCount = 0,
        )
        setScreen(UserProfileViewModel.State(socialAccounts = listOf(account)))
        composeTestRule.onNodeWithText("@testuser").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test User").assertIsDisplayed()
    }

    @Test
    fun `no social accounts placeholder when list empty`() {
        setScreen(UserProfileViewModel.State(socialAccounts = emptyList()))
        composeTestRule.onNodeWithText("No social accounts linked").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Event dispatch
    // ---------------------------------------------------------------

    @Test
    fun `tapping add phone dispatches ConnectPhoneClicked`() {
        setScreen(UserProfileViewModel.State(phone = null))
        composeTestRule.onNodeWithText("Add Phone Number").performClick()
        assertTrue(lastEvent is UserProfileViewModel.Event.ConnectPhoneClicked)
    }
}
