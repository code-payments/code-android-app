package com.flipcash.app.menu.internal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.ui.TileButton
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.menu.MenuList
import com.flipcash.app.menu.internal.MenuScreenViewModel.Event
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.menu.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun MenuScreenContent(viewModel: MenuScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val appUpdater = LocalAppUpdater.current
    val features = LocalFeatureFlags.current
    // v2: this screen is the "You" tab (card + share + settings). v1: it's the Settings sheet.
    // Collect, don't snapshot: observe() is a StateFlow seeded with the flag's DEFAULT (NewUi
    // defaults to true) until DataStore emits the stored value. Reading `.value` inside a remember
    // froze that default, so a v1 build rendered the v2 "You" screen.
    val isNewUi by features.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow
            .filterIsInstance<Event.CheckForUpdate>()
            .onEach { appUpdater.checkForUpdate() }
            .launchIn(this)
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(if (isNewUi) R.string.title_you else R.string.title_settings),
                titleAlignment = Alignment.CenterHorizontally,
                // The You tab is entered by tab selection, so it has no Close; the v1 sheet keeps it.
                endContent = { if (!isNewUi) AppBarDefaults.Close { navigator.hide() } },
            )
        },
        bottomBar = {
            // v1 pins the version footer above the nav bar; v2 scrolls it with the content (footer slot).
            if (!isNewUi) VersionFooter(viewModel, state)
        }
    ) { padding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            items = state.items,
            header = {
                if (isNewUi) {
                    YouHeader(
                        card = state.tipCard,
                        onShare = { viewModel.dispatchEvent(Event.ShareTipCard) },
                    )
                } else {
                    MoneyTiles(viewModel, navigator)
                }
            },
            footer = { if (isNewUi) VersionFooter(viewModel, state) },
            contentPadding = PaddingValues(top = CodeTheme.dimens.grid.x3),
            onItemClick = {
                viewModel.dispatchEvent(it.action)
            }
        )
    }
}

/**
 * The "You" tab header: the viewer's own tip card, tappable to present full screen via the app-root
 * bill overlay, plus a "Share as a Link" button. The in-page card fades out while its expanded copy
 * is presented in the overlay (opacity, not removal, so nothing reflows on dismiss).
 */
@Composable
private fun YouHeader(card: Scannable.TipCard?, onShare: () -> Unit) {
    if (card == null) return
    val session = LocalSessionController.current ?: return
    val billState by session.billState.collectAsStateWithLifecycle()
    val presented = billState.bill is Scannable.TipCard
    val cardAlpha by animateFloatAsState(
        targetValue = if (presented) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "youCardAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
    ) {
        // Static display (no camera behind the card): render it opaque at the design's flattened
        // colour rather than the translucent frosted fill. Figma flattens the card to rgb(16,16,17).
        CompositionLocalProvider(
            LocalTipCardColor provides Color(0xFF101011),
            LocalTipCardBaseAlpha provides 1f,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = cardAlpha }
                    .noRippleClickable { session.presentOwnTipCard(card) },
                contentAlignment = Alignment.Center,
            ) {
                ScannableRenderer(scannable = card, tipCardWidth = 230.dp)
            }
        }

        Text(
            modifier = Modifier
                .clip(CircleShape)
                .background(CodeTheme.colors.surfaceVariant)
                .clickable { onShare() }
                .padding(
                    horizontal = CodeTheme.dimens.grid.x4,
                    vertical = CodeTheme.dimens.grid.x3,
                ),
            text = stringResource(R.string.action_shareAsLink),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}

/** v1 Settings-sheet header: the Add Money / Withdraw tiles (removed from the v2 You tab). */
@Composable
private fun MoneyTiles(
    viewModel: MenuScreenViewModel,
    navigator: CodeNavigator,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        TileButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.action_addMoney),
            icon = painterResource(R.drawable.ic_menu_deposit)
        ) {
            viewModel.dispatchEvent(Event.PresentDepositOptions)
        }

        TileButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.action_withdrawMoney),
            icon = painterResource(R.drawable.ic_menu_withdraw)
        ) {
            navigator.push(AppRoute.Transfers.Withdrawal())
        }
    }
}

/** The "Version … • Build …" footer; its repeated tap toggles beta access (see the ViewModel). */
@Composable
private fun VersionFooter(
    viewModel: MenuScreenViewModel,
    state: MenuScreenViewModel.State,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .noRippleClickable {
                    viewModel.dispatchEvent(Event.OnVersionInfoClicked)
                }
                .navigationBarsPadding()
                .padding(bottom = CodeTheme.dimens.grid.x3),
            text = stringResource(
                R.string.subtitle_appVersionInfoFooter,
                state.appVersionInfo.versionName,
                state.appVersionInfo.versionCode,
                state.releaseTrack,
            ),
            color = CodeTheme.colors.textSecondary,
            style = CodeTheme.typography.textSmall.copy(
                textAlign = TextAlign.Center
            ),
        )
    }
}
