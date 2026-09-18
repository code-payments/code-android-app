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
            AppBarWithTitle(onBackIconClicked = { flowNavigator.back() })
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Both rows are for members only: a non-member has no link to hand out and nothing to
            // leave. Membership comes from the roster rather than from the gate, so the leave goes
            // away the moment the leave itself lands, not when the balance rule next re-decides.
            items = buildList<MenuItem<GroupProfileAction>> {
                if (state.groupInviteUrl != null) add(InviteToGroup)
                if (group?.isMember == true) add(LeaveChat)
            },
            header = {
                GroupProfileHeader(
                    group = group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x7),
                )
            },
            onItemClick = { item ->
                when (item.action) {
                    // The same sheet the transcript's own invite CTA opens, pushed on this flow so
                    // it sits over the profile the user asked from.
                    GroupProfileAction.Invite -> flowNavigator.navigateTo(ChatStep.InviteToGroup)
                    GroupProfileAction.Leave ->
                        viewModel.dispatchEvent(ChatViewModel.Event.LeaveChat)
                }
            },
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
