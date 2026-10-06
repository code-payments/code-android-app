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

private const val RowShare = "share"
private const val RowCard = "card"
private const val RowCopy = "copy"

/**
 * The share sheet for the viewer's own profile, behind `AppRoute.Menu.ShareProfile`.
 *
 * A row does not act on the tap. It asks the navigator to animate the sheet away and runs the
 * action once that has finished, so the system share sheet or the card screen is not opened over a
 * sheet that is still on its way out.
 */
@Composable
fun ProfileShareScreen() {
    val viewModel = hiltViewModel<ProfileShareViewModel>()
    val navigator = LocalCodeNavigator.current
    val profile = remember(viewModel) { viewModel.profile }

    val name = profile?.displayName.orEmpty()
    val handle = profile?.username
    val subtitle = if (handle.isNullOrEmpty()) {
        name
    } else {
        stringResource(R.string.subtitle_shareUserProfile, name, handle)
    }
    val rows = listOf(
        ProfileShareRow(RowShare, R.drawable.ic_share_os, stringResource(R.string.action_shareProfileLink)),
        ProfileShareRow(RowCard, R.drawable.ic_qr_code, stringResource(R.string.action_showProfileCard)),
        ProfileShareRow(RowCopy, R.drawable.ic_copy, stringResource(R.string.action_copyLink)),
    )

    ProfileShareSheet(
        title = stringResource(R.string.title_shareUserProfile),
        subtitle = subtitle,
        rows = rows,
        onRow = { row ->
            navigator.pendingSheetDismiss = {
                when (row.id) {
                    RowShare -> viewModel.share()
                    RowCard -> navigator.push(AppRoute.Menu.ProfileCard)
                    RowCopy -> viewModel.copyLink()
                }
            }
        },
    )
}
