package com.flipcash.app.tipping

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.analytics.LocalAnalytics
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The chat list's title bar under each shell. Group Money's Groups tab has no "…" menu, so its
 * "Groups" title can sit flush start (frame 1-01, node 13729:420), and a long press on a row is
 * the way into select mode. The classic shell keeps the menu and the centred "Chat".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ChatsTitleBarShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val navigator = CodeNavigator(
        backStack = NavBackStack<NavKey>(AppRoute.Tabs.Chats),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = {},
    )

    private var inset = Dp.Unspecified

    private fun setContent(shell: NavBarShell, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalCodeNavigator provides navigator,
                LocalNavBarShell provides shell,
                LocalAnalytics provides RecordingAnalytics(),
            ) {
                FlipcashPreview {
                    inset = CodeTheme.dimens.inset
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setBar(shell: NavBarShell, onSelectChats: () -> Unit = {}) = setContent(shell) {
        ChatsTitleBar(canReadAll = true, onSelectChats = onSelectChats, onReadAll = {})
    }

    // --- the title bar ---

    @Test
    fun `group money draws no overflow menu`() {
        setBar(NavBarShell.GroupMoney)

        composeRule.onNodeWithTag("action_chat_list_more").assertDoesNotExist()
    }

    @Test
    fun `group money titles the list Groups, flush with the inset`() {
        setBar(NavBarShell.GroupMoney)

        val title = composeRule.onNodeWithText("Groups").getBoundsInRoot()
        assertEquals(inset, title.left)
        composeRule.onNodeWithText("Chat").assertDoesNotExist()
    }

    @Test
    fun `group money keeps Add`() {
        setBar(NavBarShell.GroupMoney)

        composeRule.onNodeWithTag("action_add").assertExists()
    }

    @Test
    fun `classic keeps the overflow menu and its Select Chats`() {
        var selectChats = 0
        setBar(NavBarShell.Classic, onSelectChats = { selectChats++ })

        composeRule.onNodeWithText("Chat").assertExists()
        composeRule.onNodeWithText("Groups").assertDoesNotExist()
        // AppBarWithTitle composes the leading slot a second time to probe its width, and never
        // places that copy; the first match is the placed button.
        composeRule.onAllNodesWithTag("action_chat_list_more").onFirst().performClick()
        composeRule.onNodeWithTag("action_read_all").assertExists()
        composeRule.onNodeWithTag("action_select_chats").performClick()
        composeRule.waitForIdle()
        assertEquals(1, selectChats)
    }

    // --- select mode from a row, with the overflow gone ---

    private fun id(n: Int) = ChatId(ByteArray(16) { n.toByte() }.toList())

    private val group = ConversationReference(chatId = id(1), title = "Brothers", isGroup = true)
    private val tipDm = ConversationReference(chatId = id(2), displayName = "Grace Hopper", chatType = ChatType.TIP_DM)
    private val contactDm =
        ConversationReference(chatId = id(3), displayName = "Ada Lovelace", chatType = ChatType.CONTACT_DM)

    private fun reduce(state: ChatsViewModel.State, event: ChatsViewModel.Event) =
        ChatsViewModel.updateStateForEvent(event)(state)

    private val chats = listOf(group, tipDm, contactDm)

    // The list's state, driven through the view model's reducer as the screen drives it.
    private var state by mutableStateOf(
        reduce(
            ChatsViewModel.State(),
            ChatsViewModel.Event.ChatsUpdated(
                Loadable.Loaded(chats),
                ChatListProjection.Empty.copy(main = chats.map { it.chatId.toString() }),
            ),
        )
    )

    /** The full list, with the title bar the screen gives it. */
    private fun setList(shell: NavBarShell) = setContent(shell) {
            ChatsScreenContent(
                state = state,
                onSelectFilter = {},
                onRevealChips = {},
                onOpenArchived = {},
                onOpenChat = {},
                onMute = {},
                onArchive = {},
                onStartSelection = { state = reduce(state, ChatsViewModel.Event.SelectionStarted(it)) },
                onToggleSelection = { state = reduce(state, ChatsViewModel.Event.SelectionToggled(it)) },
                topBar = { ChatsTitleBar(canReadAll = state.canReadAll, onSelectChats = {}, onReadAll = {}) },
            )
    }

    /** A finger held on the row named [name], from outside select mode. */
    private fun longPress(name: String) {
        state = reduce(state, ChatsViewModel.Event.SelectionEnded)
        composeRule.waitForIdle()
        assertNull(state.selection)
        composeRule.onNodeWithText(name).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun `under group money a long press on any row enters select mode with that row selected`() {
        setList(NavBarShell.GroupMoney)
        composeRule.onNodeWithTag("action_chat_list_more").assertDoesNotExist()

        chats.forEach { chat ->
            longPress(chat.name!!)
            assertEquals(listOf(chat), state.selectedChats, chat.chatType.name)
        }
    }

    @Test
    fun `under classic a long press on a row still selects that row`() {
        setList(NavBarShell.Classic)

        longPress(tipDm.displayName!!)

        assertEquals(listOf(tipDm), state.selectedChats)
    }
}
