package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.InviteHostAction
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.invites.InviteLinkState
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatId
import com.getcode.navigation.core.CodeNavigator
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Group Money phase 2, item 4: Invite People. Frame 1-07's sheet (node 13729:672) is not built.
 * Every invite entry opens the app's existing share sheet, with or without Group Money: the
 * transcript's Invite People, Join Requests' empty state, Manage Invites' Share Link and the hand-off
 * to the join link open the steps Group Info's Share opens, and that sheet draws the same in either
 * shell. Group Info still opens the host sheet (Approve switch, Manage Invites) for a host.
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

    private val readyLink = InviteLinkState.Ready(
        url = "https://app.flipcash.com/chat/invite#s=secret",
        expiresAt = null,
        useCount = 0,
        maxUses = 0,
    )

    /** The two group sheets the chat flow draws: the join link, and the host's invite link. */
    private enum class Sheet { JoinLink, InviteLink }

    /**
     * Draws [Sheet] under [NavBarShell] as the chat flow's entries draw it, starting from the first
     * of each list, and hands back a function that switches to another pair. Each switch draws the
     * sheet from scratch, so no state carries over between shells.
     */
    private fun showSheets(): (NavBarShell, Sheet) -> Unit {
        var shown by mutableStateOf(NavBarShell.GroupMoney to Sheet.JoinLink)
        composeRule.setContent {
            val (shell, sheet) = shown
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    key(shell, sheet) {
                        Box(Modifier.testTag(SheetTag)) {
                            when (sheet) {
                                Sheet.JoinLink -> GroupInviteSheet(
                                    inviteUrl = PlainUrl,
                                    subject = ShareToChatsSubject.Group(group),
                                    onCopy = {},
                                    onDismiss = {},
                                    onShowCard = {},
                                )
                                Sheet.InviteLink -> GroupInviteSheet(
                                    inviteUrl = null,
                                    subject = ShareToChatsSubject.GroupInviteLink(group, readyLink),
                                    onCopy = {},
                                    onDismiss = {},
                                    onShowCard = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return { shell, sheet ->
            shown = shell to sheet
            composeRule.waitForIdle()
        }
    }

    /** One semantics node of the sheet: what it reads, its tag, and where it is drawn. */
    private data class Drawn(val text: String?, val tag: String?, val description: String?, val bounds: Rect)

    /** Every node of the sheet in the unmerged tree, in drawing order. */
    private fun drawnSheet(): List<Drawn> {
        val drawn = mutableListOf<Drawn>()
        fun walk(node: SemanticsNode) {
            drawn += Drawn(
                text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
                tag = node.config.getOrNull(SemanticsProperties.TestTag),
                description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
                bounds = node.boundsInRoot,
            )
            node.children.forEach(::walk)
        }
        walk(composeRule.onNodeWithTag(SheetTag, useUnmergedTree = true).fetchSemanticsNode())
        return drawn
    }

    @Test
    fun `each sheet draws the same under group money as under classic`() {
        val show = showSheets()
        Sheet.entries.forEach { sheet ->
            show(NavBarShell.Classic, sheet)
            val classic = drawnSheet()
            show(NavBarShell.GroupMoney, sheet)
            assertEquals(classic, drawnSheet(), "$sheet")
        }
    }

    @Test
    fun `sheet keeps Copy, Share, QR Code and its hint, with Close at the end, in either shell`() {
        val show = showSheets()
        NavBarShell.entries.forEach { shell ->
            Sheet.entries.forEach { sheet ->
                show(shell, sheet)
                composeRule.onNodeWithText("Copy").assertIsDisplayed()
                composeRule.onNodeWithText("Share").assertIsDisplayed()
                composeRule.onNodeWithText("QR Code").assertIsDisplayed()
                composeRule.onNodeWithText(ClassicHint).assertIsDisplayed()
                // Frame 1-07's copy, tiles, header and host controls are drawn nowhere.
                listOf(
                    "Send this link to the people you want in your group.",
                    "Anyone with the link can join.",
                    "Copy Link",
                    "Share Link",
                    "Road Trip",
                    "Approve New Members",
                    "Manage Invites",
                ).forEach { text ->
                    composeRule.onNodeWithText(text, substring = true).assertDoesNotExist()
                }
                val close = composeRule.onNodeWithTag("action_close").fetchSemanticsNode().boundsInRoot
                val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
                assertTrue(close.left >= title.right, "$shell $sheet: Close $close stays right of the title $title")
            }
        }
    }

    /** A navigator that records the routes it is sent to. */
    private fun recordingNavigator(): CodeNavigator = mockk<CodeNavigator>(relaxed = true).also { navigator ->
        every { navigator.dispatchTarget(any()) } returns navigator
    }

    /** The invite steps [open] sends a navigator to, in order. */
    private fun stepsFrom(open: CodeNavigator.() -> Unit): List<ChatStep> {
        val navigator = recordingNavigator()
        val routes = mutableListOf<NavKey>()
        every { navigator.navigate(capture(routes), any()) } just Runs
        navigator.open()
        return routes.filterIsInstance<ChatStep>()
    }

    /** Manage Invites' step names the invite it shares; the other entries' steps do not. */
    private fun ChatStep.withoutInviteId(): ChatStep = when (this) {
        is ChatStep.ShareInviteLink -> copy(inviteIdHex = null)
        else -> this
    }

    @Test
    fun `every invite entry opens the steps group info's share opens`() {
        // Group Info's Share: the host sheet's two share picks, and the join link for a member.
        val inviteLink = stepsFrom { onInviteHostPick(InviteHostAction.ShareInvite) {} }
        val joinLink = stepsFrom { onInviteHostPick(InviteHostAction.ShareJoin) {} }
        NavBarShell.entries.forEach { shell ->
            assertEquals(joinLink, stepsFrom { openGroupInfoInvite(shell, canInvite = false) {} }, "$shell member")
        }

        // Each entry's call, with the arguments its call site passes. The transcript is
        // MessengerScreen's Invite People; Join Requests is its empty state, which under Group Money
        // opens the share sheet straight away and under the classic shell opens the join link for a
        // member; Manage Invites is a row's Share Link; the hand-off is the invite sheet's offer of
        // the join link.
        val opened = mapOf(
            "transcript, invite link" to stepsFrom { openInvitePeople(InvitePeopleTarget.CurrentInviteLink) },
            "transcript, join link" to stepsFrom { openInvitePeople(InvitePeopleTarget.PlainLink) },
            "join requests, invite link" to stepsFrom { openInvitePeople(InvitePeopleTarget.CurrentInviteLink) },
            "join requests, join link" to stepsFrom { openInvitePeople(InvitePeopleTarget.PlainLink) },
            "join requests, classic member" to stepsFrom { openGroupInvite() },
            "manage invites" to stepsFrom { openInviteLinkShare("0a0b") }.map { it.withoutInviteId() },
            "hand-off" to stepsFrom { openGroupInvite() },
        )
        val expected = mapOf(
            "transcript, invite link" to inviteLink,
            "transcript, join link" to joinLink,
            "join requests, invite link" to inviteLink,
            "join requests, join link" to joinLink,
            "join requests, classic member" to joinLink,
            "manage invites" to inviteLink,
            "hand-off" to joinLink,
        )
        assertEquals(expected, opened)
    }

    @Test
    fun `manage invites shares the invite it was opened on`() {
        val step = assertIs<ChatStep.ShareInviteLink>(stepsFrom { openInviteLinkShare("0a0b") }.single())
        assertEquals("0a0b", step.inviteIdHex)
    }

    @Test
    fun `host sheet picks open the invite link, the join link and manage invites`() {
        val invite = assertIs<ChatStep.ShareInviteLink>(stepsFrom { onInviteHostPick(InviteHostAction.ShareInvite) {} }.single())
        assertEquals(null, invite.inviteIdHex)
        assertIs<ChatStep.InviteToGroup>(stepsFrom { onInviteHostPick(InviteHostAction.ShareJoin) {} }.single())

        var managed = 0
        assertEquals(emptyList(), stepsFrom { onInviteHostPick(InviteHostAction.ManageInvites) { managed++ } })
        assertEquals(1, managed)
    }

    @Test
    fun `group info opens the host sheet for a host and the join link otherwise, in either shell`() {
        NavBarShell.entries.forEach { shell ->
            val host = recordingNavigator()
            host.openGroupInfoInvite(shell, canInvite = true) {}
            verify { host.navigate(ChatStep.InviteHost, any()) }

            val member = stepsFrom { openGroupInfoInvite(shell, canInvite = false) {} }
            assertIs<ChatStep.InviteToGroup>(member.single(), "$shell")
        }
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

    private companion object {
        const val SheetTag = "invite-sheet"
        const val PlainUrl = "https://app.flipcash.com/chat/road-trip"
        const val ClassicHint = "Send this link to the people you want to join your group"
    }
}
