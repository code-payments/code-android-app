package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.RestrictedKind
import com.flipcash.app.messenger.internal.RestrictedMembersViewModel
import com.flipcash.app.messenger.internal.rememberSilenceDeadline
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.RestrictedUser
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.formatLocalized

/**
 * The host's Silenced list (frame AC8a) and Removed list (AC8b): avatar, name and a status line,
 * with Unsilence or Unban trailing. The row opens the member's profile; the button runs its RPC
 * and takes the row out on success, with no confirm.
 *
 * The group comes off the flow's shared [ChatViewModel]; the lists live in their own view model,
 * scoped to this entry, so leaving and returning reads them again.
 */
@Composable
internal fun RestrictedMembersScreen(kind: RestrictedKind, chatViewModel: ChatViewModel) {
    val viewModel = hiltViewModel<RestrictedMembersViewModel>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatId = chatState.chatId

    LaunchedEffect(chatId) { chatId?.let(viewModel::load) }

    val rows = state.rows(kind)
    val unknownName = stringResource(R.string.title_unnamedUser)

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(
                    if (kind == RestrictedKind.Silenced) R.string.title_silencedMembers else R.string.title_removedMembers
                ),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    ) { innerPadding ->
        if (rows.isEmpty()) {
            // Nothing to show before the read lands or after it failed; the empty copy is for a list that is empty.
            if (!state.loading && !state.failed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (kind == RestrictedKind.Silenced) R.string.title_noSilencedMembers else R.string.title_noRemovedMembers
                        ),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(rows, key = { it.userProfile.userId.orEmpty() }) { user ->
                    val userId = user.userProfile.userId
                    RestrictedRow(
                        kind = kind,
                        user = user,
                        name = chatterName(user.userProfile.displayName, user.userProfile.username, unknownName),
                        isWorking = userId != null && userId in state.pending,
                        onOpen = {
                            userId ?: return@RestrictedRow
                            flowNavigator.navigateTo(
                                ChatStep.Profile(
                                    contact = ChatParticipant.TipUser(userId, user.userProfile),
                                    origin = ProfileOrigin.GroupMember,
                                )
                            )
                        },
                        onAction = {
                            userId ?: return@RestrictedRow
                            val id = chatId ?: return@RestrictedRow
                            when (kind) {
                                RestrictedKind.Silenced -> viewModel.unsilence(id, userId)
                                RestrictedKind.Removed -> viewModel.unban(id, userId)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RestrictedRow(
    kind: RestrictedKind,
    user: RestrictedUser,
    name: String,
    isWorking: Boolean,
    onOpen: () -> Unit,
    onAction: () -> Unit,
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
            ContactAvatar(userProfile = user.userProfile, modifier = Modifier.size(40.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
                Text(
                    text = name,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = restrictionStatus(kind, user),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CodeButton(
            text = stringResource(if (kind == RestrictedKind.Silenced) R.string.action_unsilence else R.string.action_unban),
            buttonState = ButtonState.Filled10,
            enabled = !isWorking,
            isLoading = isWorking,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PillPadding,
            overrideContentPadding = true,
            onClick = onAction,
        )
    }
}

/** "Silenced · until 6:02 PM" or "Silenced · forever"; "Removed Oct 27", plus " · also silenced" when both. */
@Composable
private fun restrictionStatus(kind: RestrictedKind, user: RestrictedUser): String = when (kind) {
    RestrictedKind.Silenced -> when (val silence = user.silence) {
        is SilenceState.Until -> stringResource(R.string.subtitle_silencedUntil, rememberSilenceDeadline(silence.until))
        else -> stringResource(R.string.subtitle_silencedForever)
    }
    RestrictedKind.Removed -> {
        val date = user.ban?.bannedAt?.formatLocalized("MMM d").orEmpty()
        if (user.silence != null) {
            stringResource(R.string.subtitle_removedOnAlsoSilenced, date)
        } else {
            stringResource(R.string.subtitle_removedOn, date)
        }
    }
}
