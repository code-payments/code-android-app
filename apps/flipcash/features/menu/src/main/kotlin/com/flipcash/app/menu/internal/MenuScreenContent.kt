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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
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
import com.flipcash.app.menu.internal.MenuScreenViewModel.TipCardState
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.services.models.UserProfile
import com.flipcash.core.R as CoreR
import com.flipcash.features.menu.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White08
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.theme.CodeScaffold
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    // Only a claimed card expands — the unclaimed stand-in is decoration behind the prompt.
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
                            tipCardState = state.tipCardState,
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
                            onClaim = { viewModel.dispatchEvent(Event.ClaimTipCard) },
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
 * An account with no display name has no card yet, and gets [UnclaimedTipCardPrompt] in its place
 * rather than an empty page. Nothing is drawn while the state is still [TipCardState.Unknown] — a
 * named account resolves in a frame or two, and a prompt that flashed at it would be a lie.
 */
@Composable
private fun YouHeader(
    tipCardState: TipCardState,
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
    onClaim: () -> Unit,
) {
    when (tipCardState) {
        TipCardState.Unknown -> Unit
        is TipCardState.Unclaimed -> UnclaimedTipCardPrompt(
            placeholder = tipCardState.placeholder,
            cardWidth = cardWidth,
            enabled = enabled,
            onClaim = onClaim,
        )
        is TipCardState.Claimed -> ClaimedTipCard(
            card = tipCardState.card,
            link = tipCardState.link,
            enabled = enabled,
            expansion = expansion,
            slideAway = slideAway,
            cardWidth = cardWidth,
            cardShift = cardShift,
            onCardSlotPositioned = onCardSlotPositioned,
            onToggleFullScreen = onToggleFullScreen,
            onCopyLink = onCopyLink,
            onShare = onShare,
            onDownload = onDownload,
        )
    }
}

/**
 * The claimed card and everything that hangs off it.
 *
 * The caller drives the full-screen state: it sizes the card ([cardWidth]) and draws it out of its
 * slot towards the middle of the display ([cardShift], off the slot position reported by
 * [onCardSlotPositioned]). It also hands down [slideAway] — the fade-and-slide every non-card
 * element shares — plus [expansion] for the caption, which iOS fades in place rather than sliding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimedTipCard(
    card: Scannable.TipCard,
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
 * What the "You" tab shows before the account has a display name: the card it *would* have, blurred
 * out behind a prompt to claim it. Mirrors iOS `YouScreen.setupPrompt`.
 *
 * The stand-in is the account's real scannable payload drawn over an unnamed profile, with the
 * card's own fill turned off so the 8% ground shows through — the same construction iOS uses. It is
 * decoration: not tappable, not expandable, not shareable, and the tip link and Share / Download
 * tiles are absent entirely, because there is nothing yet to link to or share.
 *
 * [blurEnabled] is haze's own API-31 gate, surfaced so a preview can render what an API 29/30
 * device draws (see `Preview_UnclaimedTipCardPrompt_NoBlur`). Leave it at the default in app code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnclaimedTipCardPrompt(
    placeholder: Scannable.TipCard?,
    cardWidth: Dp,
    enabled: Boolean,
    onClaim: () -> Unit,
    blurEnabled: Boolean = HazeBlurDefaults.isBlurEnabledByDefault(),
) {
    val shape = RoundedCornerShape(cardWidth * TipCardCornerFraction)
    val hazeState = rememberHazeState()
    val placeholderName = stringResource(CoreR.string.label_tipCardNamePlaceholder)

    // The card's ground, flattened: the 8% white wash resolved against the page behind it. The
    // frosting composites over this, which is what makes the overlay opaque — haze draws the blur as
    // a layer in FRONT of its source rather than filtering it in place, so without an opaque ground
    // the sharp code would read straight through its own frosting.
    // The HazeBlurStyle builder is not a @Composable scope, so the theme read is hoisted above it.
    val cardGround = White08.compositeOver(CodeTheme.colors.background)
    val frosting = HazeBlurStyle {
        blurEnabled(blurEnabled)
        blurRadius(PlaceholderBlurRadius)
        backgroundColor(cardGround)
        // Haze only blurs on API 31+ and minSdk is 29; below that it falls back to this scrim, which
        // has to be opaque for the same reason. Never the sharp code: an unclaimed card drawn
        // legibly would read as a real one.
        fallbackColorEffect(HazeColorEffect.tint(cardGround))
        // Off: haze's default film grain over a scannable figure reads as noise in the code itself
        // rather than as texture, and iOS frosts the stand-in with a plain blur.
        noiseFactor(0f)
    }

    // The stand-in is a fixed-width card in a full-width header slot, so it has to be centred the way
    // the claimed card's own Column centres it. Without this it sits at the slot's start edge.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                // The card pads itself off the status bar; the list's content padding already owns that
                // clearance here, so consume the inset rather than paying it twice.
                .consumeWindowInsets(WindowInsets.statusBarsIgnoringVisibility)
                .size(cardWidth, cardWidth * TipCardAspectRatio)
                .clip(shape)
                .background(White08)
                .border(PlaceholderBorderWidth, White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (placeholder != null) {
                // A nameless account renders its name line as a bare "Tip ", which frosts to a much
                // narrower smudge than a real card's. Stand a name in so the blur has the weight the
                // claimed card's would (iOS `YouScreen.placeholderName`).
                val stoodIn = remember(placeholder, placeholderName) {
                    placeholder.copy(user = placeholder.user.copy(displayName = placeholderName))
                }

                CompositionLocalProvider(
                    LocalTipCardColor provides Color(0xFF101011),
                    // Fill off, so the placeholder ground behind it is what's frosted, not an opaque card.
                    LocalTipCardBaseAlpha provides 0f,
                ) {
                    ScannableRenderer(
                        modifier = Modifier.hazeSource(hazeState),
                        scannable = stoodIn,
                        tipCardWidth = cardWidth,
                    )
                }

                // Drawn over the stand-in and under the prompt, so the copy below stays sharp.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeBlur(HazeInput.Sources(hazeState), frosting)
                )
            }

            Column(
                // iOS caps the prompt at the card width less 16, so the copy never reaches the corners.
                modifier = Modifier.width(cardWidth - PromptInset * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(CoreR.string.title_tipIntro),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(CoreR.string.subtitle_tipIntro),
                    style = CodeTheme.typography.textSmall,
                    // Full strength, not secondary: it sits over the blurred code's glow.
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .clip(CircleShape)
                        .background(CodeTheme.colors.textMain)
                        .clickable(enabled = enabled, onClick = onClaim)
                        .padding(horizontal = 25.dp, vertical = 10.dp),
                    text = stringResource(CoreR.string.action_startReceivingTips),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.background,
                )
            }
        }

        Spacer(Modifier.height(UnclaimedRowsGap))
    }
}

/** The tip card's height-to-width proportion and corner radius, mirrored from `TipCard`. */
private const val TipCardAspectRatio = 333f / 269f
private const val TipCardCornerFraction = 0.08f

/** How far the unclaimed stand-in is frosted (iOS `YouScreen.setupPrompt`: `blur(radius: 12)`). */
private val PlaceholderBlurRadius = 12.dp

/** The hairline that keeps the blurred stand-in readable as a card rather than a smudge. */
private val PlaceholderBorderWidth = 1.dp

/** Margin between the claim prompt and the stand-in card's edges (iOS: 16 across the pair). */
private val PromptInset = 8.dp

/**
 * Gap between the unclaimed stand-in and the first settings row. Wider than the claimed card's 19,
 * because the claimed card pays part of its clearance in the Share / Download tiles that the
 * unclaimed state doesn't draw (iOS `YouScreen`: `.padding(.top, displayName == nil ? 48 : 19)`).
 */
private val UnclaimedRowsGap = 48.dp

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

private val PreviewCodeData = listOf(
    0xA5, 0x3C, 0xD7, 0x8B, 0x14, 0xE9, 0x62, 0xF0,
    0x4D, 0xB6, 0x29, 0x7A, 0xC3, 0x58, 0x91, 0xDE,
    0x6F, 0x03, 0xB4, 0x87, 0x2C, 0xE5, 0x50, 0xA9,
    0x1E, 0x73, 0xC6, 0x3F, 0x98, 0x41, 0xDA, 0x65,
    0x0B, 0xF2, 0x7D, 0xAE, 0x53, 0xC0, 0x19,
).map { it.toByte() }

/** The stand-in as an API 31+ device draws it: haze's RenderEffect blur over the card's ground. */
@Preview(name = "Unclaimed — blurred (API 31+)")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UnclaimedTipCardPrompt() {
    UnclaimedTipCardPrompt(
        placeholder = Scannable.TipCard(data = PreviewCodeData, user = UserProfile.Empty),
        cardWidth = YouCardWidth,
        enabled = true,
        onClaim = {},
    )
}

/**
 * The same stand-in on API 29/30, where haze can't blur and falls through to its scrim delegate.
 * The scrim draws nothing on its own, so this is the preview that proves `fallbackColorEffect`
 * is doing its job: the code underneath must be fully covered, not legible.
 */
@Preview(name = "Unclaimed — scrim fallback (API 29/30)")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UnclaimedTipCardPrompt_NoBlur() {
    UnclaimedTipCardPrompt(
        placeholder = Scannable.TipCard(data = PreviewCodeData, user = UserProfile.Empty),
        cardWidth = YouCardWidth,
        enabled = true,
        onClaim = {},
        blurEnabled = false,
    )
}
