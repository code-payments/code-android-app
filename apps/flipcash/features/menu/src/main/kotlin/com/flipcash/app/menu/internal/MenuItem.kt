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
}

internal data object Deposit : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_deposit)
    override val name: String
        @Composable get() = stringResource(R.string.title_depositUsdc)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnDepositClicked
}

internal data object Withdraw : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_withdraw)
    override val name: String
        @Composable get() = stringResource(R.string.title_withdrawUsdc)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnWithdrawClicked
}

internal data object MyAccount : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_account)
    override val name: String
        @Composable get() = stringResource(R.string.title_myAccount)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnMyAccountClicked
}

internal data object AppSettings : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_settings_outline)
    override val name: String
        @Composable get() = stringResource(R.string.title_appSettings)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnAppSettingsClicked
}

internal data object SwitchAccount : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_switchaccounts)
    override val name: String
        @Composable get() = stringResource(R.string.title_switchAccounts)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnSwitchAccountsClicked
}

internal data object Labs : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(R.string.title_labs)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnLabsClicked
}

internal data object LogOut : MenuItem {
    override val id: Any = UUID.randomUUID().toString()
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_logout)
    override val name: String
        @Composable get() = stringResource(R.string.action_logout)
    override val description: String?
        @Composable get() = null

    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OnLogOutClicked
}
