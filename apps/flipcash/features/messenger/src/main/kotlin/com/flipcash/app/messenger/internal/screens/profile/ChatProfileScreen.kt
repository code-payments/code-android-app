package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.LocalCodeNavigator
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.ChatMuteStatusChip
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.asSubject
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewerState
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
 * whole group from a screen that names one person. The muted chip follows the row for the same
 * reason: the group's mute is not this person's, so a member's profile doesn't show it. Contact DMs
 * never reach this screen ([com.flipcash.app.messenger.internal.ChatSubject.Contact] answers
 * `canViewProfile` false).
 *
 * The Message and Send Cash shortcuts follow the same split, the other way round: they show on a
 * group member's profile and not on a tip DM's, where they would only reopen the chat behind it.
 * [profileShortcutRecipient] has the whole rule.
 */
@Composable
internal fun ChatProfileScreen(
    viewModel: ChatProfileViewModel,
    chatViewModel: ChatViewModel,
) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val isTipDm = chatState.chatType == ChatType.TIP_DM

    CodeScaffold(
        topBar = {
            AppBarWithTitle(onBackIconClicked = { flowNavigator.back() })
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Mute first, then report, block last: the reversible and routine sits above the one
            // that asks someone else to look, which sits above the one that ends the conversation.
            // Same shape as the group's profile, where leaving holds the last place.
            items = buildList<MenuItem<ChatProfileAction>> {
                if (isTipDm) {
                    add(MuteDm)
                }
                add(ReportUser)
                add(BlockUser)
            },
            header = {
                val recipient = profileShortcutRecipient(
                    participant = state.participant,
                    chatType = chatState.chatType,
                    selfId = state.selfId,
                )
                ProfileHeader(
                    participant = state.participant,
                    joinDate = state.joinDate,
                    // Only where the mute is this person's chat; see the KDoc above.
                    viewerState = chatState.viewerState.takeIf { isTipDm },
                    // Not flowNavigator, for the reason Report isn't: the DM is a top-level route,
                    // so LocalCodeNavigator hands it up and it opens over this chat.
                    shortcuts = recipient?.let { user ->
                        {
                            ProfileShortcuts(
                                cashSymbol = chatState.cashSymbol,
                                onMessage = { navigator.push(user.dmRoute()) },
                                onSendCash = { navigator.push(user.dmRoute(openSendCash = true)) },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        // The gap under the header is the header's own, because MenuList puts
                        // nothing between its header slot and the first row. 40dp matches what
                        // iOS spends here (its 16pt stack spacing plus the row block's 24pt top
                        // inset); without it the mute chip sits against the first row and the
                        // screen reads as one block rather than a title above a list.
                        .padding(
                            top = CodeTheme.dimens.grid.x7,
                            // None under the shortcuts: the first row's own 25dp inset is the
                            // gap, and ProfileHeader matches it above them so they sit centered
                            // between the join date and the list.
                            bottom = if (recipient != null) {
                                0.dp
                            } else {
                                CodeTheme.dimens.grid.x8
                            },
                        ),
                )
            },
            onItemClick = { item ->
                when (item.action) {
                    ChatProfileAction.Block ->
                        viewModel.dispatchEvent(ChatProfileViewModel.Event.BlockUser)
                    // Both muting and unmuting go through the picker, which is why this row
                    // navigates either way rather than acting on one of them here. The outer
                    // navigator, as with Report: the sheet is a top-level route shared with the
                    // chat list, so it opens over the chat rather than inside it.
                    ChatProfileAction.Mute -> chatState.chatId?.let { chatId ->
                        navigator.push(AppRoute.Messaging.MuteChat(chatId, chatState.chatType))
                    }
                    // Not flowNavigator: Report is a top-level route rather than a step of
                    // this flow, and LocalCodeNavigator hands a non-FlowStep route up to its
                    // parent. So it opens over the chat rather than inside it.
                    ChatProfileAction.Report ->
                        (state.participant as? ChatParticipant.TipUser)?.let { participant ->
                            navigator.push(
                                AppRoute.Messaging.Report(
                                    ReportSubject.User(participant.userId)
                                )
                            )
                        }
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
    viewerState: ViewerState? = null,
    shortcuts: (@Composable () -> Unit)? = null,
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
            // Wider than the 5dp that binds the identity lines below it, so the name reads as the
            // start of that block rather than as another line of the picture.
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
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
        // Last, because it is the only line here that is the viewer's setting rather than a fact
        // about the person, and the only one that can stop being true while the screen is open.
        ChatMuteStatusChip(
            viewerState = viewerState,
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            // With the shortcuts below, an empty held line would double the gap above them. This
            // screen only shows them for a group member, where there's no mute row to change it.
            reserveSpace = shortcuts == null,
        )
        // Below everything that describes the person: these act on them, like the rows under the
        // header, but they're the routine ones, so they sit closest to the name. 15dp here plus
        // the mute line's 10dp is 25dp, the same as the first row's inset below them.
        shortcuts?.let { content ->
            Box(modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3)) {
                content()
            }
        }
    }
}

private fun ChatParticipant.TipUser.dmRoute(openSendCash: Boolean = false) =
    AppRoute.Messaging.Chat(
        identifier = ChatIdentifier.ByUser(userId, profile),
        openSendCash = openSendCash,
    )
