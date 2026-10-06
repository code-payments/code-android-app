package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.messenger.R
import com.flipcash.shared.common.ui.profile.ProfileShareRow
import com.flipcash.shared.common.ui.profile.ProfileShareSheet
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.launch

/** The DM with [this] person, opened by user, with a payment already started when [openSendCash]. */
internal fun ChatParticipant.TipUser.dmRoute(openSendCash: Boolean = false) =
    AppRoute.Messaging.Chat(
        identifier = ChatIdentifier.ByUser(userId, profile),
        openSendCash = openSendCash,
    )

/** Hands a person's profile to the system share sheet. */
@Composable
internal fun rememberProfileShare(): (ChatParticipant.TipUser) -> Unit {
    val shareController = LocalShareController.current
    val scope = rememberCoroutineScope()
    return remember(shareController, scope) {
        { user ->
            scope.launch {
                shareController.present(
                    Shareable.Profile(
                        userId = user.userId,
                        displayName = user.profile.displayName,
                        username = user.profile.username,
                    )
                )
            }
        }
    }
}

private const val RowShare = "share"
private const val RowCopy = "copy"

/**
 * Share Profile and Copy Link for someone else's profile. No profile-card row: the card is the
 * viewer's own to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileShareSheetHost(
    person: ChatParticipant.TipUser,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val handle = person.profile.username
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeTheme.colors.background,
    ) {
        ProfileShareSheet(
            title = stringResource(R.string.title_shareUserProfile),
            subtitle = if (handle.isNullOrEmpty()) {
                person.profile.displayName
            } else {
                stringResource(R.string.subtitle_shareUserProfile, person.profile.displayName, handle)
            },
            rows = listOf(
                ProfileShareRow(RowShare, R.drawable.ic_share_os, stringResource(R.string.action_shareProfileLink)),
                ProfileShareRow(RowCopy, R.drawable.ic_copy, stringResource(R.string.action_copyLink)),
            ),
            onRow = { row ->
                onDismiss()
                if (row.id == RowShare) onShare() else onCopyLink()
            },
        )
    }
}
