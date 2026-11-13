package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.InviteHostAction
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import com.getcode.navigation.core.CodeNavigator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Copy Link, Share Link, QR Code. Every invite entry but Group Info opens this sheet. Group Info
 * keeps the classic sheets, the host sheet (Approve switch, Manage Invites) included, and the share
 * sheets the host sheet opens are classic too. The classic shell keeps today's sheet and routing.
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

    /** The sheet under [shell]; [classic] is the route's, as the chat flow's entries apply it. */
    private fun show(shell: NavBarShell, classic: Boolean = false) {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                Box(Modifier.fillMaxSize().testTag(ScreenTag)) {
                    FlipcashPreview(showBackground = true) {
                        Box(Modifier.testTag(SheetTag)) {
                            InviteSheetShell(classic) {
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
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `group money sheet wraps its content rather than filling the screen`() {
        show(NavBarShell.GroupMoney)

        val sheet = composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot
        val screen = composeRule.onNodeWithTag(ScreenTag).fetchSemanticsNode().boundsInRoot
        assertTrue(sheet.height < screen.height, "sheet $sheet should wrap its content on screen $screen")
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
    fun `group money opens the host sheet only from group info`() {
        assertTrue(inviteOpensHost(NavBarShell.GroupMoney, canInvite = true, InviteEntry.GroupInfo))
        assertFalse(inviteOpensHost(NavBarShell.GroupMoney, canInvite = true, InviteEntry.JoinRequests))
        InviteEntry.entries.forEach { entry ->
            assertFalse(inviteOpensHost(NavBarShell.GroupMoney, canInvite = false, entry), "$entry")
        }
    }

    @Test
    fun `classic invite entries open the host sheet for a host`() {
        InviteEntry.entries.forEach { entry ->
            assertTrue(inviteOpensHost(NavBarShell.Classic, canInvite = true, entry), "$entry")
            assertFalse(inviteOpensHost(NavBarShell.Classic, canInvite = false, entry), "$entry")
        }
    }

    @Test
    fun `group info's sheet under group money is the classic sheet`() {
        show(NavBarShell.GroupMoney, classic = true)

        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Send this link to the people you want to join your group").assertIsDisplayed()
        composeRule.onNodeWithText("Anyone with the link can join.").assertDoesNotExist()
        composeRule.onNodeWithText("Road Trip").assertDoesNotExist()
        composeRule.onNodeWithText("Copy Link").assertDoesNotExist()
        val close = composeRule.onNodeWithTag("action_close").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
        assertTrue(close.left >= title.right, "classic Close $close stays right of the title $title")
    }

    /** A navigator that records the routes it is sent to. */
    private fun recordingNavigator(): CodeNavigator = mockk<CodeNavigator>(relaxed = true).also { navigator ->
        every { navigator.dispatchTarget(any()) } returns navigator
    }

    @Test
    fun `host sheet picks open the classic share sheets`() {
        val navigator = recordingNavigator()
        var managed = 0

        navigator.onInviteHostPick(InviteHostAction.ShareInvite) { managed++ }
        navigator.onInviteHostPick(InviteHostAction.ShareJoin) { managed++ }
        navigator.onInviteHostPick(InviteHostAction.ManageInvites) { managed++ }

        verify { navigator.navigate(ChatStep.ShareInviteLink(classic = true), any()) }
        verify { navigator.navigate(ChatStep.InviteToGroup(classic = true), any()) }
        assertEquals(1, managed)
    }

    @Test
    fun `entries other than group info open the group money share sheets`() {
        val navigator = recordingNavigator()

        navigator.openInvitePeople(InvitePeopleTarget.CurrentInviteLink)
        navigator.openInvitePeople(InvitePeopleTarget.PlainLink)

        verify { navigator.navigate(ChatStep.ShareInviteLink(classic = false), any()) }
        verify { navigator.navigate(ChatStep.InviteToGroup(classic = false), any()) }
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

    private companion object {
        const val SheetTag = "invite-sheet"
        const val ScreenTag = "screen"
    }
}
