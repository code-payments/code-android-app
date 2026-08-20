package com.flipcash.app.menu.internal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.LocalTabBarPadding
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
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White50
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
            // v2 has no app bar — the card is the first thing on the page (node 9276:4634). v1
            // keeps the Settings sheet's title + Close.
            if (!isNewUi) {
                AppBarWithTitle(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.title_settings),
                    titleAlignment = Alignment.CenterHorizontally,
                    endContent = { AppBarDefaults.Close { navigator.hide() } },
                )
            }
        },
        bottomBar = {
            // v1 pins the version footer above the nav bar; v2 scrolls it with the content (footer slot).
            if (!isNewUi) {
                VersionFooter(
                    viewModel = viewModel,
                    state = state,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                )
            }
        }
    ) { padding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            items = state.items,
            showChevrons = isNewUi,
            header = {
                if (isNewUi) {
                    YouHeader(
                        card = state.tipCard,
                        link = state.tipLink,
                        onCopyLink = { viewModel.dispatchEvent(Event.CopyTipLink) },
                        onShare = { viewModel.dispatchEvent(Event.ShareTipCard) },
                        onDownload = { viewModel.dispatchEvent(Event.DownloadTipCard) },
                    )
                } else {
                    MoneyTiles(viewModel, navigator)
                }
            },
            footer = {
                if (isNewUi) {
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
                }
            },
            // v2's tab bar is a hoisted overlay drawn ABOVE this content, so reserve its height as
            // bottom content padding — the list then scrolls clear of the bar instead of running
            // under it (the version footer was landing behind it). Per-entry via LocalTabBarPadding,
            // which is only non-zero for tab homes. v1 has no such bar.
            contentPadding = PaddingValues(
                // No app bar in v2, so the page owns its own status-bar clearance; the design puts
                // the card 74dp below it (node 9278:7301).
                top = if (isNewUi) {
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + CardTopSpacing
                } else {
                    CodeTheme.dimens.grid.x3
                },
                bottom = LocalTabBarPadding.current.calculateBottomPadding(),
            ),
            onItemClick = {
                viewModel.dispatchEvent(it.action)
            }
        )
    }
}

/** Distance from the status bar to the top of the tip card (node 9278:7301). */
private val CardTopSpacing = 74.dp

/** The at-rest card width on the You tab (node 9278:7301: 241.636). */
private val YouCardWidth = 242.dp

/**
 * The "You" tab header (node 9276:4634): the viewer's own tip card with a "Full Screen" affordance,
 * the copyable tip link, and the Share / Download tiles. The in-page card fades out while its
 * expanded copy is presented in the root bill overlay (opacity, not removal, so nothing reflows on
 * dismiss).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouHeader(
    card: Scannable.TipCard?,
    link: String?,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Static display (no camera behind the card): render it opaque at the design's flattened
        // colour rather than the translucent frosted fill. Figma flattens the card to rgb(16,16,17).
        CompositionLocalProvider(
            LocalTipCardColor provides Color(0xFF101011),
            LocalTipCardBaseAlpha provides 1f,
        ) {
            Box(
                modifier = Modifier
                    // The card pads itself off the status bar for the full-screen overlay; here the
                    // list's content padding already owns that clearance, so consume the inset
                    // rather than paying it twice.
                    .consumeWindowInsets(WindowInsets.statusBarsIgnoringVisibility)
                    .graphicsLayer { alpha = cardAlpha }
                    .noRippleClickable { session.presentOwnTipCard(card) },
                contentAlignment = Alignment.Center,
            ) {
                ScannableRenderer(scannable = card, tipCardWidth = YouCardWidth)
            }
        }

        Spacer(Modifier.height(CodeTheme.dimens.grid.x6))

        Row(
            modifier = Modifier
                .graphicsLayer { alpha = cardAlpha }
                .noRippleClickable { session.presentOwnTipCard(card) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(R.string.action_viewFullScreen),
                style = CodeTheme.typography.textSmall,
                color = White50,
            )
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_chevron_down_medium),
                contentDescription = null,
                tint = White50,
            )
        }

        Spacer(Modifier.height(64.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.grid.x5),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (link != null) {
                TipLinkRow(link = link, onCopy = onCopyLink)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShareTile(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_share_os,
                    label = stringResource(R.string.action_share),
                    onClick = onShare,
                )
                ShareTile(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_file_download,
                    label = stringResource(R.string.action_download),
                    onClick = onDownload,
                )
            }
        }

        Spacer(Modifier.height(19.dp))
    }
}

/** The tip link, tap-to-copy (node 9276:4748). Shown short — the full URL goes to the clipboard. */
@Composable
private fun TipLinkRow(link: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(TileShape)
            .background(White05)
            .clickable { onCopy() }
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_chain_link),
                contentDescription = null,
                tint = White,
            )
            Text(
                text = link.abbreviatedLink(),
                style = CodeTheme.typography.textSmall.copy(fontSize = 15.sp),
                color = White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = null,
            tint = White,
        )
    }
}

/** One of the two square-ish actions under the link (node 9276:4756). */
@Composable
private fun ShareTile(
    modifier: Modifier = Modifier,
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(TileShape)
            .background(White05)
            .clickable { onClick() }
            .padding(vertical = CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(28.dp),
            painter = painterResource(icon),
            contentDescription = null,
            tint = White,
        )
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
    }
}

private val TileShape = RoundedCornerShape(6.dp)

/**
 * `https://app.flipcash.com/tip/<uuid>` -> `app.flipcash.com/tip/b0ced...` (node 9276:4753). The
 * user never types this — it's a recognisable stand-in for the link the copy button puts on the
 * clipboard, so it's cut short rather than ellipsized at whatever width the device happens to give.
 */
private fun String.abbreviatedLink(): String {
    val withoutScheme = substringAfter("://")
    val lastSegment = withoutScheme.substringAfterLast('/')
    if (lastSegment.length <= ABBREVIATED_ID_LENGTH) return withoutScheme
    val prefix = withoutScheme.removeSuffix(lastSegment)
    return "$prefix${lastSegment.take(ABBREVIATED_ID_LENGTH)}..."
}

private const val ABBREVIATED_ID_LENGTH = 5

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
