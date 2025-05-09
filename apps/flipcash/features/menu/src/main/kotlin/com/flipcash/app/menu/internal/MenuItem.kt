package com.flipcash.app.menu.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.features.menu.R
import java.util.UUID

internal sealed interface MenuItem {
    val id: Any

    @get:Composable
    val icon: Painter

    @get:Composable
    val name: String

    @get:Composable
    val description: String?

    val action: MenuScreenViewModel.Event

    val isStaffOnly: Boolean
}

internal abstract class FullMenuItem(
    override val id: Any = UUID.randomUUID().toString(),
    @get:Composable
    override val description: String? = null,
    override val isStaffOnly: Boolean = false
) : MenuItem

internal abstract class StaffMenuItem(
    override val id: Any = UUID.randomUUID().toString(),
    @get:Composable
    override val description: String? = null,
    override val isStaffOnly: Boolean = true
) : MenuItem

internal data object Deposit : FullMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_deposit)
    override val name: String
        @Composable get() = stringResource(R.string.title_depositUsdc)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnDepositClicked
}

internal data object Withdraw : FullMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_withdraw)
    override val name: String
        @Composable get() = stringResource(R.string.title_withdrawUsdc)

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnWithdrawClicked
}

internal data object MyAccount : FullMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_account)
    override val name: String
        @Composable get() = stringResource(R.string.title_myAccount)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnMyAccountClicked
}

internal data object AppSettings : FullMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_settings_outline)
    override val name: String
        @Composable get() = stringResource(R.string.title_appSettings)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnAppSettingsClicked
}

internal data object SwitchAccount : StaffMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(R.string.title_switchAccounts)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnSwitchAccountsClicked
}

internal data object Labs : StaffMenuItem() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnLabsClicked
}

internal data object LogOut : FullMenuItem() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(R.string.action_logout)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnLogOutClicked
}
