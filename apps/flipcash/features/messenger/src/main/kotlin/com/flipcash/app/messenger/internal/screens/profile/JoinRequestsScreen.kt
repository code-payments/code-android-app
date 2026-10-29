package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.invites.InviteHostViewModel
import com.flipcash.app.messenger.internal.screens.openGroupInvite
import com.flipcash.app.messenger.internal.screens.openInviteLinkShare
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.RequestAge
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Node 11206:60 — who is waiting to join a private group the viewer created, newest first, each
 * with Deny and Approve. Node 11214:67 is the empty state: two lines and Share Group, no icon.
 *
 * Reads the flow's shared [ChatViewModel], which holds the lobby from HostLobbies, so this list,
 * the profile's card and the in-chat anchor all count the same people.
 */
@Composable
internal fun JoinRequestsScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val navigator = LocalCodeNavigator.current
    val group = state.subject as? ChatSubject.Group
    val inviteHost = hiltViewModel<InviteHostViewModel>()
    val canInviteByChat by inviteHost.canInvite.collectAsStateWithLifecycle()
    val canInvite = state.chatId?.let { canInviteByChat[it] } == true
    LaunchedEffect(state.chatId) { state.chatId?.let(inviteHost::refresh) }

    // LobbyUpdate is best-effort, so the list refetches whenever it comes on screen.
    LaunchedEffect(Unit) { viewModel.dispatchEvent(ChatViewModel.Event.JoinRequestsOpened) }

    // One clock for every row's "requested 2h ago".
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1.minutes)
            value = Clock.System.now()
        }
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_joinRequests),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    ) { innerPadding ->
        if (state.joinRequests.isEmpty()) {
            NoJoinRequests(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                groupName = group?.groupTitle.orEmpty(),
                canInvite = canInvite,
                onShare = {
                    viewModel.dispatchEvent(ChatViewModel.Event.InviteSheetOpened(GroupInviteSheetSource.PROFILE))
                    if (canInvite) {
                        inviteHost.presentHostSheet(
                            groupName = group?.groupTitle.orEmpty(),
                            onShareInviteLink = { navigator.openInviteLinkShare() },
                            onShareJoinLink = { navigator.openGroupInvite() },
                            onManageInvites = { flowNavigator.navigateTo(ChatStep.ManageInvites) },
                        )
                    } else {
                        navigator.openGroupInvite()
                    }
                },
            )
        } else {
            val unknownName = stringResource(R.string.title_unnamedUser)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = CodeTheme.dimens.grid.x2),
            ) {
                // Above the rows, as on iOS, so it's read before Approve and scrolls away with the list.
                item {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.grid.x1, bottom = CodeTheme.dimens.grid.x3),
                        text = stringResource(R.string.subtitle_joinRequestsFooter),
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                items(state.joinRequests, key = { it.publicKey.byteArray.toList() }) { member ->
                    val userId = member.userProfile.userId
                    JoinRequestRow(
                        member = member,
                        name = chatterName(member.userProfile.displayName, member.userProfile.username, unknownName),
                        age = RequestAge.of(member.enteredAt, now),
                        call = userId?.let { state.joinRequestCalls[it] },
                        onOpen = {
                            userId ?: return@JoinRequestRow
                            flowNavigator.navigateTo(
                                ChatStep.Profile(
                                    contact = ChatParticipant.TipUser(userId, member.userProfile),
                                    origin = ProfileOrigin.GroupMember,
                                )
                            )
                        },
                        onDeny = { viewModel.dispatchEvent(ChatViewModel.Event.DenyJoinRequest(member)) },
                        onApprove = { viewModel.dispatchEvent(ChatViewModel.Event.ApproveJoinRequest(member)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinRequestRow(
    member: LobbyMember,
    name: String,
    age: RequestAge,
    call: JoinRequestCall?,
    onOpen: () -> Unit,
    onDeny: () -> Unit,
    onApprove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ContactAvatar(userProfile = member.userProfile, modifier = Modifier.size(40.dp).clip(CircleShape))
            Column {
                Text(
                    text = name,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (age) {
                        RequestAge.JustNow -> stringResource(R.string.subtitle_requestedJustNow)
                        is RequestAge.Minutes -> stringResource(R.string.subtitle_requestedMinutesAgo, age.count)
                        is RequestAge.Hours -> stringResource(R.string.subtitle_requestedHoursAgo, age.count)
                        is RequestAge.Days -> if (age.count == 1L) {
                            stringResource(R.string.subtitle_requestedYesterday)
                        } else {
                            stringResource(R.string.subtitle_requestedDaysAgo, age.count)
                        }
                    },
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        // Deny greys out while Approve runs, and Approve stays its own size with the spinner in it.
        CodeButton(
            text = stringResource(R.string.action_deny),
            buttonState = ButtonState.Filled10,
            enabled = call == null,
            isLoading = call == JoinRequestCall.Deny,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PillPadding,
            overrideContentPadding = true,
            onClick = onDeny,
        )
        CodeButton(
            text = stringResource(R.string.action_approve),
            buttonState = ButtonState.Filled,
            enabled = call == null || call == JoinRequestCall.Approve,
            isLoading = call == JoinRequestCall.Approve,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PillPadding,
            overrideContentPadding = true,
            onClick = onApprove,
        )
    }
}

@Composable
internal fun NoJoinRequests(
    groupName: String,
    canInvite: Boolean,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(horizontal = CodeTheme.dimens.inset), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = stringResource(R.string.title_noJoinRequests),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )
            Text(
                // The frame wraps this at about two-thirds of the screen width.
                modifier = Modifier.widthIn(max = 280.dp),
                text = stringResource(R.string.subtitle_noJoinRequests, groupName),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            CodeButton(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
                text = stringResource(if (canInvite) R.string.action_shareInviteLinkLong else R.string.action_shareGroup),
                buttonState = ButtonState.Filled10,
                shape = CircleShape,
                style = CodeTheme.typography.textSmall,
                contentPadding = PillPadding,
                overrideContentPadding = true,
                onClick = onShare,
            )
        }
    }
}

private val PillPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
