package com.flipcash.app.messenger.internal.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
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
import kotlin.test.assertTrue

/**
 * Group Money phase 2, item 4: the Invite People sheet (frame 1-07, node 13729:672). Close at the
 * start, the group's avatar and name, one line above the link, a caption under it, and three tiles:
 * Copy Link, Share Link, QR Code. Every invite entry but Group Info opens this sheet. The
 * transcript's Invite People opens it condensed: no avatar or name, on tighter gaps, and Close at
 * the end at the inset it has at the start. Group Info
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

    private val readyLink = InviteLinkState.Ready(
        url = "https://app.flipcash.com/chat/invite#s=secret",
        expiresAt = null,
        useCount = 0,
        maxUses = 0,
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
                                    inviteUrl = PlainUrl,
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

    /**
     * What the chat flow draws for an invite [step]: [InviteSheetShell] on the step's classic flag,
     * then [GroupInviteSheet] on its condensed flag, with the group or its invite link.
     */
    @Composable
    private fun SheetFor(step: ChatStep) {
        when (step) {
            is ChatStep.InviteToGroup -> InviteSheetShell(step.classic) {
                GroupInviteSheet(
                    inviteUrl = PlainUrl,
                    subject = ShareToChatsSubject.Group(group),
                    onCopy = {},
                    onDismiss = {},
                    onShowCard = {},
                    condensed = step.condensed,
                )
            }
            is ChatStep.ShareInviteLink -> InviteSheetShell(step.classic) {
                GroupInviteSheet(
                    inviteUrl = null,
                    subject = ShareToChatsSubject.GroupInviteLink(group, readyLink),
                    onCopy = {},
                    onDismiss = {},
                    onShowCard = {},
                    condensed = step.condensed,
                )
            }
            else -> error("not an invite sheet: $step")
        }
    }

    /**
     * Draws [step] under [shell], re-reading it on each recomposition. Returns how much further the
     * caption sits from the copy above the link on a sheet that is not condensed, in px: grid x6 and
     * x3 there, staticGrid x3 and x2 condensed.
     */
    private fun showSteps(shell: NavBarShell, step: () -> ChatStep): Float {
        var gapDifference = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    val dimens = CodeTheme.dimens
                    gapDifference = with(LocalDensity.current) {
                        (dimens.grid.x6 + dimens.grid.x3 - dimens.staticGrid.x3 - dimens.staticGrid.x2).toPx()
                    }
                    Box(Modifier.testTag(SheetTag)) { SheetFor(step()) }
                }
            }
        }
        composeRule.waitForIdle()
        return gapDifference
    }

    private data class Measured(
        val height: Float,
        val name: Boolean,
        val initials: Boolean,
        val copyToCaption: Float,
    )

    private fun measured(): Measured {
        fun drawn(text: String) =
            composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val copy = composeRule.onNodeWithText(GroupMoneyCopy).fetchSemanticsNode().boundsInRoot
        val caption = composeRule.onNodeWithText(Caption).fetchSemanticsNode().boundsInRoot
        return Measured(
            height = composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot.height,
            name = drawn("Road Trip"),
            initials = drawn("RT"),
            copyToCaption = caption.top - copy.bottom,
        )
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
    fun `group money sheet shows no group name or avatar`() {
        // The transcript's sheets, both links, through GroupInviteSheet: it holds the group for
        // the share sheet's title and picture, so there is a group on hand that it could draw.
        val transcript = stepsFrom(openFromTranscript)
        assertEquals(2, transcript.size, "$transcript")
        var step by mutableStateOf(transcript.first())
        showSteps(NavBarShell.GroupMoney) { step }

        transcript.forEach {
            step = it
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Invite People").assertIsDisplayed()
            composeRule.onNodeWithText(Caption).assertIsDisplayed()
            composeRule.onNodeWithText("Road Trip", substring = true, useUnmergedTree = true).assertDoesNotExist()
            composeRule.onNodeWithText("RT", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `join requests, manage invites and the hand-off name the group the transcript leaves out`() {
        val transcript = stepsFrom(openFromTranscript)
        val entries = mapOf(
            "join requests" to stepsFrom(openFromJoinRequests),
            "manage invites" to stepsFrom(openFromManageInvites),
            "hand-off" to stepsFrom(openFromHandOff),
        )
        var step by mutableStateOf(transcript.first())
        val gapDifference = showSteps(NavBarShell.GroupMoney) { step }

        fun measure(of: ChatStep): Measured {
            step = of
            composeRule.waitForIdle()
            return measured()
        }

        entries.forEach { (entry, steps) ->
            assertTrue(steps.isNotEmpty(), entry)
            steps.forEach { entryStep ->
                // Against the transcript's sheet on the same link, so the pill is the same height.
                val condensed = measure(transcript.single { it::class == entryStep::class })
                val full = measure(entryStep)

                assertFalse(condensed.name || condensed.initials, "transcript $condensed")
                assertTrue(full.name && full.initials, "$entry $entryStep $full")
                assertTrue(full.height > condensed.height, "$entry $full should be taller than $condensed")
                // 4488fcebb's gaps around the link against the transcript's.
                assertEquals(gapDifference, full.copyToCaption - condensed.copyToCaption, 1.5f, "$entry $entryStep")
            }
        }
    }

    @Test
    fun `classic sheet is the same whether or not it is condensed`() {
        // Group Info's steps under Group Money, and the other entries' steps under the classic shell.
        val cases = (
            stepsFrom { openGroupInfoInvite(NavBarShell.GroupMoney, canInvite = false) {} } +
                stepsFrom { onInviteHostPick(InviteHostAction.ShareInvite) {} }
            ).map { NavBarShell.GroupMoney to it } +
            (stepsFrom(openFromTranscript) + stepsFrom(openFromManageInvites)).map { NavBarShell.Classic to it }
        var case by mutableStateOf(cases.first())
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides case.first) {
                FlipcashPreview(showBackground = true) {
                    Box(Modifier.testTag(SheetTag)) { SheetFor(case.second) }
                }
            }
        }

        cases.forEach { (shell, entryStep) ->
            val heights = listOf(entryStep.withCondensed(false), entryStep.withCondensed(true)).map {
                case = shell to it
                composeRule.waitForIdle()
                composeRule.onNodeWithText("Copy").assertIsDisplayed()
                composeRule.onNodeWithText("Share").assertIsDisplayed()
                composeRule.onNodeWithText(ClassicHint).assertIsDisplayed()
                composeRule.onNodeWithText(Caption).assertDoesNotExist()
                composeRule.onNodeWithText("Road Trip").assertDoesNotExist()
                composeRule.onNodeWithText("RT", useUnmergedTree = true).assertDoesNotExist()
                composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot.height
            }
            assertEquals(heights[0], heights[1], "$shell $entryStep")
        }
    }

    @Test
    fun `group money sheet reads the frame's copy around the link`() {
        show(NavBarShell.GroupMoney)

        composeRule.onNodeWithText(GroupMoneyCopy).assertIsDisplayed()
        composeRule.onNodeWithText(Caption).assertIsDisplayed()
        composeRule.onNodeWithText(ClassicHint).assertDoesNotExist()
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
    fun `transcript sheet puts Close at the end, the other entries at the start, at the same inset`() {
        val transcript = stepsFrom(openFromTranscript)
        val entries = mapOf(
            "join requests" to stepsFrom(openFromJoinRequests),
            "manage invites" to stepsFrom(openFromManageInvites),
            "hand-off" to stepsFrom(openFromHandOff),
        )
        var step by mutableStateOf(transcript.first())
        var controlInset = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides NavBarShell.GroupMoney) {
                FlipcashPreview(showBackground = true) {
                    controlInset = with(LocalDensity.current) { AppBarDefaults.ControlInset.toPx() }
                    Box(Modifier.testTag(SheetTag)) { SheetFor(step) }
                }
            }
        }

        fun bar(of: ChatStep): Pair<Rect, Rect> {
            step = of
            composeRule.waitForIdle()
            // A leading Close also has an unplaced copy from the bar's "leftIcon_probe".
            val close = composeRule.onAllNodesWithTag("action_close").fetchSemanticsNodes()
                .filter { it.layoutInfo.isPlaced }
            assertEquals(1, close.size, "one placed Close on $of")
            val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
            return close.single().boundsInRoot to title
        }

        val sheet = composeRule.onNodeWithTag(SheetTag).fetchSemanticsNode().boundsInRoot
        entries.forEach { (entry, steps) ->
            assertTrue(steps.isNotEmpty(), entry)
            steps.forEach { entryStep ->
                val (end, endTitle) = bar(transcript.single { it::class == entryStep::class })
                val (start, startTitle) = bar(entryStep)

                assertTrue(end.center.x > sheet.center.x, "transcript Close $end should sit in the trailing half of $sheet")
                assertTrue(start.center.x < sheet.center.x, "$entry Close $start should sit in the leading half of $sheet")
                assertEquals(start.left - sheet.left, sheet.right - end.right, 0.5f, "$entry $entryStep inset")
                assertEquals(controlInset, sheet.right - end.right, 0.5f, "transcript inset")
                assertEquals(start.size, end.size, "$entry $entryStep Close size")
                assertEquals(start.top, end.top, 0.5f, "$entry $entryStep Close top")
                assertEquals(startTitle, endTitle, "$entry $entryStep title")
            }
        }
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
        composeRule.onNodeWithText(ClassicHint).assertIsDisplayed()
        composeRule.onNodeWithText(Caption).assertDoesNotExist()
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

    /** The invite steps [open] sends a navigator to, in order. */
    private fun stepsFrom(open: CodeNavigator.() -> Unit): List<ChatStep> {
        val navigator = recordingNavigator()
        val routes = mutableListOf<NavKey>()
        every { navigator.navigate(capture(routes), any()) } just Runs
        navigator.open()
        return routes.filterIsInstance<ChatStep>()
    }

    // Each entry's navigator calls, with the arguments its call site passes. The transcript is
    // MessengerScreen's Invite People, Join Requests is its empty state under Group Money, Manage
    // Invites is a row's Share Link, and the hand-off is the invite sheet's offer of the join link.
    private val openFromTranscript: CodeNavigator.() -> Unit = {
        openInvitePeople(InvitePeopleTarget.CurrentInviteLink, condensed = true)
        openInvitePeople(InvitePeopleTarget.PlainLink, condensed = true)
    }
    private val openFromJoinRequests: CodeNavigator.() -> Unit = {
        openInvitePeople(InvitePeopleTarget.CurrentInviteLink)
        openInvitePeople(InvitePeopleTarget.PlainLink)
    }
    private val openFromManageInvites: CodeNavigator.() -> Unit = { openInviteLinkShare("0a0b") }
    private val openFromHandOff: CodeNavigator.() -> Unit = { openGroupInvite(classic = false) }

    private fun ChatStep.withCondensed(condensed: Boolean): ChatStep = when (this) {
        is ChatStep.InviteToGroup -> copy(condensed = condensed)
        is ChatStep.ShareInviteLink -> copy(condensed = condensed)
        else -> error("not an invite sheet: $this")
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
    fun `group info opens the classic sheets in either shell`() {
        NavBarShell.entries.forEach { shell ->
            val host = recordingNavigator()
            host.openGroupInfoInvite(shell, canInvite = true) {}
            verify { host.navigate(ChatStep.InviteHost, any()) }
            verify(exactly = 0) { host.navigate(ChatStep.InviteToGroup(classic = false), any()) }

            val member = recordingNavigator()
            member.openGroupInfoInvite(shell, canInvite = false) {}
            verify { member.navigate(ChatStep.InviteToGroup(classic = true), any()) }
            verify(exactly = 0) { member.navigate(ChatStep.InviteHost, any()) }
        }
    }

    @Test
    fun `only the transcript opens the group money share sheets condensed`() {
        assertEquals(
            listOf(
                ChatStep.ShareInviteLink(classic = false, condensed = true),
                ChatStep.InviteToGroup(classic = false, condensed = true),
            ),
            stepsFrom(openFromTranscript),
        )
        assertEquals(
            listOf(ChatStep.ShareInviteLink(), ChatStep.InviteToGroup()),
            stepsFrom(openFromJoinRequests),
        )
        assertEquals(listOf(ChatStep.ShareInviteLink("0a0b")), stepsFrom(openFromManageInvites))
        assertEquals(listOf(ChatStep.InviteToGroup()), stepsFrom(openFromHandOff))
    }

    @Test
    fun `classic sheet keeps Copy, Share and its hint, with Close at the end`() {
        show(NavBarShell.Classic)

        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText(ClassicHint).assertIsDisplayed()
        composeRule.onNodeWithText(Caption).assertDoesNotExist()
        composeRule.onNodeWithText("Road Trip").assertDoesNotExist()
        val close = composeRule.onNodeWithTag("action_close").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Invite People").fetchSemanticsNode().boundsInRoot
        assertTrue(close.left >= title.right, "classic Close $close stays right of the title $title")
    }

    private companion object {
        const val SheetTag = "invite-sheet"
        const val ScreenTag = "screen"
        const val PlainUrl = "https://app.flipcash.com/chat/road-trip"
        const val GroupMoneyCopy = "Send this link to the people you want in your group."
        const val Caption = "Anyone with the link can join."
        const val ClassicHint = "Send this link to the people you want to join your group"
    }
}
