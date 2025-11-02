package com.flipcash.app.advanced.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.advanced.R

internal data object Pools : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_flipcash_pools)
    override val name: String
        @Composable get() = stringResource(R.string.title_pools)
    override val action: AdvancedFeaturesScreenViewModel.Event = AdvancedFeaturesScreenViewModel.Event.OpenScreen(
        AppRoute.Advanced.PoolList)
}

internal data object BillCustomizer : FullMenuItem<AdvancedFeaturesScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Palette)
    override val name: String
        @Composable get() = stringResource(R.string.title_billCustomizer)
    override val action: AdvancedFeaturesScreenViewModel.Event = AdvancedFeaturesScreenViewModel.Event.OpenBillPlayground
}