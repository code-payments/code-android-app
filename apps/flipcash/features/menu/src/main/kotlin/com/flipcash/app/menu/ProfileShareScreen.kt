package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.internal.ProfileShareViewModel
import com.flipcash.features.menu.R
import com.flipcash.shared.common.ui.profile.ProfileShareRow
import com.flipcash.shared.common.ui.profile.ProfileShareSheet
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher

private const val RowShare = "share"
private const val RowCard = "card"

/**
 * The share sheet for the viewer's own profile, behind `AppRoute.Menu.ShareProfile`.
 *
 * A row does not act on the tap. It asks the navigator to animate the sheet away and runs the
 * action once that has finished, so the card screen or the share-to-chats sheet is not opened over
 * a sheet that is still on its way out.
 */
@Composable
fun ProfileShareScreen() {
    val viewModel = hiltViewModel<ProfileShareViewModel>()
    val navigator = LocalCodeNavigator.current
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val profile = remember(viewModel) { viewModel.profile }

    val rows = listOf(
        ProfileShareRow(RowShare, R.drawable.ic_share_os, stringResource(R.string.action_shareProfileLink)),
        ProfileShareRow(RowCard, R.drawable.ic_qr_code, stringResource(R.string.action_showProfileCard)),
    )

    ProfileShareSheet(
        title = stringResource(R.string.title_shareUserProfile),
        rows = rows,
        onRow = { row ->
            // A second tap while the sheet animates out would replace the pending action and run both.
            if (navigator.pendingSheetDismiss != null) return@ProfileShareSheet
            navigator.pendingSheetDismiss = {
                when (row.id) {
                    RowShare -> viewModel.currentUserId?.let { userId ->
                        navigator.push(
                            AppRoute.Messaging.ShareProfileToChats(
                                userId = userId,
                                username = profile?.username,
                                displayName = profile?.displayName,
                                // Your own profile has no DM to leave out.
                                directChatId = null,
                            )
                        )
                    }
                    RowCard -> navigator.push(AppRoute.Menu.ProfileCard)
                }
            }
        },
        onDismiss = dismissSheet,
    )
}
