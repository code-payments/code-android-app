package com.flipcash.app.menu.internal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.menu.MenuList
import com.flipcash.app.menu.internal.MenuScreenViewModel.Event
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.menu.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun MenuScreenContent(viewModel: MenuScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val appUpdater = LocalAppUpdater.current

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
                title = stringResource(R.string.title_you),
                titleAlignment = Alignment.CenterHorizontally,
                // The You tab is entered by tab selection, so it has no Close.
            )
        },
    ) { padding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            items = state.items,
            header = {
                YouHeader(
                    card = state.tipCard,
                    onShare = { viewModel.dispatchEvent(Event.ShareTipCard) },
                )
            },
            footer = {
                // Scrolls with the list, so it needs its own breathing room off the last row's
                // divider. No navigationBarsPadding here — the reserved tab-bar inset below
                // already clears the system bar (the bar measures itself with that padding in).
                VersionFooter(
                    viewModel = viewModel,
                    state = state,
                    modifier = Modifier.padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.grid.x3,
                    ),
                )
            },
            // The tab bar is a hoisted overlay drawn ABOVE this content, so reserve its height as
            // bottom content padding — the list then scrolls clear of the bar instead of running
            // under it (the version footer was landing behind it). Per-entry via LocalTabBarPadding,
            // which is only non-zero for tab homes.
            contentPadding = PaddingValues(
                top = CodeTheme.dimens.grid.x3,
                bottom = LocalTabBarPadding.current.calculateBottomPadding(),
            ),
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

/** The "Version … • Build …" footer; its repeated tap toggles beta access (see the ViewModel). */
@Composable
private fun VersionFooter(
    viewModel: MenuScreenViewModel,
    state: MenuScreenViewModel.State,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .noRippleClickable {
                    viewModel.dispatchEvent(Event.OnVersionInfoClicked)
                },
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
