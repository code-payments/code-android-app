package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    state: ChatViewModel.State,
    messages: LazyPagingItems<ChatListItem>,
    separatorConfig: SeparatorConfig,
    otherReadPointer: MessagePointer? = null,
    onAction: ChatActionHandler,
    canViewProfile: Boolean,
) {
    val keyboard = rememberKeyboardController()
    val listState = rememberLazyListState()
    val vibrator = LocalVibrator.current

    CompositionLocalProvider(LocalChatActionHandler provides onAction) {
        HandleMessageReads(listState, messages)

        // Haptic feedback when a new incoming message arrives
        // Track the newest message key — only fire when it changes to an incoming message
        var lastNewestKey by remember { mutableStateOf<Any?>(null) }
        LaunchedEffect(messages) {
            snapshotFlow {
                if (messages.itemCount == 0) null else messages.peek(0)
            }
                .filterNotNull()
                .mapNotNull { it as? ChatListItem.ContentBubble }
                .distinctUntilChanged { old, new -> old.itemKey == new.itemKey }
                .collectLatest { newest ->
                    val prevKey = lastNewestKey
                    lastNewestKey = newest.itemKey
                    if (prevKey != null && !newest.isFromSelf) {
                        vibrator.tick()
                    }
                }
        }

        // Gate insertion animations: only animate items that arrive after the initial page load
        val initialLoadComplete by remember {
            derivedStateOf {
                messages.loadState.refresh is LoadState.NotLoading && messages.itemCount > 0
            }
        }
        var hasLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(initialLoadComplete) {
            if (initialLoadComplete) hasLoaded = true
        }
        // Keys that have already played their insertion animation — persists
        // across item disposal so scrolling away and back doesn't replay.
        val animatedKeys = remember { mutableSetOf<Any>() }

        // The backdrop, read once for everything it covers: the rows, the bubbles' own targets,
        // and the contact card at the start of history all stop taking taps together.
        val selecting = state.selection != null || state.editing != null

        // A message can be long-pressed while it is running under the top bar, which the fade
        // there makes easy to do — and the bar then swaps to the actions for it, so the row the
        // backdrop leaves sharp sits behind the buttons acting on it. Bring it down level with the
        // bar's lower edge before that happens. Selecting and then editing is one focus, not two,
        // so the key doesn't change across that step and the row isn't scrolled twice.
        val focusedMessageId = state.editing?.messageId ?: state.selection?.messageId
        LaunchedEffect(focusedMessageId) {
            if (focusedMessageId == null) return@LaunchedEffect
            val buried = -listState.layoutInfo.headroomAbove(messages, focusedMessageId)
            if (buried > 0) listState.animateScrollBy(buried.toFloat())
        }

        // Holds the focused message at the position it was long-pressed at, against the keyboard
        // shortening the list from below — whether the keyboard came up for the edit or because the
        // composer was tapped with the selection bar still showing. See FocusPin.
        val imeInsets = WindowInsets.ime
        val listBottomPad = CodeTheme.dimens.grid.x2 + contentPadding.calculateBottomPadding()
        val listBottomPadPx = with(LocalDensity.current) { listBottomPad.roundToPx() }
        val focusPin = rememberFocusPin(listState, messages, focusedMessageId, imeInsets, listBottomPadPx)
        // Read live from the auto-scroll effect below, which outlives the composition it launched in.
        val editingMessageId by rememberUpdatedState(state.editing?.messageId)

        // Track when the initial Paging refresh has truly completed (Loading → NotLoading).
        // This avoids showing the ContactInfoContainer before messages arrive,
        // which would cause the list to start scrolled to the wrong position.
        // rememberSaveable so it survives the screen being torn down and rebuilt when the
        // amount-entry step is pushed over the conversation — otherwise the content gate
        // re-arms on pop-back and the list re-settles (visible reflow).
        var refreshSettled by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            snapshotFlow { messages.loadState.refresh }
                .dropWhile { it !is LoadState.Loading }
                .first { it !is LoadState.Loading }
            refreshSettled = true
        }

        LazyColumn(
            // NB: no sheetResignmentBehavior, unlike every other scrolling list in the app. The
            // conversation is a full-screen destination, not a sheet, so there is no dismiss drag
            // for the guard to hand the gesture back to — back is the only way out. It would also
            // read this list wrong: the guard expects a top-anchored list, where index0/offset0 is
            // the edge a downward drag overscrolls past, and this one is reverseLayout, where
            // index0/offset0 is the resting position and a downward drag scrolls into history.
            modifier = modifier
                // The backdrop is modal. While a message is selected or being edited the rest of
                // the transcript sits behind it, so a tap there dismisses what is up rather than
                // reaching the message it landed on — the exit iOS gives its held blur.
                .pointerInput(state.selection != null, state.editing != null) {
                    detectTapGestures {
                        when {
                            state.editing != null -> onAction(ChatAction.CancelEdit)
                            state.selection != null -> onAction(ChatAction.ClearSelection)
                            else -> keyboard.hide()
                        }
                    }
                }
                .holdFocusedMessageInPlace(focusPin, imeInsets, listBottomPadPx),
            state = listState,
            reverseLayout = true,
            // The transcript can't be dragged for as long as the backdrop is up — selection and
            // edit alike. It is behind the same scrim the taps above dismiss, so a drag there has
            // no more business reaching it than a tap does. Arriving messages still move it — see
            // the auto-scroll effect below, which caps how far.
            userScrollEnabled = !selecting,
            contentPadding = PaddingValues(
                top = CodeTheme.dimens.inset + contentPadding.calculateTopPadding(),
                bottom = listBottomPad,
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
            ),
            verticalArrangement = Arrangement.Top,
        ) {
            items(
                count = messages.itemCount,
                key = messages.itemKey { it.itemKey }
            ) { index ->
                val item = messages[index] ?: return@items

                // Cross-item bookkeeping, so it stays with the list rather than the row: a message
                // animates in once, the first time it is laid out after the initial page.
                val animateInsertion = index == 0 && hasLoaded && item.itemKey !in animatedKeys
                if (animateInsertion) animatedKeys.add(item.itemKey)

                val bubble = item as? ChatListItem.ContentBubble
                // Selecting or editing a message pushes the rest of the transcript behind a blur,
                // leaving the one message sharp — the same treatment iOS holds from its context
                // menu through the edit that can follow it.
                val focused = when {
                    // The delete confirmation is modal, so nothing behind it is the focus: the
                    // selected message drops back with the rest rather than sitting sharp and
                    // half-clipped where the sheet cuts across it.
                    state.confirmingDelete -> false
                    state.editing != null -> bubble?.messageId == state.editing.messageId
                    state.selection != null -> bubble?.itemKey == state.selection.itemKey
                    else -> true
                }

                MessageRow(
                    index = index,
                    item = item,
                    messages = messages,
                    separatorConfig = separatorConfig,
                    otherReadPointer = otherReadPointer,
                    selecting = selecting,
                    focused = focused,
                    animateInsertion = animateInsertion,
                )
            }

            // Show trailing separator and contact info once messages are available
            // (from Room cache) or after the refresh settles (for empty conversations).
            // This prevents these items from being the only content before messages
            // load, which would cause the list to start at the wrong scroll position.
            if (messages.itemCount > 0 || refreshSettled) {
                // Trailing date separator for the oldest loaded message.
                // This replaces the `after == null` boundary from insertSeparators
                // which Paging 3 defers until endOfPaginationReached, causing a
                // visible frame delay where the message appears without its separator.
                if (messages.itemCount > 0) {
                    val oldest = messages.peek(messages.itemCount - 1)
                    val oldestTimestamp = when (oldest) {
                        is ChatListItem.ContentBubble -> oldest.timestamp
                        is ChatListItem.DateSeparator -> null // already a separator
                        null -> null
                    }
                    if (oldestTimestamp != null) {
                        item(key = "trailing-date-${oldestTimestamp.epochSeconds}") {
                            Box(
                                modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x2),
                            ) {
                                DateSeparatorRow(oldestTimestamp)
                            }
                        }
                    }
                }

                // Chat start shows contact info container
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContactInfoContainer(
                            participant = state.participant,
                            modifier = Modifier
                                .fillMaxWidth(0.63f),
                            onRefreshContact = { onAction(ChatAction.RefreshContact) },
                            // null hides the chevron and makes the card non-tappable when the
                            // profile isn't viewable (non-tip-DM chats).
                            onOpenProfile = if (canViewProfile && !selecting) {
                                { onAction(ChatAction.ViewProfile) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        // Scroll to bottom when the newest message changes (sent or received)
        LaunchedEffect(listState, messages) {
            snapshotFlow {
                if (messages.itemCount == 0) null
                else (messages.peek(0) as? ChatListItem.ContentBubble)?.itemKey
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    // Always scroll for own messages; only near-bottom for incoming
                    val nearBottom = listState.firstVisibleItemIndex <= 5
                    val newest = messages.peek(0) as? ChatListItem.ContentBubble
                    val editing = editingMessageId
                    if (editing != null) {
                        // A message arriving mid-edit still moves the transcript, or it would land
                        // off the bottom edge with nothing to say it had arrived. What it can't do
                        // is carry the row being edited away: the scroll stops at the point that
                        // row would pass under the top bar, and the pin holds it there.
                        if (newest?.isFromSelf == true || nearBottom) {
                            val room = listState.layoutInfo.headroomAbove(messages, editing)
                            // Negative is toward index 0. The list settles on the newest message by
                            // itself if that comes before the cap.
                            if (room > 0) listState.animateScrollBy(-room.toFloat())
                        }
                        return@collectLatest
                    }
                    when {
                        // Own message: anchor the new bubble during the next measure pass
                        // rather than animating to it. An animated scroll resolves its target
                        // offset up front, so the previous message's receipt collapsing out
                        // from under it moves the target mid-flight and the list overshoots
                        // and corrects — read as the bubble bouncing. iOS never hand-rolls
                        // this scroll at all: ChatLayout's keepContentOffsetAtBottomOnBatchUpdates
                        // pins the bottom edge across the one batch that both inserts the new
                        // cell and reconfigures the old one's receipt away.
                        newest?.isFromSelf == true -> listState.requestScrollToItem(0, 0)
                        nearBottom -> listState.animateScrollToItem(0)
                    }
                }
        }

        // Opt out of the list maintaining scroll position when adding
        // elements before the first item. Only needed during initial load
        // (to prevent starting at the ContactInfoContainer) and when
        // scrolled back (to prevent shifting when pagination prepends).
        // When at index 0, we do NOT call requestScrollToItem — doing so
        // forces an instant reposition that causes a single-frame jitter
        // when new messages are inserted. Instead, animateScrollToItem in
        // the LaunchedEffect above handles smooth scrolling to new items.
        Snapshot.withoutReadObservation {
            if (!hasLoaded) {
                listState.requestScrollToItem(0, 0)
            }
        }
    } // CompositionLocalProvider
}
