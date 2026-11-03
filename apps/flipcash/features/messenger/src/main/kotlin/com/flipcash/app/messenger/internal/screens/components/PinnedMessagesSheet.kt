package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.getcode.ui.utils.AllowSheetExpansionWhenScrollable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.CircularIconButtonDefaults
import com.getcode.ui.core.expandedTouchTarget
import com.getcode.ui.components.AppBarWithTitle
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.flipcash.shared.chat.ui.bubbleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.BottomScrollEdge
import com.getcode.ui.components.TopScrollEdge
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.messenger.internal.pins.PinnedRow
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ContentBubble
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

/**
 * Every visible pin, newest first (L1). Each row is the sender, the message drawn as its transcript
 * bubble, and who pinned it when; a tap closes the list and jumps to the message. Unpin shows only
 * with `can_pin` (L1c without). [full] is set when the list opened because the chat is at its pin
 * cap (L1b): a header asks for an unpin, never naming the cap.
 *
 * @param fullQuote the message waiting on a slot, quoted in the header; null quotes nothing.
 */
@Composable
internal fun PinnedMessagesSheet(
    rows: List<PinnedRow>,
    canUnpin: Boolean,
    full: Boolean,
    fullQuote: String?,
    onOpen: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    AllowSheetExpansionWhenScrollable(listState)
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    // The header's height, so the first row starts below it and the top blur covers it.
    var headerHeight by remember { mutableStateOf(0.dp) }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // As iOS's sheet: the title stays put while rows scroll under it, blurring in at the top and
    // out at the bottom.
    Box(modifier = Modifier.fillMaxWidth().testTag("pinned_messages_sheet")) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                top = headerHeight,
                bottom = navigationBar + CodeTheme.dimens.grid.x4,
            ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            items(rows, key = { it.pin.messageId }) { row ->
                PinnedMessageRow(
                    modifier = Modifier.animateItem(),
                    row = row,
                    canUnpin = canUnpin,
                    onOpen = { onOpen(row.pin.messageId) },
                    onUnpin = { onUnpin(row.pin.messageId) },
                )
            }
        }

        TopScrollEdge(
            hazeState = hazeState,
            listState = listState,
            height = headerHeight,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        BottomScrollEdge(
            hazeState = hazeState,
            listState = listState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .padding(bottom = CodeTheme.dimens.grid.x3),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            // The reactors sheet's header: a centred title and the bar's close at the end, frosted
            // as the invite sheet's is, since rows scroll under it.
            AppBarWithTitle(
                title = stringResource(R.string.title_pinnedMessages),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    val closeLabel = stringResource(R.string.cd_close)
                    // The bar's close is drawn smaller than 48dp; grow its touch area to that.
                    val reach = ((48.dp - CircularIconButtonDefaults.ButtonSize) / 2).coerceAtLeast(0.dp)
                    AppBarDefaults.Close(
                        // The shared close draws its icon with no description.
                        modifier = Modifier.expandedTouchTarget(PaddingValues(reach), onTap = onClose).semantics {
                            contentDescription = closeLabel
                            role = Role.Button
                        },
                        hazeState = hazeState,
                        onClick = onClose,
                    )
                },
            )
            if (full) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .testTag("pinned_messages_full"),
                    text = fullQuote?.let { stringResource(R.string.label_pinsFull, it) }
                        ?: stringResource(R.string.label_pinsFullUntitled),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One pinned message. Only the bubble (jump to the message) and Unpin take taps: the avatar, the
 * name, the pinned-by line and the space around them do nothing, as on iOS.
 */
@Composable
internal fun PinnedMessageRow(
    row: PinnedRow,
    canUnpin: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSize = CodeTheme.dimens.staticGrid.x5
    val gutter = CodeTheme.dimens.grid.x1
    val jumpLabel = stringResource(R.string.action_jumpToMessage)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pinned_message_${row.pin.messageId}"),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Text(
            modifier = Modifier.padding(start = avatarSize + gutter),
            text = row.sender.displayName,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(gutter),
        ) {
            ContactAvatar(
                image = row.sender.picture,
                displayName = row.sender.initialsName.orEmpty(),
                access = BlobAccessContext.profile(row.sender.userId),
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .clearAndSetSemantics { },
            )
            // ContentBubble fills the row but puts [modifier] on the bubble itself, so the jump
            // covers the bubble and not the empty width beside it.
            Box(Modifier.weight(1f)) {
                ContentBubble(
                    item = row.bubble,
                    position = BubblePosition.Solo,
                    interactive = false,
                    leading = true,
                    modifier = Modifier
                        .clip(bubbleShape(BubblePosition.Solo, row.bubble.isFromSelf))
                        .jumpTarget(onOpen)
                        .clearAndSetSemantics {
                            testTag = "pinned_bubble_${row.pin.messageId}"
                            row.spoken?.let { contentDescription = it }
                            role = Role.Button
                            onClick(label = jumpLabel) { onOpen(); true }
                        },
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = avatarSize + gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_pinnedBy, row.pinnedByName, pinnedDate(row)),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            if (canUnpin) {
                Spacer(Modifier.size(CodeTheme.dimens.grid.x2))
                // 48dp to touch, laid out at that height so it sits below the bubble, not over it.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClick = onUnpin)
                        .testTag("action_unpin_${row.pin.messageId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.action_unpinMessage),
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textMain,
                    )
                }
            }
        }
    }
}

@Composable
private fun pinnedDate(row: PinnedRow): String {
    val formatter = remember {
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
        )
    }
    return remember(row.pin.pinnedAt) {
        formatter.format(row.pin.pinnedAt.toJavaInstant().atZone(ZoneId.systemDefault()))
    }
}

/**
 * Takes a tap anywhere on the bubble before its own links and buttons see it, so the whole bubble
 * is one jump. Only the down and the up are consumed: a drag still scrolls the list, and moving
 * past touch slop drops the press.
 */
private fun Modifier.jumpTarget(onClick: () -> Unit): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    this
        .indication(interactions, ripple())
        .pointerInput(interactions) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                down.consume()
                val press = PressInteraction.Press(down.position)
                interactions.tryEmit(press)
                val slop = viewConfiguration.touchSlop
                var released = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        released = !change.isOutOfBounds(size, extendedTouchPadding)
                        break
                    }
                    if ((change.position - down.position).getDistance() > slop) break
                }
                if (released) {
                    interactions.tryEmit(PressInteraction.Release(press))
                    currentOnClick()
                } else {
                    interactions.tryEmit(PressInteraction.Cancel(press))
                }
            }
        }
}
