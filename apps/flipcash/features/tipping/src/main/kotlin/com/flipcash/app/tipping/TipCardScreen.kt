package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tipping.TipResult
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.flipcash.app.tipping.internal.TipFlowViewModel.Event
import com.flipcash.features.tipping.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.theme.CodeScaffold

/**
 * The user's own tip card — a step in the tipping [TippingFlowScreen] flow, so it shares the flow's
 * [TipFlowViewModel]. Only the chrome differs by [FeatureFlag.NewUi]:
 * - **v2**: the post-profile-setup landing (the flow seeded at TipCard) — the card full-bleed, no
 *   chrome. The primary home for the card is now the "You" tab (the menu), which also owns settings.
 * - **v1**: a sheet step — title bar with back, and a Share action.
 */
@Composable
fun TipCardScreen() {
    val features = LocalFeatureFlags.current
    // Collect rather than snapshot `.value` — the flow is seeded with the flag's default until
    // DataStore emits, so a remembered read freezes the default (see MenuScreenContent).
    val isNewUi by features.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()

    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    if (isNewUi) {
        // Post-profile-setup landing: the user's newly created tip card, full-bleed. Settings now
        // live in the "You" tab (the menu), so this screen no longer carries a settings hamburger.
        Box(modifier = Modifier.fillMaxSize()) {
            TipCardArt(card = state.tipCard, modifier = Modifier.fillMaxSize())
        }
    } else {
        val flowNavigator = rememberFlowNavigator<TipStep, TipResult>()
        CodeScaffold(
            topBar = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x10),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppBarWithTitle(
                        title = stringResource(R.string.title_myTipCard),
                        titleAlignment = Alignment.CenterHorizontally,
                        onBackIconClicked = { flowNavigator.back() },
                    )
                    Text(
                        text = stringResource(R.string.subtitle_myTipCard),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                }
            },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    ) {
                        CircularIconButton(
                            imageSize = CodeTheme.dimens.staticGrid.x6,
                            buttonSize = CodeTheme.dimens.grid.x12,
                            onClick = {
                                viewModel.dispatchEvent(Event.ShareTipCard)
                            }
                        ) { size ->
                            Icon(
                                painter = painterResource(R.drawable.ic_remote_send),
                                contentDescription = null,
                                tint = Color.White,
                                // The send glyph is bottom-heavy (wide tray below a thin arrow),
                                // so geometric centering leaves it sitting visually low in the
                                // circle. Nudge it up slightly to optically center it.
                                modifier = Modifier
                                    .requiredSize(size)
                                    .offset(y = (-1.5).dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_share),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TipCardArt(card = state.tipCard)
            }
        }
    }
}

@Composable
private fun TipCardArt(card: Scannable.TipCard?, modifier: Modifier = Modifier) {
    if (card == null) return
    CompositionLocalProvider(
        // Static display (no camera behind the card), so render it opaque at the design's flattened
        // colour rather than the translucent frosted fill — the 36% alpha otherwise multiplies the
        // app's Brand background and reads incorrectly. Figma flattens the card to rgb(16,16,17).
        LocalTipCardColor provides Color(0xFF101011),
        LocalTipCardBaseAlpha provides 1f,
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ScannableRenderer(scannable = card)
        }
    }
}
