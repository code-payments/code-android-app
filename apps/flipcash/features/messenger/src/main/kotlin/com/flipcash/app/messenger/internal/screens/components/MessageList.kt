package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.ui.utils.sheetResignmentBehavior
import com.getcode.util.toLocalDate
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface SeparatorConfig {
    val groupingWindow: Duration

    fun shouldSeparate(before: Instant, after: Instant): Boolean
    fun isGrouped(a: Instant, b: Instant): Boolean =
        (a - b).absoluteValue <= groupingWindow

    data object DayOnly : SeparatorConfig {
        override val groupingWindow: Duration = 60.seconds
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
    }

    data class TimeGap(
        val gap: Duration = 3.hours,
        override val groupingWindow: Duration = 60.seconds,
    ) : SeparatorConfig {
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
                    || (before - after).absoluteValue > gap
    }
}

internal enum class ReceiptStatus { SENDING, SENT, READ, FAILED }

internal sealed interface ChatListItem {
    val itemKey: Any
    val itemContentType: Any

    data class DateSeparator(val timestamp: Instant) : ChatListItem {
        override val itemKey: Any = "sep-${timestamp.epochSeconds}"
        override val itemContentType: Any = "date-separator"
    }

    data class ContentBubble(
        val messageId: Long,
        val contentIndex: Int,
        val content: MessageContent,
        val isFromSelf: Boolean,
        val timestamp: Instant,
        val receiptStatus: ReceiptStatus? = null,
        val pendingClientIdHex: String? = null,
    ) : ChatListItem {
        override val itemKey: Any = pendingClientIdHex ?: "$messageId-$contentIndex"
        override val itemContentType: Any = when (content) {
            is MessageContent.Text -> "text-bubble"
            is MessageContent.Cash -> "cash-bubble"
        }
    }
}


@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    state: ChatViewModel.State,
    messages: LazyPagingItems<ChatListItem>,
    separatorConfig: SeparatorConfig,
    otherReadPointer: MessagePointer? = null,
    onAdvanceReadPointer: ((Long) -> Unit)? = null,
) {
    val keyboard = rememberKeyboardController()
    val listState = rememberLazyListState()
    val vibrator = LocalVibrator.current

    if (onAdvanceReadPointer != null) {
        HandleMessageReads(listState, messages, onAdvanceReadPointer)
    }

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

    LazyColumn(
        modifier = modifier
            .sheetResignmentBehavior(listState)
            .pointerInput(Unit) {
                detectTapGestures { keyboard.hide() }
            },
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.inset + contentPadding.calculateTopPadding(),
            bottom = CodeTheme.dimens.grid.x2 + contentPadding.calculateBottomPadding(),
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
            val bottomSpacing = bottomSpacingFor(index, item, messages, separatorConfig)

            val isOutgoing = (item as? ChatListItem.ContentBubble)?.isFromSelf ?: false

            // Message insertion animation — scale from 0.95 + opacity with edge anchor.
            // Tuned to match observed iOS timing (~500ms gradual fade-in).
            // Only animate genuinely new messages (index 0 after initial load).
            val shouldAnimate = index == 0 && hasLoaded
            var appeared by remember { mutableStateOf(!shouldAnimate) }
            LaunchedEffect(Unit) { if (!appeared) appeared = true }
            val insertionAlphaSpec = spring<Float>(dampingRatio = 0.86f, stiffness = 80f)
            val insertionScaleSpec = spring<Float>(dampingRatio = 0.73f, stiffness = 300f)
            val insertionAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = insertionAlphaSpec,
                label = "insertAlpha",
            )
            val insertionScale by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.95f,
                animationSpec = insertionScaleSpec,
                label = "insertScale",
            )

            val insertionModifier = Modifier.graphicsLayer {
                alpha = insertionAlpha
                scaleX = insertionScale
                scaleY = insertionScale
                transformOrigin = if (isOutgoing) {
                    TransformOrigin(1f, 0.5f) // anchor trailing
                } else {
                    TransformOrigin(0f, 0.5f) // anchor leading
                }
            }

            Box(
                modifier = Modifier
                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                    .padding(bottom = bottomSpacing),
            ) {
                when (item) {
                    is ChatListItem.DateSeparator -> Box(insertionModifier) {
                        DateSeparatorRow(item.timestamp)
                    }
                    is ChatListItem.ContentBubble -> {
                        val effectiveStatus = effectiveReceiptStatus(item, otherReadPointer)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
                        ) {
                            Box(insertionModifier) {
                                ContentBubble(
                                    item = item,
                                    position = bubblePositionOf(index, item, messages, separatorConfig),
                                )
                            }
                            val showReceipt =
                                shouldShowReceiptLabel(index, item, messages, otherReadPointer)
                            if (showReceipt && effectiveStatus != null) {
                                ReceiptLabel(
                                    status = effectiveStatus,
                                    readPointer = otherReadPointer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat start shows contact info container (only after messages have loaded)
        if (messages.itemCount > 0) {
            item {
                Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                    ContactInfoContainer(
                        contact = state.chattingWith,
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.grid.x12)
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
                if (newest?.isFromSelf == true || nearBottom) {
                    if (nearBottom) {
                        listState.animateScrollToItem(0)
                    } else {
                        listState.scrollToItem(0)
                    }
                }
            }
    }
}

@Composable
private fun bottomSpacingFor(
    index: Int,
    item: ChatListItem,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
): Dp {
    val tight = CodeTheme.dimens.grid.x1
    val normal = CodeTheme.dimens.grid.x2
    val wide = CodeTheme.dimens.grid.x3
    // index-1 is the item below (newer) in reverseLayout
    val itemBelow = (if (index > 0) messages.peek(index - 1) else null) ?: return tight

    // Separator adjacent → normal gap
    if (item is ChatListItem.DateSeparator || itemBelow is ChatListItem.DateSeparator) {
        return normal
    }

    val current = item as? ChatListItem.ContentBubble ?: return tight
    val below = itemBelow as? ChatListItem.ContentBubble ?: return tight

    return when {
        // Different sender → wide
        current.isFromSelf != below.isFromSelf -> wide
        // Same sender, outside grouping window → normal
        !config.isGrouped(current.timestamp, below.timestamp) -> normal
        // Same sender, close together → tight
        else -> tight
    }
}

@Composable
private fun HandleMessageReads(
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
    onAdvanceReadPointer: (Long) -> Unit,
) {
    var lastAdvanced by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState, messages) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val count = messages.itemCount
            val visibleRange = layout.visibleItemsInfo
            if (visibleRange.isEmpty() || count == 0) return@snapshotFlow null

            var highestId = 0L
            for (info in visibleRange) {
                if (info.index !in 0 until count) continue
                val bubble = messages.peek(info.index) as? ChatListItem.ContentBubble ?: continue
                if (!bubble.isFromSelf && bubble.messageId > highestId) {
                    highestId = bubble.messageId
                }
            }
            if (highestId > 0L) highestId else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { messageId ->
                if (messageId > lastAdvanced) {
                    lastAdvanced = messageId
                    onAdvanceReadPointer(messageId)
                }
            }
    }
}

private fun effectiveReceiptStatus(
    bubble: ChatListItem.ContentBubble,
    otherReadPointer: MessagePointer?,
): ReceiptStatus? {
    val base = bubble.receiptStatus ?: return null
    val pointerValue = otherReadPointer?.value ?: 0L
    if (base == ReceiptStatus.SENT && bubble.messageId in 1..pointerValue) {
        return ReceiptStatus.READ
    }
    return base
}

/**
 * Show a receipt label below a self-message only within the last 2 contiguous
 * self-message groups. In reverseLayout, index 0 is the newest message.
 *
 * Within a group, labels appear at status boundaries (SENT↔READ).
 * At group boundaries, labels are suppressed when the nearest self-group
 * below already shows the same status (avoids duplicate "Read" labels).
 */
private fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    otherReadPointer: MessagePointer?,
): Boolean {
    if (!item.isFromSelf) return false
    val status = effectiveReceiptStatus(item, otherReadPointer) ?: return false
    if (status != ReceiptStatus.SENT && status != ReceiptStatus.READ) return false

    // index - 1 is the item below (newer) in reverseLayout
    val below = if (index > 0) messages.peek(index - 1) else null
    val belowBubble = below as? ChatListItem.ContentBubble

    // Within a self-group: show at intra-group status boundaries only
    if (belowBubble != null && belowBubble.isFromSelf) {
        return effectiveReceiptStatus(belowBubble, otherReadPointer) != status
    }

    // At a group boundary — count which self-group this is.
    var selfGroups = 0
    var prevWasSelf = false
    for (i in 0 until index) {
        val peek = messages.peek(i) ?: break
        val bubble = peek as? ChatListItem.ContentBubble
        val isSelf = bubble != null && bubble.isFromSelf
        if (isSelf && !prevWasSelf) selfGroups++
        prevWasSelf = isSelf
    }
    if (!prevWasSelf) selfGroups++ // current item starts a new group
    if (selfGroups > 2) return false

    // Bottommost self-group always shows its label
    if (selfGroups == 1) return true

    // For group 2: show only if status differs from the nearest self-group below
    for (i in (index - 1) downTo 0) {
        val peek = messages.peek(i) ?: break
        val bubble = peek as? ChatListItem.ContentBubble ?: continue
        if (bubble.isFromSelf) return effectiveReceiptStatus(bubble, otherReadPointer) != status
    }
    return true
}

