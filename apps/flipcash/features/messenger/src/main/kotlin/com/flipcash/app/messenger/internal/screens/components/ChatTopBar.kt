package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.core.measured
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.KeyboardController
import com.getcode.ui.utils.rememberKeyboardController

@Composable
internal fun ChatTopBar(
    navigator: CodeNavigator,
    state: ChatViewModel.State,
    chatActionHandler: ChatActionHandler,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    var titleHeight by remember { mutableStateOf(0.dp) }
    val bgColor = CodeTheme.colors.background
    val statusBars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Held here rather than in the selection bar: KeyboardController.visible only starts tracking
    // from the composition it is created in, and the bar is composed after a long-press that leaves
    // the IME already up — a controller created there would read it as hidden.
    val keyboard = rememberKeyboardController()
    Box {
        val edgeHeight = titleHeight + ChatTopEdge.Tail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(edgeHeight)
                .background(ChatTopEdge.brush(bgColor, statusBars, titleHeight, edgeHeight))
        )
        // A message action takes the bar over rather than stacking a second one over it, so the
        // conversation's own actions can't be reached while one is pending. The takeover holds
        // through the edit that a selection can lead to: dropping back to the title bar mid-edit
        // would offer the profile and leave back as the only way out.
        val mode: TopBarMode = when {
            state.editing != null -> TopBarMode.Editing
            state.selection != null -> TopBarMode.Selecting(state.selection)
            else -> TopBarMode.Conversation
        }
        AnimatedContent(
            modifier = Modifier.measured { titleHeight = it.height },
            targetState = mode,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "chat top bar",
        ) { target ->
            when (target) {
                TopBarMode.Conversation -> ConversationTitleBar(navigator, state, chatActionHandler)
                TopBarMode.Editing -> EditingBar(dispatch)
                is TopBarMode.Selecting -> MessageSelectionBar(target.selection, keyboard, dispatch)
            }
        }
    }
}

/**
 * How the transcript meets the bar: a fade to the background colour, opaque behind the status bar
 * and clear by the end of the tail.
 *
 * Carried over from iOS's `TranscriptTopFade`, which exists because the bar has no background of
 * its own. A cash card's amount is large white text, so without this it reads over the title and up
 * into the status bar.
 */
private object ChatTopEdge {

    /**
     * How far the fade runs past the bar's own height. The whole of the ramp from [BarEdgeAlpha] to
     * transparent is spent here, so it is long enough that the ramp's own end doesn't read as a
     * band across whatever bubble it lands on.
     */
    val Tail = 40.dp

    /**
     * How far short of the status bar's bottom edge the fade starts, so the strip behind the clock
     * and battery is solid background rather than a nearly-opaque scrim with a bubble under it.
     */
    private val OpaqueInsetTrim = 12.dp

    /**
     * How much scrim is left where the bar's own bottom edge is. Nearly all of it: a linear ramp
     * across the whole region is down to roughly a third by the title's baseline, and white bubble
     * text reads straight through. The ramp to transparent is spent almost entirely on [Tail],
     * below the bar, where nothing has to stay legible.
     */
    private const val BarEdgeAlpha = 0.9f

    /**
     * Opaque behind the status bar, still mostly opaque where the bar ends, clear by the end of the
     * tail. The middle stop is keyed to the bar's own height rather than a fraction of the ramp, so
     * a taller bar — the selection and editing modes — darkens over its whole height rather than
     * running out partway down.
     */
    fun brush(containerColor: Color, statusBars: Dp, titleHeight: Dp, edgeHeight: Dp): Brush {
        val hold = opaqueStop(statusBars, edgeHeight)
        val barEdge = fraction(titleHeight, edgeHeight).coerceIn(hold, 1f)
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to containerColor,
                hold to containerColor,
                barEdge to containerColor.copy(alpha = BarEdgeAlpha),
                1f to Color.Transparent,
            )
        )
    }

    /** The fraction of [edgeHeight] that stays fully opaque before the gradient starts. */
    private fun opaqueStop(statusBars: Dp, edgeHeight: Dp): Float =
        fraction(statusBars - OpaqueInsetTrim, edgeHeight)

    /** [of] as a fraction of [total], clamped, and 0 before the bar has been measured. */
    private fun fraction(of: Dp, total: Dp): Float =
        if (total <= 0.dp) 0f else (of / total).coerceIn(0f, 1f)
}

/** What the bar is showing. The payload rides along so a crossfade-out still has it. */
private sealed interface TopBarMode {
    data object Conversation : TopBarMode
    data object Editing : TopBarMode
    data class Selecting(val selection: ChatListItem.ContentBubble) : TopBarMode
}

/** Bare back arrow: the composer holds the edit's own cancel and confirm. */
@Composable
private fun EditingBar(dispatch: (ChatViewModel.Event) -> Unit) {
    AppBarWithTitle(
        leftIcon = {
            CircularIconButton(
                onClick = { dispatch(ChatViewModel.Event.CancelEdit) },
                testTag = "action_cancel_edit_from_bar",
            ) { size ->
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_cancelEdit),
                    tint = Color.White,
                    modifier = Modifier.requiredSize(size),
                )
            }
        },
        title = { },
    )
}

@Composable
private fun ConversationTitleBar(
    navigator: CodeNavigator,
    state: ChatViewModel.State,
    chatActionHandler: ChatActionHandler,
) {
    AppBarWithTitle(
        leftIcon = {
            AppBarDefaults.UpNavigation { navigator.pop() }
        },
        title = {
            Row(
                // Profile open is only available for tip DMs (see State.canViewProfile).
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.canViewProfile) {
                            Modifier.unboundedClickable {
                                chatActionHandler(ChatAction.ViewProfile)
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                ParticipantAvatar(
                    participant = state.participant,
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.staticGrid.x8)
                        .clip(CircleShape),
                )

                Text(
                    modifier = Modifier.weight(1f),
                    // Name-or-handle: the bar is one line (node 9443:9094), and the handle is
                    // the only identity a name-less tip DM counterparty has.
                    text = state.participant?.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    )
}

/**
 * The bar a long-press puts up, offering exactly what the transcript resolved for that message.
 *
 * The actions render as icons for as far as the bar's action budget reaches, and whatever is left
 * over goes under an overflow. Which ones that is falls out of the width rather than being named
 * here, so the same set sits flat on a phone and collapses on a narrow one — and an action added
 * later takes its place in the order without a layout decision attached.
 */
@Composable
private fun MessageSelectionBar(
    selection: ChatListItem.ContentBubble,
    keyboard: KeyboardController,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    val capabilities = selection.capabilities
    val body = selection.plainText

    // Order is priority: the first actions keep their icons when the bar runs out of room. Reply
    // leads because it is the most common action and the only one a cash bubble offers — burying it
    // is the one choice that would leave that bubble's bar empty. Delete follows: putting the one
    // action with a confirmation behind a menu makes it a three-tap job.
    val actions = buildList {
        if (MessageCapability.Reply in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_reply),
                    icon = Icons.AutoMirrored.Outlined.Reply,
                    testTag = "action_reply_message",
                    onClick = { dispatch(ChatViewModel.Event.ReplyRequested(selection)) },
                )
            )
        }
        if (MessageCapability.Delete in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_delete),
                    icon = Icons.Outlined.Delete,
                    testTag = "action_delete_message",
                    onClick = {
                        keyboard.hideIfVisible {
                            dispatch(ChatViewModel.Event.DeleteMessage(selection.messageId))
                        }
                    },
                )
            )
        }
        // Copy and edit both act on the message's text, so a bubble without any is offered neither.
        if (body != null && MessageCapability.Copy in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_copy),
                    icon = Icons.Outlined.ContentCopy,
                    testTag = "action_copy_message",
                    onClick = { dispatch(ChatViewModel.Event.CopyMessage(body)) },
                )
            )
        }
        if (body != null && MessageCapability.Edit in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_edit),
                    icon = Icons.Outlined.Edit,
                    testTag = "action_edit_message",
                    onClick = {
                        dispatch(ChatViewModel.Event.EditMessage(selection.messageId, body))
                    },
                )
            )
        }
    }

    AppBarWithTitle(
        leftIcon = {
            CircularIconButton(
                onClick = { dispatch(ChatViewModel.Event.ClearMessageSelection) },
                testTag = "action_clear_message_selection",
            ) { size ->
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_clearMessageSelection),
                    tint = Color.White,
                    modifier = Modifier.requiredSize(size),
                )
            }
        },
        // Nothing in the title slot: one message is selected at a time, so a count would only ever
        // read "1" and the back arrow already says the bar is a selection.
        title = { },
        rightContents = { MessageActions(actions) },
    )
}

/** One thing the selection bar can do to the selected message. */
private data class MessageAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit,
)

/**
 * The share of the bar the actions may occupy before they start collapsing into the overflow.
 *
 * A share rather than a slot count, so the answer tracks the screen: at 40dp a button and 10dp
 * between them, a normal phone fits all three actions and a compact one keeps the first inline
 * with the rest a tap away. It stays a minority of the bar so the cluster still reads as trailing
 * and leaves room for a title, should the selection bar ever grow one.
 */
private const val ActionBudgetFraction = 0.35f

@Composable
private fun MessageActions(actions: List<MessageAction>) {
    if (actions.isEmpty()) return

    // Both match what the app bar itself uses, so the budget is measured in the widths that will
    // actually be laid out.
    val buttonSize = CodeTheme.dimens.staticGrid.x8
    val spacing = CodeTheme.dimens.grid.x2

    BoxWithConstraints {
        // n buttons cost n widths and n-1 gaps, so adding one gap to both sides makes it a division.
        val capacity = ((maxWidth * ActionBudgetFraction + spacing) / (buttonSize + spacing))
            .toInt()
            .coerceAtLeast(1)
        // The overflow needs a slot of its own, so it only pays for itself when it is holding
        // something — the last action is not displaced by a menu that would contain only it.
        val inline = if (actions.size <= capacity) actions else actions.take(capacity - 1)

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            inline.forEach { action ->
                CircularIconButton(onClick = action.onClick, testTag = action.testTag) { size ->
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = Color.White,
                        modifier = Modifier.requiredSize(size),
                    )
                }
            }
            MessageOverflow(actions.drop(inline.size))
        }
    }
}

@Composable
private fun MessageOverflow(actions: List<MessageAction>) {
    if (actions.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        // Which actions end up under here falls out of the width, so a UI test cannot know whether
        // to reach for the icon or the menu. Tagging the button lets it ask.
        AppBarDefaults.Overflow(
            modifier = Modifier.testTag("action_message_overflow"),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            containerColor = CodeTheme.colors.brandLight,
            // Rounded to the sheet corner and dropped clear of the button, so the menu reads as its
            // own surface rather than an extension of the circular icon it hangs from.
            shape = CodeTheme.shapes.extraLarge,
            offset = DpOffset(x = 0.dp, y = CodeTheme.dimens.grid.x2),
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { OverflowLabel(action.label) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun OverflowLabel(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
    )
}
