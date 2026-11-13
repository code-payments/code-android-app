package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Group Money phase 2, item 4: the Invite People sheet (frame 1-07, node 13729:672). Close at the
 * start, the group's avatar and name, one line above the link, a caption under it, and three tiles:
 * Copy Link, Share Link, QR Code. Every invite entry opens this sheet; the host sheet (Approve
 * switch, Manage Invites) is not reached. The classic shell keeps today's sheet and routing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class GroupInviteSheetGroupMoneyInviteSpec {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val group = ChatSubject.Group(
        chatId = ChatId(byteArrayOf(4)),
        groupTitle = "Road Trip",
        picture = null,
        memberCount = 1L,
        rules = null,
        isMember = true,
        isPrivate = true,
    )

    private var copied = 0
    private var shared = 0
    private var shownCard = 0

    private fun show(shell: NavBarShell) {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    GroupInviteSheetContent(
                        group = group,
                        inviteUrl = "https://app.flipcash.com/chat/road-trip",
                        inviteLink = null,
                        creating = false,
                        copied = false,
                        onCopy = { copied++ },
                        onShare = { shared++ },
                        onShowCard = { shownCard++ },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `group money sheet names the group with its initials avatar`() {
        show(NavBarShell.GroupMoney)

        composeRule.onNodeWithText("Invite People").assertIsDisplayed()
        composeRule.onNodeWithText("RT", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
    }

    @Test
    fun `group money sheet reads the frame's copy around the link`() {
        show(NavBarShell.GroupMoney)

        composeRule.onNodeWithText("Send this link to the people you want in your group.").assertIsDisplayed()
        composeRule.onNodeWithText("Anyone with the link can join.").assertIsDisplayed()
        composeRule.onNodeWithText("Send this link to the people you want to join your group").assertDoesNotExist()
    }

    @Test
    fun `group money sheet has Copy Link, Share Link and QR Code tiles`() {
        show(NavBarShell.GroupMoney)

        composeRule.onNodeWithText("Copy Link").performClick()
        composeRule.onNodeWithText("Share Link").performClick()
        composeRule.onNodeWithText("QR Code").performClick()
        assertEquals(Triple(1, 1, 1), Triple(copied, shared, shownCard))
    }

    @Test
    fun `group money sheet puts Close at the start`() {
        show(NavBarShell.GroupMoney)

        // The bar also subcomposes its leading control unplaced, to measure it (TopAppBarBase's
        // "leftIcon_probe"), so a leading Close has a second, unplaced node with the same tag.
        val placed = composeRule.onAllNodesWithTag("action_close").fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }
        assertEquals(1, placed.size, "one placed Close")
        val close = placed.single().boundsInRoot
        val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
        assertTrue(close.right <= title.left, "Close $close should sit left of the title $title")
    }

    @Test
    fun `group money sheet has no host controls`() {
        show(NavBarShell.GroupMoney)

        composeRule.onNodeWithText("Approve New Members", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Manage Invites", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Recent Chats", substring = true).assertDoesNotExist()
    }

    @Test
    fun `group money invite entries never open the host sheet`() {
        assertFalse(inviteOpensHost(NavBarShell.GroupMoney, canInvite = true))
        assertFalse(inviteOpensHost(NavBarShell.GroupMoney, canInvite = false))
    }

    @Test
    fun `classic invite entries open the host sheet for a host`() {
        assertTrue(inviteOpensHost(NavBarShell.Classic, canInvite = true))
        assertFalse(inviteOpensHost(NavBarShell.Classic, canInvite = false))
    }

    @Test
    fun `classic sheet keeps Copy, Share and its hint, with Close at the end`() {
        show(NavBarShell.Classic)

        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Send this link to the people you want to join your group").assertIsDisplayed()
        composeRule.onNodeWithText("Anyone with the link can join.").assertDoesNotExist()
        composeRule.onNodeWithText("Road Trip").assertDoesNotExist()
        val close = composeRule.onNodeWithTag("action_close").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
        assertTrue(close.left >= title.right, "classic Close $close stays right of the title $title")
    }
}
