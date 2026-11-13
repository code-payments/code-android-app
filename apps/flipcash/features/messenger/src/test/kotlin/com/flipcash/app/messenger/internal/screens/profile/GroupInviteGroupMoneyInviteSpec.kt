package com.flipcash.app.messenger.internal.screens.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.InviteLandingState
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.invite.DeadInviteReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * Group Money phase 2, item 5: the join preview (frame 2-09, node 13729:1404). No "Private group ·
 * You've been invited to join" note; the cover, initials avatar, name, a pluralised member count,
 * each member's avatar and name, and a white Join Group. The classic shell keeps the note.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class GroupInviteGroupMoneyInviteSpec {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val invitedNote = "Private group · You’ve been invited to join"

    private fun member(index: Int, name: String) = ChatMember(
        userId = listOf(index.toByte()),
        userProfile = UserProfile(
            displayName = name,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            userId = listOf(index.toByte()),
        ),
        pointers = emptyList(),
        joinedAt = Instant.fromEpochSeconds(100L + index),
        version = 1,
    )

    private fun landing(names: List<String>) = InviteLandingState(
        phase = InviteLandingState.Phase.Preview,
        group = ChatSubject.Group(
            chatId = ChatId(ByteArray(16) { 6 }.toList()),
            groupTitle = "Saturday Knicks Game",
            picture = null,
            memberCount = names.size.toLong(),
            rules = null,
            isMember = false,
            isPrivate = true,
        ),
        members = names.mapIndexed { i, n -> member(i + 1, n) },
        memberCount = names.size.toLong(),
    )

    private fun show(shell: NavBarShell, state: InviteLandingState) {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    GroupInviteContent(state, onBack = {}, onJoin = {}, onRequest = {}, onCancelRequest = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `group money preview drops the invited note`() {
        show(NavBarShell.GroupMoney, landing(listOf("Shebah")))

        composeRule.onNodeWithText(invitedNote).assertDoesNotExist()
    }

    @Test
    fun `group money preview reads name, 1 member, the member and Join Group`() {
        show(NavBarShell.GroupMoney, landing(listOf("Shebah")))

        composeRule.onNodeWithText("Saturday Knicks Game").assertIsDisplayed()
        composeRule.onNodeWithText("1 member").assertIsDisplayed()
        composeRule.onNodeWithText("Shebah").assertIsDisplayed()
        composeRule.onNodeWithText("Join Group").assertIsDisplayed()
    }

    @Test
    fun `group money preview pluralises the member count`() {
        show(NavBarShell.GroupMoney, landing(listOf("Shebah", "Raj")))

        composeRule.onNodeWithText("2 members").assertIsDisplayed()
    }

    @Test
    fun `group money preview still says why a dead link is dead`() {
        show(NavBarShell.GroupMoney, landing(listOf("Shebah")).copy(dead = true, deadReason = DeadInviteReason.EXPIRED))

        composeRule.onNodeWithText("expired", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `classic preview keeps the invited note`() {
        show(NavBarShell.Classic, landing(listOf("Shebah")))

        composeRule.onNodeWithText(invitedNote).performScrollTo().assertIsDisplayed()
    }
}
