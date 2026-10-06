package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.ProfileActionButton
import com.flipcash.shared.common.ui.profile.ProfileHeader
import com.flipcash.shared.common.ui.profile.ProfileStatsCard
import com.flipcash.shared.common.ui.profile.ProfileStatusChip
import com.flipcash.shared.common.ui.profile.joinedLabel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.components.toast.ToastBottomClearance
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.flipcash.app.core.chat.ChatStep

/**
 * A DM counterparty's profile, hosted inside the chat's flow so back returns to the chat.
 *
 * The chat is the flow's [ChatViewModel], but nothing here reads it: this screen's own
 * [ChatProfileViewModel] works out for itself whether a DM with this person exists, and every row
 * and the pinned button act on that.
 */
@Composable
internal fun ChatProfileScreen(
    viewModel: ChatProfileViewModel,
    onOpenChat: (ChatId) -> Unit,
) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    PersonProfileScreen(
        viewModel = viewModel,
        onBack = { flowNavigator.back() },
        onOpenChat = onOpenChat,
    )
}

/**
 * Another person's profile, from a chat or from a link: the shared header with the Blocked and
 * Muted chips and Share, the stats card, and the one action pinned to the bottom (Start Chatting,
 * Open Chat or Unblock). Mute, Report and Block are behind the top bar's ⋯.
 */
@Composable
internal fun PersonProfileScreen(
    viewModel: ChatProfileViewModel,
    onBack: () -> Unit,
    onOpenChat: (ChatId) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    // The host decides what opening the chat means (pop back to it, or push it); everything else
    // the profile can ask for is the same wherever it is.
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ChatProfileViewModel.Event.OpenChat -> currentOnOpenChat(event.chatId)
                is ChatProfileViewModel.Event.OpenSendCash ->
                    navigator.push(event.participant.dmRoute(openSendCash = true))
                is ChatProfileViewModel.Event.OpenScreen -> navigator.push(event.route)
                else -> Unit
            }
        }
    }

    StartChattingSheet(
        state = state,
        onConfirm = { viewModel.dispatchEvent(ChatProfileViewModel.Event.ConfirmStartChatting) },
        onDismiss = { viewModel.dispatchEvent(ChatProfileViewModel.Event.DismissPaymentSheet) },
    )

    val person = state.participant as? ChatParticipant.TipUser
    val isSelf = person != null && person.userId == state.selfId
    val pinned = if (isSelf) null else state.pinnedAction
    val share = rememberProfileShare()
    var shareOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var pinnedHeight by remember { mutableStateOf(0.dp) }
    // Nothing is pinned, so nothing for a toast to clear.
    val clearance = if (pinned != null) pinnedHeight else 0.dp
    ToastBottomClearance(clearance)

    val hazeState = rememberHazeState()

    CodeScaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = clearance),
            ) {
                ProfileHeader(
                    modifier = Modifier.hazeSource(hazeState),
                    cover = person?.profile?.coverPicture,
                    access = person?.let { BlobAccessContext.profile(it.userId) } ?: BlobAccessContext.Owned,
                    avatar = { modifier ->
                        ContactAvatar(
                            image = person?.profile?.profilePicture,
                            displayName = person?.displayName.orEmpty(),
                            access = person?.let { BlobAccessContext.profile(it.userId) }
                                ?: BlobAccessContext.Owned,
                            modifier = modifier,
                        )
                    },
                    title = state.participant?.let {
                        it.name ?: stringResource(R.string.title_unnamedUser)
                    }.orEmpty(),
                    subtitle = state.participant?.handle?.takeIf { it != state.participant?.name },
                    // Until the whole profile is here a missing bio means nothing, so it is not shown.
                    body = person?.profile?.bio?.takeIf { state.isFullProfileLoaded },
                    actions = {
                        if (state.isBlocked) {
                            ProfileStatusChip(
                                icon = Icons.Outlined.Block,
                                text = stringResource(R.string.label_blocked),
                            )
                        }
                        if (state.isMuted) {
                            ProfileStatusChip(
                                icon = Icons.Outlined.NotificationsOff,
                                text = stringResource(R.string.label_muted),
                            )
                        }
                        if (person != null) {
                            ProfileActionButton(
                                icon = ImageVector.vectorResource(R.drawable.ic_share_os),
                                contentDescription = stringResource(R.string.action_share),
                                onClick = { shareOpen = true },
                            )
                        }
                    },
                )
                ProfileStatsCard(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.staticGrid.x4),
                    // The fee is a field of the full profile; before it settles there is no honest number.
                    minimumToChat = state.fee?.takeIf { state.profileSettled }?.formatted(),
                    joined = joinedLabel(state.joinDate),
                )
            }

            AppBarWithTitle(
                onBackIconClicked = onBack,
                hazeState = hazeState,
                endContent = {
                    if (person != null && !isSelf) {
                        Box {
                            CircularIconButton(hazeState = hazeState, onClick = { menuOpen = true }) { size ->
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_moreProfileActions),
                                    tint = Color.White,
                                    modifier = Modifier.requiredSize(size),
                                )
                            }
                            ProfileMenu(
                                expanded = menuOpen,
                                items = state.menuItems,
                                isMuted = state.isMuted,
                                onDismiss = { menuOpen = false },
                                onItem = { item ->
                                    when (item) {
                                        ChatProfileAction.Block ->
                                            viewModel.dispatchEvent(ChatProfileViewModel.Event.BlockUser)
                                        ChatProfileAction.Unblock ->
                                            viewModel.dispatchEvent(ChatProfileViewModel.Event.Unblock)
                                        // The picker is where muting and unmuting both live. Pushed
                                        // on the outer navigator: it is a top-level route.
                                        ChatProfileAction.Mute -> state.dmChatId?.let { chatId ->
                                            navigator.push(AppRoute.Messaging.MuteChat(chatId, ChatType.TIP_DM))
                                        }
                                        ChatProfileAction.Report ->
                                            navigator.push(
                                                AppRoute.Messaging.Report(ReportSubject.User(person.userId))
                                            )
                                    }
                                },
                            )
                        }
                    }
                },
            )

            val showFooter = pinned != null && state.dmExists && state.isEncrypted
            if (pinned != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } }
                        .background(CodeTheme.colors.background)
                        .navigationBarsPadding(),
                ) {
                    if (showFooter) {
                        E2eeFooter(
                            isEncrypted = true,
                            onLearnMore = { navigator.push(AppRoute.Messaging.E2eeDmInfo) },
                            clearNavigationBar = false,
                        )
                    }
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x3),
                        buttonState = ButtonState.Filled,
                        text = pinned.label(),
                        isLoading = pinned == ProfilePinnedAction.OpeningChat,
                        onClick = { viewModel.dispatchEvent(ChatProfileViewModel.Event.PinnedActionTapped) },
                    )
                }
            }
        }
    }

    if (shareOpen && person != null) {
        ProfileShareSheetHost(
            person = person,
            onShare = { share(person) },
            onCopyLink = viewModel::copyLink,
            onDismiss = { shareOpen = false },
        )
    }
}

@Composable
private fun ProfilePinnedAction.label(): String = when (this) {
    is ProfilePinnedAction.StartChatting ->
        if (fee != null) stringResource(labelRes(), fee.formatted()) else stringResource(labelRes())
    else -> stringResource(labelRes())
}
