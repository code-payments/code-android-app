package com.flipcash.app.advanced.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.core.R
import com.getcode.util.resources.icons.Delete

/**
 * Node 9279:121978. Access Key, Log Out and Delete Account moved here from My Account — they're
 * recovery/destructive actions, not account details.
 */
internal data object AccessKey : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_hardware_security_key)
    override val name: String
        @Composable get() = stringResource(R.string.title_accessKey)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OnAccessKeyClicked
}

internal data object BillCustomizer : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Palette)
    override val name: String
        @Composable get() = stringResource(R.string.title_billCustomizer)
    override val action: AdvancedFeaturesScreenViewModel.Event = AdvancedFeaturesScreenViewModel.Event.OpenBillPlayground
}

internal data object DeviceLogs : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Description)
    override val name: String
        @Composable get() = stringResource(R.string.title_deviceLogs)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OpenScreen(AppRoute.Menu.DeviceLogs)
}

internal data object BetaFlags : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OpenScreen(AppRoute.Menu.Lab())
}

internal data object LogOut : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(R.string.action_logout)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OnLogOutClicked
}

internal data object DeleteAccount : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(ImageVector.Delete)
    override val name: String
        @Composable get() = stringResource(R.string.action_deleteAccount)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OnDeleteAccountClicked
}

/**
 * Staff/beta only, and dead without Google's Password Manager behind it —
 * `PassphraseCredentialManager.selectCredential()` refuses outright when the flag is off. Sits here
 * rather than on the You tab: it's a beta tool, next to the other one.
 */
internal data object SwitchAccount : StaffMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(R.string.title_switchAccounts)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OnSwitchAccountsClicked
    override val featureFlag: FeatureFlag<*> = FeatureFlag.CredentialManager
}
