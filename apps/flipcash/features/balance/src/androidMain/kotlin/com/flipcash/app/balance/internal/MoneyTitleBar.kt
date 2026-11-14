// kmp-audit: AppBarWithLargeTitle is Android-only; WalletScreenContent in commonMain takes this bar through its topBar slot.
package com.flipcash.app.balance.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.flipcash.app.core.AppRoute
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.title_tabMoney
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithLargeTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The Money tab's title bar (node 13729:744): "Money" with the viewer's avatar at the end, which
 * opens You. The Groups tab draws its title through the same bar, so switching between the two tabs
 * leaves the title where it is.
 */
@Composable
internal fun MoneyTitleBar(
    profileAvatar: @Composable () -> Unit,
    dispatchEvent: (WalletViewModel.Event) -> Unit,
) {
    AppBarWithLargeTitle(
        title = stringResource(Res.string.title_tabMoney),
        endContent = {
            Box(
                modifier = Modifier
                    // Add's size, so the avatar takes the place Add has on the Groups tab.
                    .size(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape)
                    .clickable { dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Tabs.Menu)) }
                    .testTag("money_profile"),
                contentAlignment = Alignment.Center,
            ) {
                profileAvatar()
            }
        },
    )
}
