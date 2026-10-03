package com.flipcash.app.tipping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.SnackbarResult
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.flipcash.app.tipping.internal.ChipRevealConnection
import com.flipcash.app.tipping.internal.ParkFiller
import com.flipcash.app.tipping.internal.blockTouchesWhile
import com.flipcash.app.tipping.internal.rememberParkFiller
import com.flipcash.app.tipping.internal.chipReveal
import com.flipcash.app.tipping.internal.chipVisibleFraction
import com.flipcash.shared.chat.ChatListFilter
import com.getcode.ui.components.FilterChip
import com.getcode.ui.components.snack.SnackData
import com.getcode.ui.components.snack.showSnackbar
import com.getcode.ui.theme.CodeSnackbar
import com.getcode.ui.theme.CodeSnackbarHost
import kotlinx.coroutines.launch
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
import com.getcode.ui.components.SwipeRevealGroup
import com.getcode.ui.components.rememberSwipeRevealGroup
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement

/**
 * The "Chats" root tab: tip DMs and groups under the standard centred screen title, with no dismiss
 * affordance (the root nav bar is the chrome). The tip card lives on the "You" tab.
 *
 * The All / Unread / Groups chips are the list's first item. The list starts on item 1, so they sit
 * just out of view (under the bar, at zero alpha) until a pull from the top brings them on; see
 * [ChipRevealConnection] for how a pull settles.
 */
@Composable
fun ChatsScreen() {
    val viewModel = hiltViewModel<ChatsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    val chats = state.chats
    // Item 1 from the first frame, so the chips never flash on. Saveable, so the position (chips
    // shown or not) survives opening a chat and coming back.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (state.showsChips) 0 else 1)
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val chipRevealConnection = remember(listState, flingBehavior, viewModel) {
        ChipRevealConnection(
            listState = listState,
            flingBehavior = flingBehavior,
            isShown = { viewModel.stateFlow.value.showsChips },
            // Nothing to filter while loading or with no chats at all.
            canReveal = { viewModel.stateFlow.value.let { it.chats.isLoaded() && !it.hasNoChatsAtAll } },
            onRevealed = viewModel::revealChips,
        )
    }
    val visible = state.visibleChats
    // Everything between the chips and the filler, in list order.
    val parkKeys: List<Any> = remember(state.chats, state.hasNoChatsAtAll, visible) {
        listOf<Any>("archived") + when {
            !chats.isLoaded() -> listOf("loading")
            state.hasNoChatsAtAll -> listOf("empty")
            visible.isEmpty() -> listOf("empty_filter")
            else -> visible.map { it.chatId }
        }
    }
    val parkFiller = rememberParkFiller()
    SideEffect { parkFiller.retain(parkKeys) }
    // Restored on the chips (after process death, say) with the ViewModel starting over: they are
    // on screen, so they count as revealed rather than being parked by the next scroll.
    LaunchedEffect(listState) {
        if (listState.firstVisibleItemIndex == 0 && !viewModel.stateFlow.value.showsChips) {
            viewModel.revealChips()
        }
    }
    // One row open at a time: swiping another row closes the one left revealed.
    val revealGroup = rememberSwipeRevealGroup()
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val archivedMessage = stringResource(R.string.message_chatArchived)
    val undoLabel = stringResource(R.string.action_undo)
    val tabBarPadding = LocalTabBarPadding.current.calculateBottomPadding()

    CodeScaffold(
        scaffoldState = scaffoldState,
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
                title = stringResource(R.string.title_tabChat),
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
        // The tab bar is hoisted, so without this the snackbar draws underneath it.
        snackbarHost = { hostState ->
            CodeSnackbarHost(hostState, modifier = Modifier.padding(bottom = tabBarPadding)) { data ->
                // Every toast on this screen is the archive undo.
                CodeSnackbar(snackbarData = data, icon = Icons.Outlined.Archive)
            }
        },
    ) { barPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Scroll anchor for UI tests: `send_contact_row` addresses a single row, this
                // addresses the scrollable list itself.
                .testTag("chat_list")
                // Holds the chips out of view until a pull from the top brings them on.
                .chipReveal(chipRevealConnection)
                // End edge only — the start edge is the bar's scrim now, and a second fade there
                // would darken rows twice over as they pass under the title.
                .verticalScrollStateGradient(scrollState = listState, showAtStart = false),
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(
                // The bar's height as content padding rather than as a layout inset: the viewport
                // runs the full height and rows scroll under the bar, but at rest the first row
                // still sits clear of it.
                top = barPadding.calculateTopPadding(),
                // Clears the hoisted tab bar: keeps the last row reachable. Both paddings are
                // measured out of `fillParentMaxSize`, so the empty states stay centered in the
                // space the two bars leave visible.
                bottom = tabBarPadding,
            ),
        ) {
            item(key = "chips", contentType = "chips") {
                ChatFilterRow(
                    modifier = Modifier
                        .blockTouchesWhile { !state.showsChips && listState.chipVisibleFraction() == 0f }
                        .graphicsLayer {
                            // Fades in with the pull. Read here, in the draw phase, so following
                            // the finger never recomposes the row.
                            alpha = if (state.showsChips) 1f else listState.chipVisibleFraction()
                        },
                    selected = state.filter,
                    unreadCount = state.projection.unreadChipCount,
                    groupsCount = state.projection.groupsChipCount,
                    onSelect = { filter ->
                        if (!state.showsChips && listState.chipVisibleFraction() < 1f) {
                            // Hidden under the bar (a stray tap, or a screen reader reaching it):
                            // bring the row on rather than switching filters out of sight.
                            viewModel.revealChips()
                            scope.launch { listState.animateScrollToItem(0) }
                        } else if (filter != state.filter) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectFilter(filter)
                        }
                    },
                )
            }

            // Always emitted, so item 1 exists from the first composition (including while
            // loading) and the parked position has a stable anchor; empty when there is no row.
            // Its key never leaves the list, so animateItem only moves it; the row itself fades in
            // and out inside the slot, while the rows below slide to make room or close the gap.
            item(key = "archived", contentType = "archived") {
                Box(Modifier.animateItem().then(parkFiller.tracked("archived"))) {
                    AnimatedVisibility(
                        visible = state.showsArchivedRow,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        ArchivedRow(
                            count = state.projection.archivedRowCount,
                            onClick = { navigator.push(AppRoute.Messaging.ArchivedChats) },
                        )
                    }
                }
            }

            when {
                // A full-height spacer: with only the chips and an empty Archived slot the content
                // is shorter than the viewport, the list fills backward to index 0, and the chips
                // would show once rows arrive.
                !chats.isLoaded() -> item(key = "loading") {
                    Spacer(parkFiller.tracked("loading").fillParentMaxSize())
                }
                // The empty states fade like the rows do, so a filter or archive that empties the
                // list doesn't drop the message on top of rows still fading out.
                state.hasNoChatsAtAll -> item(key = "empty") {
                    NoChatsYet(
                        Modifier.animateItem().then(parkFiller.tracked("empty")).fillParentMaxSize()
                    )
                }
                visible.isEmpty() -> item(key = "empty_filter") {
                    EmptyFilterState(
                        modifier = Modifier.animateItem()
                            .then(parkFiller.tracked("empty_filter"))
                            .fillParentMaxSize(),
                        message = when (state.filter) {
                            ChatListFilter.Unread -> R.string.title_noUnreadChats
                            ChatListFilter.Groups -> R.string.title_noGroupChats
                            ChatListFilter.All -> R.string.title_allChatsArchived
                        },
                    )
                }
                else -> tipChatItems(
                    chats = visible,
                    onClick = { chat ->
                        navigator.push(
                            AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId))
                        )
                    },
                    // The same sheet the chat and group profiles open, so the list offers exactly
                    // the durations they do, and unmuting is its "Never" row rather than a toggle.
                    onMute = { chat -> navigator.push(AppRoute.Messaging.MuteChat(chat.chatId, chat.chatType)) },
                    onArchive = { chat ->
                        viewModel.archive(chat.chatId)
                        // The row leaves the list at once, so offer to put it back.
                        scope.launch {
                            val host = scaffoldState.snackbarHostState
                            val result = host.showSnackbar(SnackData(message = archivedMessage, actionLabel = undoLabel))
                            if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(chat.chatId)
                        }
                    },
                    revealGroup = revealGroup,
                    parkFiller = parkFiller,
                )
            }

            // Makes up a list shorter than the viewport, so it can still park; see [ParkFiller].
            item(key = "park_filler", contentType = "park_filler") {
                Spacer(with(parkFiller) { filler(parkKeys) })
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
    onArchive: (ConversationReference) -> Unit,
    revealGroup: SwipeRevealGroup,
    parkFiller: ParkFiller,
) {
    // Keyed by chat so a row's swipe state stays with its chat when new activity reorders the list.
    // animateItem fades a row out where it stands when it is archived (or filtered away), even
    // swiped open, slides its neighbours into the gap, and fades it back in on Undo.
    itemsIndexed(chats, key = { _, chat -> chat.chatId }) { index, chat ->
        ChatSwipeRow(
            modifier = Modifier.animateItem().then(parkFiller.tracked(chat.chatId)),
            isMuted = rememberIsMuted(chat.viewerState),
            onMute = { onMute(chat) },
            onArchive = { onArchive(chat) },
            stateKey = chat.chatId,
            revealGroup = revealGroup,
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
 * Two trailing swipe actions: archive inside, mute outside. [SwipeActionRow] draws the last action
 * outermost and fires it on a full swipe, so a full swipe still opens the mute sheet as before, and
 * archive is a tap on the revealed action.
 *
 * The mute icon names the state the chat would be moved out of: a crossed-out bell on an audible
 * chat, a plain one on a muted chat, where the sheet is also how the mute is cleared. It never
 * mutes directly, because a mute always needs a duration. It resets rather than settling open: a
 * row left swiped under the sheet would still be sitting open once the sheet closed.
 *
 * Archive removes the row, which disposes its swipe state, so Undo brings the chat back as a fresh,
 * closed row.
 */
@Composable
private fun ChatSwipeRow(
    isMuted: Boolean,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    stateKey: Any,
    revealGroup: SwipeRevealGroup,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val muteLabel = stringResource(
        if (isMuted) R.string.content_description_changeMute
        else R.string.content_description_muteChat
    )
    val archiveLabel = stringResource(R.string.content_description_archiveChat)
    SwipeActionRow(
        actions = listOf(
            SwipeAction(
                background = CodeTheme.colors.surfaceVariant,
                onTriggered = onArchive,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Archive,
                    contentDescription = archiveLabel,
                    tint = CodeTheme.colors.textMain,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            },
            SwipeAction(
                // The profile's unverified badge treatment rather than the delete red: nothing is
                // lost by it.
                background = CodeTheme.colors.warning.copy(alpha = 0.15f),
                onTriggered = onMute,
                resetOnDismiss = true,
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.Outlined.Notifications
                    } else {
                        Icons.Outlined.NotificationsOff
                    },
                    contentDescription = muteLabel,
                    tint = CodeTheme.colors.warning,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            },
        ),
        // A swipe is out of reach with a screen reader, so the row offers the same actions there.
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(muteLabel) { onMute(); true },
                CustomAccessibilityAction(archiveLabel) { onArchive(); true },
            )
        },
        stateKey = stateKey,
        revealGroup = revealGroup,
        content = content,
    )
}

/**
 * The filter chips. All carries no number: the tab badge already shows it. Unread and Groups show
 * theirs only when non-zero.
 */
@Composable
private fun ChatFilterRow(
    selected: ChatListFilter,
    unreadCount: Int,
    groupsCount: Int,
    onSelect: (ChatListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        FilterChip(
            label = stringResource(R.string.title_filterAll),
            selected = selected == ChatListFilter.All,
            onClick = { onSelect(ChatListFilter.All) },
        )
        FilterChip(
            label = stringResource(R.string.title_filterUnread),
            selected = selected == ChatListFilter.Unread,
            onClick = { onSelect(ChatListFilter.Unread) },
            count = unreadCount,
        )
        FilterChip(
            label = stringResource(R.string.title_filterGroups),
            selected = selected == ChatListFilter.Groups,
            onClick = { onSelect(ChatListFilter.Groups) },
            count = groupsCount,
        )
    }
}

/**
 * The way into the archived chats. Secondary throughout (icon, label, count, chevron), so it reads
 * as a folder rather than one more chat; the count is the unread archived chats that aren't muted.
 */
@Composable
private fun ArchivedRow(count: Int, onClick: () -> Unit) {
    val description = if (count > 0) {
        stringResource(R.string.content_description_archivedUnread, count)
    } else {
        stringResource(R.string.title_archived)
    }
    val color = CodeTheme.colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.background)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .testTag("archived_row")
            .padding(vertical = CodeTheme.dimens.grid.x3)
            .padding(start = CodeTheme.dimens.inset, end = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        // Centred in the avatar column, so the label lines up with the chat names below.
        Box(
            modifier = Modifier.width(CodeTheme.dimens.staticGrid.x8),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Archive,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.title_archived),
            style = CodeTheme.typography.textMedium,
            color = color,
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                style = CodeTheme.typography.textSmall,
                color = color,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = color,
        )
    }
}

/** A filter with nothing under it, centred in the space it is given (the list viewport). */
@Composable
private fun EmptyFilterState(@StringRes message: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            text = stringResource(message),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewNoChatsYet() {
    NoChatsYet(Modifier.fillMaxSize())
}
