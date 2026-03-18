package com.flipcash.app.menu.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.features.menu.R
import com.getcode.util.resources.icons.Delete

internal data object MyAccount : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_account)
    override val name: String
        @Composable get() = stringResource(R.string.title_myAccount)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.MyAccount
    )
}

internal data object Withdraw : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_withdraw)
    override val name: String
        @Composable get() = stringResource(R.string.title_withdrawFunds)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Sheets.TokenSelection(TokenPurpose.Withdraw)
    )
}

internal data object AdvancedFeatures : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_advanced_features)
    override val name: String
        @Composable get() = stringResource(R.string.title_advancedFeatures)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.AdvancedFeatures
    )
}

internal data object AppSettings : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_settings_outline)
    override val name: String
        @Composable get() = stringResource(R.string.title_appSettings)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.AppSettings
    )
}

internal data object SwitchAccount : StaffMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(R.string.title_switchAccounts)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnSwitchAccountsClicked
    override val featureFlag: FeatureFlag = FeatureFlag.CredentialManager
}

internal data object Labs : StaffMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.Lab
    )
}

internal data object LogOut : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(R.string.action_logout)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnLogOutClicked
}
