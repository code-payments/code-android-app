package com.flipcash.app.myaccount.internal.myaccount

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.core.R as CoreR
import com.flipcash.features.myaccount.R

/**
 * Node 9277:121893. Account-shaped settings only — the destructive/diagnostic rows (Access Key,
 * Log Out, Delete Account) moved to Advanced, and the standalone App Settings screen folded its one
 * surviving toggle (Require Biometrics) in here.
 *
 * Require Biometrics is a toggle, not a destination — the screen renders a switch in its trailing
 * slot and routes the tap through a biometric prompt. Its [action] is what a row tap dispatches,
 * same as the switch.
 */
internal data object RequireBiometrics : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = painterResource(R.drawable.ic_biometrics)
    override val name: String
        @Composable get() = stringResource(CoreR.string.title_requireBiometrics)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnBiometricsToggled
}

internal data object Blocklist : FullMenuItem<MyAccountScreenViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Block)
    override val name: String
        @Composable get() = stringResource(R.string.title_blocklist)
    override val action: MyAccountScreenViewModel.Event = MyAccountScreenViewModel.Event.OnBlocklistClicked
}
