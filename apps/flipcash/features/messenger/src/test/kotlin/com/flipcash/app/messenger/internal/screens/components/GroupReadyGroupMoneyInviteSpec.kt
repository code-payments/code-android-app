package com.flipcash.app.messenger.internal.screens.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.TypingConstraints
import com.flipcash.app.messenger.internal.withSeparators
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.BadgeFocus
import dev.chrisbanes.haze.HazeState
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Group Money phase 2, item 3: a group just created (frames 1-06 node 13729:642, 1-08 node
 * 13729:851). The transcript's ready card becomes a centred empty state under the same rule; the
 * header reads "1 person"; the composer has its + inside the Message field and a blue $ button.
 * The classic shell keeps today's incoming "Your Group Is Ready" bubble and the dark $ button.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class GroupReadyGroupMoneyInviteSpec {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val start = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun group(memberCount: Long) = ChatSubject.Group(
        chatId = ChatId(byteArrayOf(3)),
        groupTitle = "Road Trip",
        picture = null,
        memberCount = memberCount,
        rules = null,
        isMember = true,
        isPrivate = true,
    )

    private fun chatState(memberCount: Long) = ChatViewModel.State(
        chatId = ChatId(byteArrayOf(3)),
        subject = group(memberCount),
        chatType = ChatType.GROUP,
        resolveState = ChatViewModel.ResolveState.Resolved,
        typingConstraints = TypingConstraints(enabled = true, resolved = true),
    )

    /** One message from the sole member: the card stays up after they post, and the list is non-empty. */
    private fun showTranscript(shell: NavBarShell, memberCount: Long = 1L, onInvite: () -> Unit = {}) {
        val bubble = ChatListItem.ContentBubble(
            messageId = 1L,
            contentIndex = 0,
            content = MessageContent.Text("first"),
            isFromSelf = true,
            timestamp = start,
        )
        val loaded = LoadState.NotLoading(endOfPaginationReached = true)
        val page = PagingData.from<ChatListItem.TranscriptMessage>(listOf(bubble), LoadStates(loaded, loaded, loaded))
        val paged = flowOf(page.withSeparators(UnreadBoundary.None, SeparatorConfig.DayOnly))
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashThemeWrapper().Wrap {
                    MessageList(
                        contentPadding = PaddingValues(),
                        state = chatState(memberCount),
                        messages = paged.collectAsLazyPagingItems(),
                        separatorConfig = SeparatorConfig.DayOnly,
                        onAction = {},
                        linkCardResolution = Unresolved,
                        viewerCanInvite = true,
                        onInvitePeople = onInvite,
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("first").fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
    }

    @Test
    fun `group money shows the empty state in place of the ready card`() {
        showTranscript(NavBarShell.GroupMoney)

        composeRule.onNodeWithText("Your group is ready").assertIsDisplayed()
        composeRule.onNodeWithText("Invite people to join your group.").assertIsDisplayed()
        composeRule.onNodeWithText("Invite People").assertIsDisplayed()
        composeRule.onNodeWithText("Your Group Is Ready").assertDoesNotExist()
        composeRule.onNodeWithText("Invite people to join Road Trip").assertDoesNotExist()
    }

    @Test
    fun `group money Invite People opens the invite sheet`() {
        var invited = 0
        showTranscript(NavBarShell.GroupMoney, onInvite = { invited++ })

        composeRule.onNodeWithText("Invite People").performClick()
        assertEquals(1, invited)
    }

    @Test
    fun `group money draws no empty state once someone has joined`() {
        showTranscript(NavBarShell.GroupMoney, memberCount = 2L)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Your group is ready").assertDoesNotExist()
        composeRule.onNodeWithText("Invite People").assertDoesNotExist()
    }

    @Test
    fun `classic keeps the ready card bubble`() {
        showTranscript(NavBarShell.Classic)

        composeRule.onNodeWithText("Your Group Is Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Invite people to join Road Trip").assertIsDisplayed()
        composeRule.onNodeWithText("Your group is ready").assertDoesNotExist()
    }

    @Test
    fun `group money header reads the group name and 1 person`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides NavBarShell.GroupMoney) {
                FlipcashPreview(showBackground = true) {
                    ChatTopBar(
                        navigator = mockk<CodeNavigator>(relaxed = true),
                        state = chatState(memberCount = 1L),
                        onBarHeightChange = {},
                        chatActionHandler = {},
                        dispatch = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
        composeRule.onNodeWithText("1 person").assertIsDisplayed()
    }

    private fun showComposer(shell: NavBarShell) {
        composeRule.setContent {
            CompositionLocalProvider(LocalNavBarShell provides shell) {
                FlipcashPreview(showBackground = true) {
                    UserControlBottomBar(
                        state = chatState(memberCount = 1L),
                        hazeState = HazeState(),
                        onAction = {},
                        dispatch = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    @Test
    fun `group money composer has the + inside the Message field`() {
        showComposer(NavBarShell.GroupMoney)

        val plus = composeRule.onNodeWithTag("chat_attach_button").fetchSemanticsNode().boundsInRoot
        val field = composeRule.onNodeWithText("Message", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(plus.right <= field.left, "the + sits at the field's leading edge")
    }

    @Test
    fun `group money $ button is blue`() {
        showComposer(NavBarShell.GroupMoney)

        val fill = sendCashFill()
        assertTrue(close(BadgeFocus, fill), "\$ fill ${hex(fill)}, wanted ${hex(BadgeFocus.toArgb())}")
    }

    @Test
    fun `classic $ button stays dark`() {
        showComposer(NavBarShell.Classic)

        val fill = sendCashFill()
        assertFalse(close(BadgeFocus, fill), "classic \$ should not be blue, was ${hex(fill)}")
    }

    /** The $ button's fill, read just inside its leading edge, clear of the glyph and the rim. */
    private fun sendCashFill(): Int {
        val bounds = composeRule.onNodeWithTag(SEND_CASH_TAG).fetchSemanticsNode().boundsInRoot
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return bitmap.getPixel((bounds.left + 8f).roundToInt(), bounds.center.y.roundToInt())
    }

    private fun close(expected: Color, actual: Int): Boolean {
        val e = expected.toArgb()
        return listOf(16, 8, 0).all { shift -> abs((e shr shift and 0xFF) - (actual shr shift and 0xFF)) <= 8 }
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    private object Unresolved : LinkCardResolution {
        override val revision: StateFlow<Int> = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard = card
        override suspend fun resolve(card: LinkCard): LinkCard = card
    }

    private companion object {
        const val SEND_CASH_TAG = "chat_send_cash_button"
    }
}
