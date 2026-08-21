package com.flipcash.app.menu.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.HideTabBar
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.ui.TileButton
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.menu.MenuList
import com.flipcash.app.menu.internal.MenuScreenViewModel.Event
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
import kotlinx.coroutines.delay
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

    val listState = rememberLazyListState()
    // Full screen is a state of *this* screen, not a destination: the card grows into the middle of
    // the display and everything else — rows, footer, tab bar — animates out from under it
    // (node 9277:121410). Pushing a route would cross-fade a second copy of the card in instead.
    var cardExpanded by remember { mutableStateOf(false) }
    val canExpand = isNewUi && state.tipCard != null

    LaunchedEffect(canExpand) {
        // Losing the card (a v1 build, or sign-out) must not strand the page expanded.
        if (!canExpand) cardExpanded = false
    }
    HideTabBar(hidden = cardExpanded)
    BackHandler(enabled = cardExpanded) { cardExpanded = false }

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // No app bar in v2, so the page owns its own status-bar clearance; the design puts the
            // card 74dp below it (node 9278:7301).
            val restingTop = WindowInsets.statusBars.asPaddingValues()
                .calculateTopPadding() + CardTopSpacing
            // The design's width, narrowed only if the display can't hold it inside the page's
            // margins — same rule iOS applies.
            val expandedCardWidth = minOf(
                FullScreenCardWidth,
                maxWidth - PageHorizontalInset * 2,
            )
            val cardWidth by animateDpAsState(
                targetValue = if (cardExpanded) expandedCardWidth else YouCardWidth,
                animationSpec = expansionSpring(Dp.VisibilityThreshold),
                label = "tipCardWidth",
            )
            // v2's tab bar is a hoisted overlay drawn ABOVE this content, so reserve its height as
            // bottom content padding — the list then scrolls clear of the bar instead of running
            // under it (the version footer was landing behind it). Per-entry via LocalTabBarPadding,
            // which is only non-zero for tab homes. v1 has no such bar; expanding gives the bar back
            // its space because HideTabBar has taken the bar away.
            val tabBarInset = LocalTabBarPadding.current.calculateBottomPadding()
            val bottomInset by animateDpAsState(
                targetValue = if (cardExpanded) 0.dp else tabBarInset,
                animationSpec = expansionSpring(Dp.VisibilityThreshold),
                label = "tabBarInset",
            )
            // How far into the expansion we are: everything but the card fades out on it and slides
            // down out of the way, rather than being removed. Keeping the rows in the layout means
            // nothing reflows on the way back (iOS does the same with opacity + offset).
            val expansion by animateFloatAsState(
                targetValue = if (cardExpanded) 1f else 0f,
                animationSpec = expansionSpring(),
                label = "expansion",
            )
            val slideAway = Modifier.graphicsLayer {
                // Same overshoot: keep the fade inside a legal alpha range.
                alpha = (1f - expansion).coerceIn(0f, 1f)
                translationY = ContentSlideDistance.toPx() * expansion
            }

            // The card doesn't hand off to a second copy of itself: the one in the list keeps its
            // slot and is drawn travelling out of it, the way iOS offsets the card from its own
            // measured frame. Measuring the slot (which never carries the offset) rather than the
            // card keeps the measurement out of its own feedback loop, and because the slot grows
            // with the card, the card is exactly centred by the time the spring settles.
            var cardSlotCenterY by remember { mutableFloatStateOf(0f) }
            val displayCenterY = LocalWindowInfo.current.containerSize.height / 2f
            val cardShift = when {
                cardSlotCenterY <= 0f -> 0f
                else -> (displayCenterY - cardSlotCenterY) * expansion
            }

            MenuList(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                items = state.items,
                showChevrons = isNewUi,
                userScrollEnabled = !cardExpanded,
                itemModifier = if (isNewUi) slideAway else Modifier,
                header = {
                    if (isNewUi) {
                        YouHeader(
                            card = state.tipCard,
                            link = state.tipLink,
                            enabled = !cardExpanded,
                            expansion = expansion,
                            slideAway = slideAway,
                            cardWidth = cardWidth,
                            cardShift = cardShift,
                            onCardSlotPositioned = { cardSlotCenterY = it },
                            onToggleFullScreen = { cardExpanded = !cardExpanded },
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
                        // Scrolls with the list, so it needs its own breathing room off the last
                        // row's divider. No navigationBarsPadding here — the reserved tab-bar inset
                        // below already clears the system bar (the bar measures itself with that
                        // padding in).
                        VersionFooter(
                            viewModel = viewModel,
                            state = state,
                            enabled = !cardExpanded,
                            modifier = slideAway.padding(
                                top = VersionFooterTopSpacing,
                                bottom = CodeTheme.dimens.grid.x3,
                            ),
                        )
                    }
                },
                contentPadding = PaddingValues(
                    top = if (isNewUi) restingTop else CodeTheme.dimens.grid.x3,
                    // Clamped: the spring is underdamped, so it undershoots past the target on the
                    // way to 0, and PaddingValues throws on a negative — taking the Recomposer, and
                    // with it the whole UI, down with it.
                    bottom = bottomInset.coerceAtLeast(0.dp),
                ),
                onItemClick = {
                    // The faded-out rows are still laid out under the expanded card; don't let them
                    // take a tap meant for the card.
                    if (!cardExpanded) viewModel.dispatchEvent(it.action)
                }
            )

            // Close sits at the foot of the display rather than under the card (node 9277:121410).
            AnimatedVisibility(
                visible = cardExpanded,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(expansionSpring()),
                exit = fadeOut(expansionSpring()),
            ) {
                FullScreenToggle(
                    label = stringResource(R.string.action_closeFullScreen),
                    chevronRotation = 180f,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = CloseBottomSpacing)
                        .noRippleClickable { cardExpanded = false },
                )
            }
        }
    }
}

/** Distance from the status bar to the top of the tip card (node 9278:7301). */
private val CardTopSpacing = 74.dp

/** The at-rest card width on the You tab (node 9278:7301: 241.636). */
private val YouCardWidth = 242.dp

/** The expanded card's width (node 9277:121410: 302.21 on a 402 frame); iOS pins the same 302. */
private val FullScreenCardWidth = 302.dp

/** The page's horizontal inset, and so the expanded card's minimum margin. */
private val PageHorizontalInset = 20.dp

/** Gap between the Close row and the system nav bar (node 9277:121410). */
private val CloseBottomSpacing = 8.dp

/**
 * Clearance between the last settings row's divider and the version footer. iOS spends 32 above the
 * footer plus 12 of the footer's own vertical padding on top of the row's 25 inset; the Android row
 * already pays that same 25, so the difference lands here.
 */
private val VersionFooterTopSpacing = 44.dp

/** How far the page's content slides down as it fades out under the expanding card. */
private val ContentSlideDistance = 60.dp

/**
 * The whole expansion — card size, card position, the content sliding away, the Close row — runs on
 * one spring, as iOS does: `.spring(response: 0.45, dampingFraction: 0.85)`. SwiftUI's `response` is
 * the undamped period, so the equivalent Compose stiffness is `(2 * PI / 0.45) ^ 2`.
 */
private fun <T> expansionSpring(visibilityThreshold: T? = null) = spring(
    dampingRatio = 0.85f,
    stiffness = 195f,
    visibilityThreshold = visibilityThreshold,
)

/**
 * The "You" tab header (node 9276:4634): the viewer's own tip card with a "Full Screen" affordance,
 * the copyable tip link, and the Share / Download tiles.
 *
 * The caller drives the full-screen state: it sizes the card ([cardWidth]) and draws it out of its
 * slot towards the middle of the display ([cardShift], off the slot position reported by
 * [onCardSlotPositioned]). It also hands down [slideAway] — the fade-and-slide every non-card
 * element shares — plus [expansion] for the caption, which iOS fades in place rather than sliding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouHeader(
    card: Scannable.TipCard?,
    link: String?,
    enabled: Boolean,
    expansion: Float,
    slideAway: Modifier,
    cardWidth: Dp,
    cardShift: Float,
    onCardSlotPositioned: (Float) -> Unit,
    onToggleFullScreen: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    if (card == null) return

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
                    .onGloballyPositioned { onCardSlotPositioned(it.boundsInRoot().center.y) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = cardShift }
                        .noRippleClickable { onToggleFullScreen() },
                ) {
                    ScannableRenderer(scannable = card, tipCardWidth = cardWidth)
                }
            }
        }

        Spacer(Modifier.height(CodeTheme.dimens.grid.x6))

        // The caption belongs to the card, so it fades where it stands instead of sliding off with
        // the rest of the page.
        FullScreenToggle(
            label = stringResource(R.string.action_viewFullScreen),
            chevronRotation = 0f,
            modifier = Modifier
                .graphicsLayer { alpha = (1f - expansion).coerceIn(0f, 1f) }
                .noRippleClickable { onToggleFullScreen() },
        )

        // Everything under the card gets out of the way so the card can own the display.
        Column(
            modifier = slideAway.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x5),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (link != null) {
                    TipLinkRow(link = link, enabled = enabled, onCopy = onCopyLink)
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
                        enabled = enabled,
                        onClick = onShare,
                    )
                    ShareTile(
                        modifier = Modifier.weight(1f),
                        icon = R.drawable.ic_file_download,
                        label = stringResource(R.string.action_download),
                        enabled = enabled,
                        onClick = onDownload,
                    )
                }
            }

            Spacer(Modifier.height(19.dp))
        }
    }
}

/**
 * The label + chevron that toggles the card's full-screen state — "Full Screen" pointing down under
 * the resting card (node 9276:4634), "Close" pointing up at the foot of the expanded one
 * (node 9277:121410). One glyph, flipped, so the two read as the same control.
 */
@Composable
private fun FullScreenToggle(
    label: String,
    chevronRotation: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        Icon(
            modifier = Modifier
                .size(16.dp)
                .rotate(chevronRotation),
            painter = painterResource(R.drawable.ic_chevron_down_medium),
            contentDescription = null,
            tint = White50,
        )
    }
}

/** The tip link, tap-to-copy (node 9276:4748). Shown short — the full URL goes to the clipboard. */
@Composable
private fun TipLinkRow(link: String, enabled: Boolean, onCopy: () -> Unit) {
    // Bumped rather than latched so a second tap restarts the hold instead of being swallowed.
    var copyToken by remember { mutableIntStateOf(0) }
    val copied = copyToken > 0

    LaunchedEffect(copyToken) {
        if (copyToken > 0) {
            delay(CopyConfirmationMillis)
            copyToken = 0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(TileShape)
            .background(White05)
            .clickable(enabled = enabled) {
                onCopy()
                copyToken++
            }
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
        // Confirms the copy landed, then hands the row back to the copy glyph (mirrors iOS
        // TipCardLinkRow) — the clipboard gives no feedback of its own.
        Crossfade(
            targetState = copied,
            animationSpec = tween(CopyIconFadeMillis, easing = EaseInOut),
            label = "copyConfirmation",
        ) { showCheck ->
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(
                    if (showCheck) R.drawable.ic_check_circle else R.drawable.ic_copy
                ),
                contentDescription = null,
                tint = White,
            )
        }
    }
}

/** How long the copy button holds the checkmark before reverting (iOS: 1.5s). */
private const val CopyConfirmationMillis = 1_500L

/** Cross-fade between the copy and confirmation glyphs (iOS: 0.15s ease-in-out). */
private const val CopyIconFadeMillis = 150

/**
 * One of the two square-ish actions under the link (node 9276:4756). The tile's height is fixed by
 * the parent row and the arrangement centres its contents, so it takes no vertical padding of its
 * own: 20dp of it on each side left the label a 14dp box for a 16dp line and clipped its descenders.
 */
@Composable
private fun ShareTile(
    modifier: Modifier = Modifier,
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(TileShape)
            .background(White05)
            .clickable(enabled = enabled) { onClick() },
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
    enabled: Boolean = true,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .noRippleClickable(enabled = enabled) {
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
