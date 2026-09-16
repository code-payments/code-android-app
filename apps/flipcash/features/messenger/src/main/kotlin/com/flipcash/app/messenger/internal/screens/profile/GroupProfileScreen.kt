package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.features.messenger.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Whether the group's profile offers a way out of the group.
 *
 * Off while the rest of group membership settles: a leave is not something to hand someone before
 * the join it undoes is finished. Everything behind it — the confirmation, the call, the close —
 * is in place, so this is the only line to change when it ships.
 */
private const val SHOW_LEAVE_ACTION = false

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
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val group = state.subject as? ChatSubject.Group

    // A group you have left has nothing left on this screen to act on, so it closes back to the
    // transcript — which is now showing the gate in place of the composer.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.LeftChat>()
            .collect { flowNavigator.back() }
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(onBackIconClicked = { flowNavigator.back() })
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Leaving is the only thing on offer, and only to someone who is in the group — but
            // it is held back for now, so this is the identity header on its own. The leave
            // itself is wired end to end behind [SHOW_LEAVE_ACTION]; flipping it puts the row back.
            items = if (SHOW_LEAVE_ACTION && group?.isMember == true) {
                listOf<MenuItem<ChatViewModel.Event>>(LeaveChat)
            } else {
                emptyList()
            },
            header = {
                GroupProfileHeader(
                    group = group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x7),
                )
            },
            onItemClick = { viewModel.dispatchEvent(it.action) },
        )
    }
}

@VisibleForTesting
@Composable
internal fun GroupProfileHeader(
    group: ChatSubject.Group?,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
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
    }
}
