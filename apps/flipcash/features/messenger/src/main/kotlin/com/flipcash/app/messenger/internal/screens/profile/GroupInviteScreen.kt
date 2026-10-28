package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.messenger.internal.InviteLandingState
import com.flipcash.app.messenger.internal.InviteLandingViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.shared.chat.invite.DeadInviteReason
import com.flipcash.shared.common.ui.profile.ChattingGridItem
import com.flipcash.shared.common.ui.profile.GroupChattingGrid
import com.flipcash.shared.common.ui.profile.ProfileHeader
import com.flipcash.shared.common.ui.profile.ProfilePinnedActionBar
import com.flipcash.shared.common.ui.profile.ProfileTopScrollEdge
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * What `/chat/invite#s=…` opens. A member goes straight to the chat; anyone else sees the group
 * and can join with the link, or, once it is dead, ask to join.
 *
 * @param secret the link's secret, or null for a link whose fragment holds none.
 */
@Composable
fun GroupInviteScreen(secret: ByteArray?) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<InviteLandingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.open(secret) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Replaces this screen, so back from the chat does not land on the link again.
                is InviteLandingViewModel.Effect.OpenChat ->
                    navigator.replace(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(effect.chatId)))
                InviteLandingViewModel.Effect.Pop -> navigator.pop()
            }
        }
    }

    GroupInviteContent(
        state = state,
        onBack = { navigator.pop() },
        onJoin = viewModel::join,
        onRequest = viewModel::requestToJoin,
        onCancelRequest = viewModel::cancelRequest,
        onEncryption = { navigator.push(AppRoute.Messaging.E2eePrivateGroupInfo) },
        onReport = { chatId -> navigator.push(AppRoute.Messaging.Report(ReportSubject.Chat(chatId))) },
    )
}

@Composable
internal fun GroupInviteContent(
    state: InviteLandingState,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onEncryption: () -> Unit = {},
    onReport: (ChatId) -> Unit = {},
) {
    when (state.phase) {
        InviteLandingState.Phase.Loading -> InviteLoading(onBack)
        InviteLandingState.Phase.NotFound -> InviteNotice(
            title = stringResource(R.string.title_inviteLinkNotFound),
            body = stringResource(R.string.description_inviteLinkNotFound),
            onBack = onBack,
        )
        InviteLandingState.Phase.Banned -> InviteNotice(
            title = stringResource(R.string.title_inviteBanned),
            body = stringResource(R.string.description_inviteBanned),
            onBack = onBack,
        )
        InviteLandingState.Phase.LoadFailed -> InviteNotice(
            title = stringResource(R.string.error_title_failedToJoinGroup),
            body = stringResource(R.string.error_description_failedToJoinGroup),
            onBack = onBack,
        )
        InviteLandingState.Phase.Preview -> InvitePreview(
            state = state,
            onBack = onBack,
            onJoin = onJoin,
            onRequest = onRequest,
            onCancelRequest = onCancelRequest,
            onEncryption = onEncryption,
            onReport = onReport,
        )
    }
}

@Composable
private fun InviteLoading(onBack: () -> Unit) {
    CodeScaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppBarWithTitle(onBackIconClicked = onBack)
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/** Nodes 12846:148 and 12846:160: a title, a line, and OK, centred under a back button. */
@Composable
private fun InviteNotice(title: String, body: String, onBack: () -> Unit) {
    CodeScaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).testTag("invite_notice")) {
            AppBarWithTitle(onBackIconClicked = onBack)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = CodeTheme.dimens.inset * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                Text(
                    text = title,
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.Bold),
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                CodeButton(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    buttonState = ButtonState.Subtle,
                    text = stringResource(R.string.action_ok),
                    onClick = onBack,
                )
            }
        }
    }
}

/**
 * Nodes 12846:59 and 12846:126: the group as a requester sees it on its profile, with the invite
 * in place of the host-approves line. Built from the profile's own pieces; the profile screen
 * itself is bound to a chat the viewer does not have.
 */
@Composable
private fun InvitePreview(
    state: InviteLandingState,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onEncryption: () -> Unit,
    onReport: (ChatId) -> Unit,
) {
    val group = state.group
    val hazeState = rememberHazeState()
    var pinnedHeight by remember { mutableStateOf(0.dp) }
    var menuOpen by remember { mutableStateOf(false) }
    val unknownName = stringResource(R.string.title_unnamedUser)
    val members = remember(state.members, group?.creator, unknownName) {
        state.members.take(MembersGridLimit).map { member ->
            val profile = member.userProfile
            ChattingGridItem(
                userId = member.userId,
                name = chatterName(profile.displayName, profile.username, unknownName),
                picture = profile.profilePicture,
                isHost = member.userId == group?.creator,
            )
        }
    }

    CodeScaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("group_invite_screen"),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
                    .heightIn(min = maxHeight)
                    .padding(bottom = pinnedHeight),
            ) {
                ProfileHeader(
                    cover = group?.coverPicture,
                    access = group?.let { BlobAccessContext.ChatProfile(it.chatId) } ?: BlobAccessContext.Owned,
                    avatar = { modifier -> ChatSubjectAvatar(subject = group, modifier = modifier) },
                    title = group?.title.orEmpty(),
                    subtitle = null,
                    body = group?.description?.takeIf { it.isNotBlank() },
                    actions = {},
                )

                // A dead link whose reason is not known gets no line rather than a guessed one.
                val note = if (state.dead) {
                    state.deadReason?.let { deadReasonText(it) }
                } else {
                    stringResource(R.string.subtitle_privateGroupInvited)
                }
                if (note != null) {
                    PrivateGroupNote(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x3),
                        summary = note,
                        detail = null,
                    )
                }

                // Nothing is fetched for a link that was dead on arrival; rows that arrived before
                // it died stay.
                GroupChattingGrid(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.staticGrid.x6),
                    chatters = members,
                    title = pluralStringResource(
                        R.plurals.subtitle_memberCount,
                        state.memberCount.toInt(),
                        state.memberCount.toString(),
                    ),
                    onOpen = {},
                )
            }

            ProfileTopScrollEdge(hazeState = hazeState, scrollState = scrollState)

            AppBarWithTitle(
                onBackIconClicked = onBack,
                hazeState = hazeState,
                endContent = {
                    val density = LocalDensity.current
                    var anchorHeight by remember { mutableStateOf(0.dp) }
                    Box(modifier = Modifier.onSizeChanged { anchorHeight = with(density) { it.height.toDp() } }) {
                        CircularIconButton(hazeState = hazeState, onClick = { menuOpen = true }) { size ->
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.action_moreProfileActions),
                                tint = Color.White,
                                modifier = Modifier.requiredSize(size),
                            )
                        }
                        GroupProfileMenu(
                            expanded = menuOpen,
                            items = groupProfileMenuActions(isMember = false),
                            isPrivate = true,
                            anchorHeight = anchorHeight,
                            onDismiss = { menuOpen = false },
                            onItem = { item ->
                                when (item) {
                                    GroupProfileMenuAction.Encryption -> onEncryption()
                                    GroupProfileMenuAction.Report -> group?.chatId?.let(onReport)
                                    // A non-member has no notification setting to change.
                                    GroupProfileMenuAction.Mute -> Unit
                                }
                            },
                        )
                    }
                },
            )

            val join = if (state.dead) {
                PrivateGroupJoin.resolve(isPrivate = true, isMember = false, requestedAt = state.requestedAt)
            } else {
                null
            }
            val bar = Modifier.align(Alignment.BottomCenter)
            if (join != null) {
                PrivateJoinBar(
                    modifier = bar,
                    join = join,
                    hostName = null,
                    lobbyCall = when {
                        !state.requesting -> null
                        state.requestedAt == null -> LobbyCall.Request
                        else -> LobbyCall.Cancel
                    },
                    onRequest = onRequest,
                    onCancel = onCancelRequest,
                    hazeState = hazeState,
                    onHeightChanged = { pinnedHeight = it },
                )
            } else {
                JoinGroupBar(
                    modifier = bar,
                    joining = state.joining,
                    onJoin = onJoin,
                    hazeState = hazeState,
                    onHeightChanged = { pinnedHeight = it },
                )
            }
        }
    }
}

@Composable
private fun JoinGroupBar(
    joining: Boolean,
    onJoin: () -> Unit,
    hazeState: HazeState,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfilePinnedActionBar(
        modifier = modifier,
        text = stringResource(R.string.action_joinGroup),
        onClick = onJoin,
        isLoading = joining,
        enabled = !joining,
        hazeState = hazeState,
        onHeightChanged = onHeightChanged,
    )
}

/** The line that stands where "You've been invited to join" does. */
@Composable
private fun deadReasonText(reason: DeadInviteReason): String = when (reason) {
    DeadInviteReason.EXHAUSTED -> stringResource(R.string.subtitle_inviteExhausted)
    DeadInviteReason.EXPIRED -> stringResource(R.string.subtitle_inviteExpired)
    DeadInviteReason.REVOKED -> stringResource(R.string.subtitle_inviteRevoked)
}
