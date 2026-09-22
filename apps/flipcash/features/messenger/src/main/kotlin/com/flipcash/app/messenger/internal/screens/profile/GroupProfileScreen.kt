package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.LocalCodeNavigator
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.ChatMuteStatusChip
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ViewerState
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance

/**
 * The group's own profile, reached from the info card at the head of its transcript.
 *
 * Driven by the conversation's [ChatViewModel] rather than one of its own: everything on this
 * screen — the title, the picture, the member count, the membership the leave clears — is state the
 * transcript is already holding and keeping current from the roster. A second view model would
 * mean a second copy going stale behind this one.
 */
@Composable
internal fun GroupProfileScreen(viewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val group = state.subject as? ChatSubject.Group

    // Leaving exits the whole chat flow rather than popping this screen, landing back on the chat
    // list: a group you have left is not a conversation you are still in, and closing to the
    // transcript would leave you reading it from behind the gate you just put yourself outside of.
    // Same exit the profile's block action takes, for the same reason — FlowHost.onExit pops the
    // Chat route.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.LeftChat>()
            .collect { flowNavigator.exitCanceled() }
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                onBackIconClicked = { flowNavigator.back() },
                endContent = {
                    GroupProfileOverflow(
                        // Server-computed, and absent entirely for a non-member, so an
                        // unresolved viewer state reads as "may not edit" rather than as a
                        // permission to be re-derived here.
                        canEdit = state.viewerState?.permissions?.canEdit == true,
                        onAction = { action ->
                            when (action) {
                                GroupProfileAction.Edit ->
                                    flowNavigator.navigateTo(ChatStep.EditGroup)
                                // The overflow carries the edit row alone; the rest of
                                // [GroupProfileAction] is the list's.
                                else -> Unit
                            }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Every other row is for members only: a non-member has no link to hand out, nothing
            // to leave, and no viewer state on a chat they are not in. Membership comes from the
            // roster rather than from the gate, so the rows go away the moment the leave itself
            // lands, not when the balance rule next re-decides.
            //
            // Reporting is the exception, and deliberately so: a group you have already left is
            // the one you are most likely to report.
            items = buildList<MenuItem<GroupProfileAction>> {
                if (state.groupInviteUrl != null) add(InviteToGroup)
                if (group?.isMember == true) add(MuteChat)
                add(ReportGroup)
                if (group?.isMember == true) add(LeaveChat)
            },
            header = {
                GroupProfileHeader(
                    group = group,
                    viewerState = state.viewerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The gap under the header is the header's own, because MenuList puts
                        // nothing between its header slot and the first row. 40dp matches what
                        // iOS spends here (its 16pt stack spacing plus the row block's 24pt top
                        // inset); without it the mute chip sits against the first row and the
                        // screen reads as one block rather than a title above a list.
                        .padding(
                            top = CodeTheme.dimens.grid.x7,
                            bottom = CodeTheme.dimens.grid.x8,
                        ),
                )
            },
            onItemClick = { item ->
                when (item.action) {
                    // The same sheet the transcript's own invite CTA opens, pushed on this flow so
                    // it sits over the profile the user asked from.
                    GroupProfileAction.Invite -> flowNavigator.navigateTo(ChatStep.InviteToGroup)
                    // Both muting and unmuting go through the picker, which is why this row
                    // navigates either way rather than acting on one of them here.
                    GroupProfileAction.Mute -> flowNavigator.navigateTo(ChatStep.MuteChat)
                    GroupProfileAction.Leave ->
                        viewModel.dispatchEvent(ChatViewModel.Event.LeaveChat)
                    // The outer navigator, not this flow's: reporting is its own top-level
                    // route, so it opens over the chat rather than inside it.
                    GroupProfileAction.Report -> state.chatId?.let { chatId ->
                        navigator.push(
                            AppRoute.Messaging.Report(ReportSubject.Chat(chatId))
                        )
                    }
                    // Edit is an overflow row, not a body row, so this arm is unreachable today.
                    // It routes to the same step the overflow does so that "what Edit means" has
                    // one definition, rather than going stale if the row ever moves into the body.
                    GroupProfileAction.Edit -> flowNavigator.navigateTo(ChatStep.EditGroup)
                }
            },
        )
    }
}

/**
 * The profile's top-right overflow — one row, Edit, and only for a viewer who may use it.
 *
 * Draws nothing at all when the list is empty rather than a disabled button: an overflow that
 * opens on nothing is worse than no overflow, and `canEdit` is stable for the life of the screen
 * in every case but a permission being revoked under it.
 *
 * Shape is the message long-press menu's, from `ChatTopBar.MessageOverflow` — same surface colour,
 * same corner, same drop clear of the button — so the two menus in this feature read as one
 * control rather than two.
 */
@Composable
private fun GroupProfileOverflow(
    canEdit: Boolean,
    onAction: (GroupProfileAction) -> Unit,
) {
    val items = groupProfileOverflowItems(canEdit)
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        AppBarDefaults.Overflow(
            modifier = Modifier.testTag("action_group_profile_overflow"),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            containerColor = CodeTheme.colors.brandLight,
            shape = CodeTheme.shapes.extraLarge,
            offset = DpOffset(x = 0.dp, y = CodeTheme.dimens.grid.x2),
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("action_edit_group"),
                    text = {
                        Text(
                            text = item.name,
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textMain,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(item.action)
                    },
                )
            }
        }
    }
}

@VisibleForTesting
@Composable
internal fun GroupProfileHeader(
    group: ChatSubject.Group?,
    modifier: Modifier = Modifier,
    viewerState: ViewerState? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatSubjectAvatar(
            subject = group,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )
        Text(
            // Wider than the 5dp that binds the identity lines below it, so the name reads as the
            // start of that block rather than as another line of the picture.
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            text = group?.title.orEmpty(),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The size stands where a person's handle does: it is the one thing a group is identified
        // by beyond its name, and it is what a join or a leave changes.
        if (group != null) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = pluralStringResource(
                    R.plurals.subtitle_chatMemberCount,
                    group.memberCount.toInt(),
                    group.memberCount.toString(),
                ),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        // Last, because it is the only line here that is the viewer's setting rather than a fact
        // about the group, and the only one that can stop being true while the screen is open.
        ChatMuteStatusChip(
            viewerState = viewerState,
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
        )
    }
}
