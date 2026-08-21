package com.flipcash.app.menu.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.menu.R

internal data object MyAccount : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_people_id_card)
    override val name: String
        @Composable get() = stringResource(R.string.title_myAccount)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.MyAccount
    )
}

internal data object AdvancedFeatures : FullMenuItem<MenuScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_maintenance)
    override val name: String
        @Composable get() = stringResource(R.string.title_advancedFeatures)
    override val action: MenuScreenViewModel.Event = MenuScreenViewModel.Event.OpenScreen(
        AppRoute.Menu.AdvancedFeatures
    )
}
