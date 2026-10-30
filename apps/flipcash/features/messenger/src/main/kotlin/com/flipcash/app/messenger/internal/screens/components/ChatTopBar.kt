package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.PushPin
import com.flipcash.app.messenger.internal.screens.openMessageShare
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge.topFade
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatActionHandler
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.ui.MutedIndicator
import com.flipcash.shared.chat.ui.media.ForwardArrow
import com.flipcash.shared.chat.ui.messageFullTime
import com.getcode.navigation.core.CodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.core.expandedTouchTarget
import com.getcode.ui.core.measured
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.utils.KeyboardController
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.util.resources.icons.SpeakerSlash

@Composable
internal fun ChatTopBar(
    navigator: CodeNavigator,
    state: ChatViewModel.State,
    onBarHeightChange: (Dp) -> Unit,
    chatActionHandler: ChatActionHandler,
    dispatch: (ChatViewModel.Event) -> Unit,
    /** Asks the host to confirm a removal: chat, member, first name, group title. Supplied by the screen so the bar needs no Hilt. */
    onConfirmRemove: (ChatId, ID, String, String) -> Unit = { _, _, _, _ -> },
    trailing: @Composable () -> Unit = {},
    /** Controls in the bar's end slot, which sit the same distance from the edge as back does. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bgColor = CodeTheme.colors.background
    val statusBars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Held here rather than in the selection bar: KeyboardController.visible only starts tracking
    // from the composition it is created in, and the bar is composed after a long-press that leaves
    // the IME already up — a controller created there would read it as hidden.
    val keyboard = rememberKeyboardController()
    Box {
        // A message action takes the bar over rather than stacking a second one over it, so the
        // conversation's own actions can't be reached while one is pending. The takeover holds
        // through the edit that a selection can lead to: dropping back to the title bar mid-edit
        // would offer the profile and leave back as the only way out.
        val mode: TopBarMode = when {
            state.editing != null -> TopBarMode.Editing
            // A double tap presents the strip alone, over the ordinary title bar.
            state.selection != null && !state.reactionStripOnly -> TopBarMode.Selecting(state.selection)
            else -> TopBarMode.Conversation
        }
        AnimatedContent(
            modifier = Modifier
                .topFade(bgColor, statusBars)
                .measured { onBarHeightChange(it.height) },
            targetState = mode,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "chat top bar",
        ) { target ->
            when (target) {
                TopBarMode.Conversation -> ConversationTitleBar(navigator, state, chatActionHandler, trailing, actions)
                TopBarMode.Editing -> EditingBar(dispatch)
                is TopBarMode.Selecting -> {
                    val sender = target.selection.sender
                    val groupTitle = (state.subject as? ChatSubject.Group)?.title
                    val moderationTarget = if (sender != null && groupTitle != null) {
                        Triple(sender.userId, sender.displayName.substringBefore(' ').ifEmpty { sender.displayName }, groupTitle)
                    } else {
                        null
                    }
                    MessageSelectionBar(
                    selection = target.selection,
                    pinned = target.selection.messageId in state.pins.pinnedIds,
                    keyboard = keyboard,
                    dispatch = dispatch,
                    // Resolved here rather than in the bar: reporting is its own top-level
                    // flow now, so it needs the chat's id, and this is the nearest scope holding
                    // both that and the navigator.
                    onShareMessage = {
                        keyboard.hideIfVisible {
                            navigator.openMessageShare(target.selection.messageId)
                        }
                    },
                    onReport = {
                        state.chatId?.let { chatId ->
                            navigator.push(
                                AppRoute.Messaging.Report(
                                    ReportSubject.Message(chatId, target.selection.messageId)
                                )
                            )
                        }
                    },
                    // Both name the sender and the group, so neither is offered until the sender's
                    // profile has resolved. The host never sees them on the creator or on a DM:
                    // the capabilities are not granted there.
                    onSilence = moderationTarget?.let { (userId, firstName, groupTitle) ->
                        {
                            state.chatId?.let { chatId ->
                                dispatch(ChatViewModel.Event.ClearMessageSelection)
                                navigator.push(AppRoute.Messaging.SilenceUser(chatId, userId, firstName, groupTitle))
                            }
                        }
                    },
                    onRemove = moderationTarget?.let { (userId, firstName, groupTitle) ->
                        {
                            state.chatId?.let { chatId ->
                                dispatch(ChatViewModel.Event.ClearMessageSelection)
                                onConfirmRemove(chatId, userId, firstName, groupTitle)
                            }
                        }
                    },
                    )
                }
            }
        }
    }
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
    trailing: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    AppBarWithTitle(
        rightContents = actions,
        leftIcon = {
            // The avatar sits just past back and opens the profile, so a thumb that missed the
            // arrow landed in the dead strip before it or on the avatar. Everything before the
            // avatar goes back instead.
            AppBarDefaults.UpNavigation(
                modifier = Modifier.expandedTouchTarget(
                    AppBarDefaults.leadingControlTouchOutset(),
                    onTap = { navigator.pop() },
                ),
                onClick = { navigator.pop() },
            )
        },
        title = {
            Row(
                // Profile open is the tip arm's answer alone (see State.canViewProfile).
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_title")
                    .then(
                        if (state.canViewProfile) {
                            // No indication: the target is the whole title row, so an unbounded
                            // ripple centred on it washed across the bar on every tap. The role
                            // is kept so the row still reads as a button.
                            Modifier.noRippleClickable(role = Role.Button) {
                                chatActionHandler(ChatAction.ViewProfile)
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                ChatSubjectAvatar(
                    subject = state.subject,
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.staticGrid.x8)
                        .clip(CircleShape),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    ) {
                        Text(
                            // Name-or-handle for a DM, the group's title for a group. A DM bar
                            // stays one line (node 9443:9094): the handle is the only identity a
                            // name-less tip counterparty has, so it is the title there rather than
                            // a second row.
                            //
                            // Gives up width to the indicator rather than taking the whole line,
                            // so a long name loses its tail instead of pushing the bell off the bar.
                            modifier = Modifier.weight(1f, fill = false),
                            text = state.subject?.title.orEmpty(),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Beside the name, not at the bar's edge: what is muted is this chat, and
                        // the name is what says which chat. An audible one emits nothing, so the
                        // spacing above is not spent either.
                        MutedIndicator(viewerState = state.viewerState)
                    }
                    // A group always shows its size, whether or not the viewer is in it — the
                    // count is what a join changes, and node 10125:19153 -> 10125:19201 is the
                    // same bar at two values of it. A DM has nothing to count.
                    val group = state.subject as? ChatSubject.Group
                    if (group != null) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.subtitle_chatMemberCount,
                                group.memberCount.toInt(),
                                group.memberCount.toString(),
                            ),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The payment request deck, beside the title rather than in the actions slot, so
                // the bar keeps its height (group requests spec, concept B).
                trailing()
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
    /** Whether the selected message is pinned, so the pin action unpins it instead. */
    pinned: Boolean,
    keyboard: KeyboardController,
    dispatch: (ChatViewModel.Event) -> Unit,
    onShareMessage: () -> Unit,
    onReport: () -> Unit,
    /** Null when the sender is unknown or the viewer cannot silence them. */
    onSilence: (() -> Unit)?,
    /** Null when the sender is unknown or the viewer cannot remove them. */
    onRemove: (() -> Unit)?,
) {
    val capabilities = selection.capabilities
    val body = selection.plainText

    // Order is priority: the first actions keep their icons when the bar runs out of room, and it
    // matches iOS's menu: Copy, Reply, Forward, Edit, Delete, Report. Report goes last: it is the
    // rarest and the only one that is never about your own message, so it is the right one to
    // collapse into the overflow on a narrow screen. Forward hands out the message's link; the
    // sheet it opens has the Copy Link tile, so the bar has no copy-link row of its own.
    // Copy and edit both act on the message's text, so a bubble without any is offered neither.
    val actions = buildList {
        if (body != null && MessageCapability.Copy in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_copy),
                    icon = Icons.Filled.ContentCopy,
                    testTag = "action_copy_message",
                    onClick = { dispatch(ChatViewModel.Event.CopyMessage(body)) },
                )
            )
        }
        if (MessageCapability.Reply in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_reply),
                    icon = Icons.AutoMirrored.Filled.Reply,
                    testTag = "action_reply_message",
                    onClick = { dispatch(ChatViewModel.Event.ReplyRequested(selection)) },
                )
            )
        }
        if (MessageCapability.Link in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_forwardMessage),
                    icon = ForwardArrow,
                    testTag = "action_share_message",
                    onClick = {
                        dispatch(ChatViewModel.Event.ShareMessageRequested)
                        onShareMessage()
                    },
                )
            )
        }
        // Granted by can_pin on a message that can be pinned; whether it pins or unpins is the
        // chat's pinned ids', which the capability doesn't carry.
        if (MessageCapability.Pin in capabilities) {
            add(
                MessageAction(
                    label = stringResource(if (pinned) R.string.action_unpinMessage else R.string.action_pinMessage),
                    icon = Icons.Filled.PushPin,
                    testTag = if (pinned) "action_unpin_message" else "action_pin_message",
                    onClick = {
                        dispatch(
                            if (pinned) {
                                ChatViewModel.Event.UnpinMessage(selection.messageId)
                            } else {
                                ChatViewModel.Event.PinMessage(selection.messageId, body)
                            }
                        )
                    },
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
        if (MessageCapability.Delete in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_delete),
                    icon = Icons.Filled.Delete,
                    testTag = "action_delete_message",
                    onClick = {
                        keyboard.hideIfVisible {
                            dispatch(ChatViewModel.Event.DeleteMessage(selection.messageId))
                        }
                    },
                )
            )
        }
        if (MessageCapability.Report in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.title_report),
                    icon = Icons.Filled.Feedback,
                    testTag = "action_report_message",
                    // Unlike the others this one leaves the chat, so nothing on the way back
                    // dismisses the bar. Clearing it here means the transcript is at rest behind
                    // the report flow, and still at rest when it closes.
                    onClick = {
                        dispatch(ChatViewModel.Event.ReportRequested)
                        onReport()
                    },
                )
            )
        }
    }

    // The host's two, after Report and always under the overflow: they are rare and consequential,
    // so they never take an icon slot however wide the bar is.
    val moderationActions = buildList {
        val firstName = selection.sender?.displayName?.substringBefore(' ').orEmpty()
        if (onSilence != null && MessageCapability.Silence in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_silenceMember, firstName),
                    icon = SpeakerSlash,
                    testTag = "action_silence_member",
                    onClick = onSilence,
                )
            )
        }
        if (onRemove != null && MessageCapability.Remove in capabilities) {
            add(
                MessageAction(
                    label = stringResource(R.string.action_removeMemberNamed, firstName),
                    icon = Icons.Outlined.PersonRemove,
                    testTag = "action_remove_member",
                    isDestructive = true,
                    onClick = onRemove,
                )
            )
        }
    }

    val time = messageFullTime(selection.timestamp)
    val timeStyle = CodeTheme.typography.textMedium
    val timeWidth = with(LocalDensity.current) {
        rememberTextMeasurer().measure(time, timeStyle, maxLines = 1).size.width.toDp()
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
        // When the selected message was sent, not a count: one message is selected at a time, so a
        // count would only ever read "1". iOS gives the same time at the top of its message menu.
        title = {
            Text(
                modifier = Modifier.testTag("selected_message_time"),
                text = time,
                style = timeStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // The time is never cut short: the icons get what it leaves, and the rest go under the overflow.
        rightContents = { MessageActions(actions, moderationActions, reservedWidth = timeWidth + TitleChrome) },
    )
}

/** One thing the selection bar can do to the selected message. */
private data class MessageAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The share of the actions' slot they may occupy before the rest collapse into the overflow.
 *
 * A share rather than a slot count, so the answer tracks the screen: at 40dp a button and 10dp
 * between them, every phone width fits the three actions another participant's message offers, and
 * the two extra on your own collapse into the menu.
 *
 * It is a ceiling, not a width — the row is laid out to its contents, so a bar leaving the budget
 * unspent is no wider for it. The figure only has to clear three buttons and their two gaps, 140dp,
 * on the narrowest phone worth supporting. 0.35 did not, and the miss was small enough to look like
 * it should have worked: the slot is the bar less the app bar's 5dp either side, so a 400dp device
 * budgets against 390dp and lands on 136.5dp — three and a half short of three buttons, with most
 * of the bar standing empty and copy and report in a menu.
 */
private const val ActionBudgetFraction = 0.5f

/**
 * What the bar spends besides the title and the actions, inside the actions' slot: the back
 * button (40dp) in its 5dp-padded slot, and the app bar's content padding, which [TopAppBarBase]
 * takes twice on each side (10dp × 4).
 */
private val TitleChrome = 50.dp + 40.dp

/**
 * How many actions stay as icons in a [barWidth] slot. Extracted so the widths that decide it can
 * be tested without laying a bar out; see `MessageActionCapacityTest`.
 */
internal fun inlineActionCapacity(
    barWidth: Dp,
    buttonSize: Dp,
    spacing: Dp,
    /** Width the title needs whole; the actions only get what is left after it. */
    reservedWidth: Dp = 0.dp,
): Int {
    // n buttons cost n widths and n-1 gaps, so adding one gap to both sides makes it a division.
    fun fit(width: Dp) = ((width + spacing) / (buttonSize + spacing)).toInt()
    return minOf(fit(barWidth * ActionBudgetFraction), fit(barWidth - reservedWidth)).coerceAtLeast(1)
}

@Composable
private fun MessageActions(
    actions: List<MessageAction>,
    overflowOnly: List<MessageAction> = emptyList(),
    reservedWidth: Dp = 0.dp,
) {
    if (actions.isEmpty() && overflowOnly.isEmpty()) return

    // Both match what the app bar itself uses, so the budget is measured in the widths that will
    // actually be laid out.
    val buttonSize = CodeTheme.dimens.staticGrid.x8
    val spacing = CodeTheme.dimens.grid.x2

    BoxWithConstraints {
        val capacity = inlineActionCapacity(maxWidth, buttonSize, spacing, reservedWidth)
        // The overflow needs a slot of its own, so it only pays for itself when it is holding
        // something — the last action is not displaced by a menu that would contain only it.
        // Actions that live only under the overflow take its slot whether or not the others fit.
        val inline = when {
            overflowOnly.isNotEmpty() -> actions.take(capacity - 1)
            actions.size <= capacity -> actions
            else -> actions.take(capacity - 1)
        }

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
            MessageOverflow(actions.drop(inline.size), overflowOnly)
        }
    }
}

@Composable
private fun MessageOverflow(actions: List<MessageAction>, overflowOnly: List<MessageAction> = emptyList()) {
    if (actions.isEmpty() && overflowOnly.isEmpty()) return

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
                    text = { OverflowLabel(action.label, action.isDestructive) },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(CodeTheme.dimens.grid.x5),
                            tint = if (action.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain,
                        )
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
            if (actions.isNotEmpty() && overflowOnly.isNotEmpty()) {
                HorizontalDivider(color = CodeTheme.colors.divider)
            }
            overflowOnly.forEach { action ->
                DropdownMenuItem(
                    text = { OverflowLabel(action.label, action.isDestructive) },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(CodeTheme.dimens.grid.x5),
                            tint = if (action.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain,
                        )
                    },
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
private fun OverflowLabel(text: String, isDestructive: Boolean = false) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = if (isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain,
    )
}
