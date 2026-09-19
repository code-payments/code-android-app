package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.asSubject
import com.flipcash.app.messenger.internal.rememberMutedLabel
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatType
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.DateUtils
import com.getcode.view.LoadingSuccessState
import kotlin.time.Instant


/**
 * A DM counterparty's profile.
 *
 * Two view models rather than one, because the screen says two kinds of thing about two different
 * subjects. [viewModel] holds the person — their profile, their join date, whether they are
 * blocked — and is the screen's own. [chatViewModel] is the conversation's, shared with the
 * transcript, and it is what muting goes through: a mute is held by the chat, not by the person,
 * and the same mute is reachable from a group's profile where there is no person at all.
 *
 * The mute row is shown for a tip DM only. This route is also how a group member's profile opens,
 * and there the chat behind it is the group — a mute row on a member's profile would silence the
 * whole group from a screen that names one person. Contact DMs never reach this screen
 * ([com.flipcash.app.messenger.internal.ChatSubject.Contact] answers `canViewProfile` false).
 */
@Composable
internal fun ChatProfileScreen(
    viewModel: ChatProfileViewModel,
    chatViewModel: ChatViewModel,
) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()

    // Asked per composition rather than stored: a timed mute lapses with nothing sent to say so,
    // and this row is where the user would otherwise be looking at a stale Unmute.
    val mutedLabel = rememberMutedLabel(chatState.viewerState)

    CodeScaffold(
        topBar = {
            AppBarWithTitle(onBackIconClicked = { flowNavigator.back() })
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            items = buildList<MenuItem<ChatProfileAction>> {
                add(BlockUser)
                if (chatState.chatType == ChatType.TIP_DM) {
                    add(if (mutedLabel != null) UnmuteDm else MuteDm)
                }
            },
            header = {
                ProfileHeader(
                    participant = state.participant,
                    joinDate = state.joinDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x7),
                )
            },
            onItemClick = { item ->
                when (item.action) {
                    ChatProfileAction.Block ->
                        viewModel.dispatchEvent(ChatProfileViewModel.Event.BlockUser)
                    // Muting needs a shape picked before anything can be asked for; unmuting is
                    // the whole request, so it goes straight to the conversation's view model.
                    ChatProfileAction.Mute -> flowNavigator.navigateTo(ChatStep.MuteChat)
                    ChatProfileAction.Unmute ->
                        chatViewModel.dispatchEvent(ChatViewModel.Event.UnmuteChat)
                }
            },
            endSlot = { item ->
                val loading = item.action == ChatProfileAction.Block &&
                    state.processingState.state == LoadingSuccessState.State.Loading
                if (loading) {
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                    )
                } else if (item.action == ChatProfileAction.Unmute && mutedLabel != null) {
                    // Stating the deadline is what keeps a timed mute distinguishable from a
                    // permanent one — without it both rows read "Unmute".
                    Text(
                        text = mutedLabel,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = CodeTheme.colors.textSecondary,
                    )
                }
            },
        )
    }
}

@VisibleForTesting
@Composable
internal fun ProfileHeader(
    participant: ChatParticipant?,
    joinDate: Instant?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatSubjectAvatar(
            subject = participant.asSubject(),
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = participant?.name.orEmpty(),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The handle sits under the name, the same shape as the info card's identity line
        // (node 9443:8928). Left out when the line above is already the handle, so a name-less
        // account doesn't read it twice.
        participant?.handle?.takeIf { it != participant.name }?.let { handle ->
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = handle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        joinDate?.let { instant ->
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = stringResource(
                    R.string.subtitle_joinedDate,
                    DateUtils.getDate(instant.toEpochMilliseconds(), "MMMM yyyy"),
                ),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
