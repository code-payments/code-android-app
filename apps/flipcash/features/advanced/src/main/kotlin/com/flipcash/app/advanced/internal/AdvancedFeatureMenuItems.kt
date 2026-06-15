package com.flipcash.app.advanced.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.core.R

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
    override val showBetaIndicator: Boolean = true
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Filled.Science)
    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)
    override val action: AdvancedFeaturesScreenViewModel.Event =
        AdvancedFeaturesScreenViewModel.Event.OpenScreen(AppRoute.Menu.Lab())
}