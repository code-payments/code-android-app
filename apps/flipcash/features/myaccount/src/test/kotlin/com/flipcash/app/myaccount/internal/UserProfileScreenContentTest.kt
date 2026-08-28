package com.flipcash.app.myaccount.internal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.flipcash.app.myaccount.internal.userprofile.UserProfileScreenContent
import com.flipcash.app.myaccount.internal.userprofile.UserProfileViewModel
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

    /** The social section sits below the fold, so bring it into view before asserting on it. */
    private fun scrollTo(text: String) {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

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
    fun `no display name placeholder when empty`() {
        setScreen(UserProfileViewModel.State(displayName = ""))
        composeTestRule.onNodeWithText("No display name set").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Handle and public link
    // ---------------------------------------------------------------

    @Test
    fun `claimed handle shown with its link`() {
        setScreen(
            UserProfileViewModel.State(
                username = "alice",
                tipCardLink = "https://flipcash.com/alice",
            )
        )
        composeTestRule.onNodeWithText("@alice").assertIsDisplayed()
        // The scheme is dropped: the link is shown the way it's read aloud.
        composeTestRule.onNodeWithText("flipcash.com/alice").assertIsDisplayed()
    }

    @Test
    fun `unclaimed handle shows the upsell and its minimum`() {
        setScreen(
            UserProfileViewModel.State(
                username = null,
                usernameMinBalance = "$5 USD",
            )
        )
        composeTestRule.onNodeWithText("Get a custom @username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get your balance to $5 USD or more to unlock")
            .assertIsDisplayed()
    }

    @Test
    fun `tapping the link dispatches CopyTipCardLink`() {
        setScreen(UserProfileViewModel.State(tipCardLink = "https://flipcash.com/alice"))
        composeTestRule.onNodeWithText("flipcash.com/alice").performClick()
        assertTrue(lastEvent is UserProfileViewModel.Event.CopyTipCardLink)
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
        scrollTo("@testuser")
        composeTestRule.onNodeWithText("@testuser").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test User").assertIsDisplayed()
    }

    @Test
    fun `no social accounts placeholder when list empty`() {
        setScreen(UserProfileViewModel.State(socialAccounts = emptyList()))
        scrollTo("No social accounts linked")
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
