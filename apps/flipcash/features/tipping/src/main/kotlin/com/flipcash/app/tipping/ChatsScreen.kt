package com.flipcash.app.tipping

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.app.tipping.internal.components.TipChatRow
import com.flipcash.features.tipping.R
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.rememberIsMuted
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SwipeAction
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement

/**
 * The "Chats" root tab: tip DMs and groups under the standard centred screen title, with no dismiss
 * affordance (the root nav bar is the chrome). The tip card lives on the "You" tab.
 */
@Composable
fun ChatsScreen() {
    val viewModel = hiltViewModel<ChatsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    val chats = state.chats
    val listState = rememberLazyListState()

    CodeScaffold(
        // The list runs the full height and passes under the title bar, which fades it out against
        // the background at its own edge — the same treatment the chat screen gives its message
        // list, rather than cutting the list off at the bar.
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            val backgroundColor = CodeTheme.colors.background
            AppBarWithTitle(
                // Drawn behind the bar rather than as a box sized to it, so the scrim's reach past
                // the bar's bottom edge stays out of the bar's own measurement — which is what the
                // list is padded by.
                modifier = Modifier.drawBehind {
                    val scrimHeight = size.height + ScrimTail.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(backgroundColor, Color.Transparent),
                            startY = 0f,
                            endY = scrimHeight,
                        ),
                        size = size.copy(height = scrimHeight),
                    )
                },
                title = stringResource(R.string.title_chats),
                // Centred rather than flush-start: an empty leading slot reserves no width, so a
                // Start title sits at the inset and reads as off-centre against the Add button.
                titleAlignment = Alignment.CenterHorizontally,
                // Node 9442:5779 — the only way to start a chat with someone who has never paid
                // you. A pushed route rather than an in-place screen: this list is a tab home, and
                // anything drawn inside it would leave the tab bar over the entry screen.
                endContent = {
                    AppBarDefaults.Add { navigator.push(AppRoute.Messaging.NewChat) }
                },
            )
        },
    ) { barPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Scroll anchor for UI tests: `send_contact_row` addresses a single row, this
                // addresses the scrollable list itself.
                .testTag("chat_list")
                // End edge only — the start edge is the bar's scrim now, and a second fade there
                // would darken rows twice over as they pass under the title.
                .verticalScrollStateGradient(scrollState = listState, showAtStart = false),
            state = listState,
            contentPadding = PaddingValues(
                // The bar's height as content padding rather than as a layout inset: the viewport
                // runs the full height and rows scroll under the bar, but at rest the first row
                // still sits clear of it.
                top = barPadding.calculateTopPadding(),
                // Clears the hoisted tab bar: keeps the last row reachable. Both paddings are
                // measured out of `fillParentMaxSize`, so the empty state stays centered in the
                // space the two bars leave visible.
                bottom = LocalTabBarPadding.current.calculateBottomPadding(),
            ),
        ) {
            // Once the feed has loaded and there's nothing to show, the list is replaced by a
            // centered prompt.
            if (chats.isLoaded() && chats.data.isEmpty()) {
                item { NoChatsYet(Modifier.fillParentMaxSize()) }
            } else {
                tipChatItems(
                    chats = chats.dataOrNull.orEmpty(),
                    onClick = { chat ->
                        navigator.push(
                            AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId))
                        )
                    },
                    // The same sheet the chat and group profiles open, so the list offers exactly
                    // the durations they do, and unmuting is its "Never" row rather than a toggle.
                    onMute = { chat -> navigator.push(AppRoute.Messaging.MuteChat(chat.chatId)) },
                )
            }
        }
    }
}

/**
 * How far past the bar's bottom edge the scrim reaches before it is fully transparent. Rows begin
 * dissolving this far below the title rather than only once they meet it.
 */
private val ScrimTail = 48.dp

/**
 * The "Chats" tab empty state (node 9340:2746) — bubble mark, title and prompt, centered in the
 * space the caller gives it (the list viewport).
 */
@Composable
private fun NoChatsYet(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                modifier = Modifier.size(CodeTheme.dimens.grid.x16),
                painter = painterResource(R.drawable.ic_bubble_outline),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.textMain),
            )

            Text(
                text = stringResource(R.string.title_noChatsYet),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )

            Text(
                modifier = Modifier.fillMaxWidth(0.6f),
                text = stringResource(R.string.description_noChatsYet),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun LazyListScope.tipChatItems(
    chats: List<ConversationReference>,
    onClick: (ConversationReference) -> Unit,
    onMute: (ConversationReference) -> Unit,
) {
    // Keyed by chat so a row's swipe state stays with its chat when new activity reorders the list.
    itemsIndexed(chats, key = { _, chat -> chat.chatId }) { index, chat ->
        MuteSwipeRow(
            isMuted = rememberIsMuted(chat.viewerState),
            onMute = { onMute(chat) },
            stateKey = chat.chatId,
        ) {
            TipChatRow(
                chat = chat,
                showDivider = index < chats.lastIndex,
            ) {
                onClick(chat)
            }
        }
    }
}

/**
 * A trailing swipe that opens the mute sheet, whichever way the chat is muted.
 *
 * The icon names the state the chat would be moved out of: a crossed-out bell on an audible chat,
 * and a plain one on a muted chat, where the sheet is also how the mute is cleared. It never mutes
 * directly, because a mute always needs a duration.
 *
 * Resets rather than settling open: a full swipe opens the sheet, and a row left swiped under it
 * would still be sitting open once the sheet closed.
 */
@Composable
private fun MuteSwipeRow(
    isMuted: Boolean,
    onMute: () -> Unit,
    stateKey: Any,
    content: @Composable () -> Unit,
) {
    val label = stringResource(
        if (isMuted) R.string.content_description_changeMute
        else R.string.content_description_muteChat
    )
    SwipeActionRow(
        actions = listOf(
            SwipeAction(
                // Neutral rather than the delete red: nothing is lost by it.
                background = CodeTheme.colors.surfaceVariant,
                onTriggered = onMute,
                resetOnDismiss = true,
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.Outlined.Notifications
                    } else {
                        Icons.Outlined.NotificationsOff
                    },
                    contentDescription = label,
                    tint = CodeTheme.colors.textMain,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            }
        ),
        // A swipe is out of reach with a screen reader, so the row offers the same action there.
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(label) { onMute(); true })
        },
        stateKey = stateKey,
        content = content,
    )
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewNoChatsYet() {
    NoChatsYet(Modifier.fillMaxSize())
}
