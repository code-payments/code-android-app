package com.flipcash.app.advanced.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.advanced.R

internal data object CurrencyCreator : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Default.AddCircle)
    override val name: String
        @Composable get() = stringResource(R.string.title_createYourCurrency)
    override val action: AdvancedFeaturesScreenViewModel.Event = AdvancedFeaturesScreenViewModel.Event.OpenScreen(
        AppRoute.Token.CurrencyCreator
    )
}

internal data object Deposit : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_menu_deposit)
    override val name: String
        @Composable get() = stringResource(R.string.title_depositFunds)
    override val action: AdvancedFeaturesScreenViewModel.Event = AdvancedFeaturesScreenViewModel.Event.OpenScreen(
        AppRoute.Sheets.TokenSelection(purpose = TokenPurpose.Deposit)
    )
}

internal data object DeviceLogs : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Description)
    override val name: String
        @Composable get() = stringResource(R.string.title_deviceLogs)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OpenScreen(AppRoute.Menu.DeviceLogs)
}