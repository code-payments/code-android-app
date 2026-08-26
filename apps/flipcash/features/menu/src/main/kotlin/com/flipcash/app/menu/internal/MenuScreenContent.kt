package com.flipcash.app.menu.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
import com.flipcash.app.menu.internal.components.UsernameProgress
import com.flipcash.app.menu.internal.components.UsernameProgressCard
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
import com.getcode.theme.extraSmall
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
    val expansion = rememberTipCardExpansion()
    val cardExpanded = expansion.isExpanded
    // Only a claimed card expands — the unclaimed stand-in is decoration behind the prompt.
    val canExpand = isNewUi && state.tipCard != null

    LaunchedEffect(canExpand) {
        // Losing the card (a v1 build, or sign-out) must not strand the page expanded.
        if (!canExpand) expansion.collapse()
    }
    HideTabBar(hidden = cardExpanded)
    BackHandler(enabled = cardExpanded) { expansion.collapse() }

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
                maxWidth - CodeTheme.dimens.inset * 2,
            )
            // How far into the expansion we are. One progress drives all of it — the card's
            // size and position, the page fading out beneath it, the Close row — so a swipe that
            // stops part-way is as legal a state as either end, and none of it can drift out of
            // step with the rest.
            //
            // Handed down as a lambda and never read here: every consumer below reads it inside a
            // graphicsLayer block, which the frame re-runs on its own when the value changes.
            // Reading it in composition instead recomposed this entire page — settings list
            // included — on every frame of the animation.
            val progress = remember(expansion) { { expansion.progress } }
            // The card is scaled, not re-laid-out. Animating its width made every frame re-measure
            // the card and everything inside it: a new corner radius to clip to, a new font size
            // for the name (a full text re-layout), and a new size for the interop View that draws
            // the code, which re-derives the code's geometry from scratch on each draw. That is
            // work for the UI thread, and there is more of it than a frame has time for. iOS hit
            // the same wall and settled on one scale over a card drawn once: laid out, each of the
            // card's metrics is a separate animatable value — and the name's font size is not
            // animatable at all — so the parts arrive at their new sizes at different moments and
            // overlap mid-flight. Scaled, the card travels as one figure.
            //
            // The card is drawn at [FullScreenCardWidth], the widest it ever gets, so the scale
            // only ever samples the drawing down.
            //
            // Both widths are read here rather than inside the lambda: they are theme-backed now,
            // and the lambda the frame calls is not a composable scope.
            val restingCardWidth = YouCardWidth
            val drawnCardWidth = FullScreenCardWidth
            val cardScale = remember(expandedCardWidth, restingCardWidth, drawnCardWidth) {
                { lerp(restingCardWidth, expandedCardWidth, progress()) / drawnCardWidth }
            }
            // v2's tab bar is a hoisted overlay drawn ABOVE this content, so reserve its height as
            // bottom content padding — the list then scrolls clear of the bar instead of running
            // under it (the version footer was landing behind it). Per-entry via LocalTabBarPadding,
            // which is only non-zero for tab homes. v1 has no such bar.
            //
            // It does not animate away with the expansion. It is content padding, so every frame of
            // it relaid the whole list — and by the time the released space could be seen, the list
            // has already faded out and slid away under the card.
            val bottomInset = LocalTabBarPadding.current.calculateBottomPadding()
            // Everything but the card fades out on the expansion and slides down out of the way,
            // rather than being removed. Keeping the rows in the layout means nothing reflows on
            // the way back (iOS does the same with opacity + offset).
            val slideDistance = ContentSlideDistance
            val slideAway = remember(progress, slideDistance) {
                Modifier.graphicsLayer {
                    val fraction = progress()
                    // A settled flick overshoots its end a little, so keep the fade in a legal
                    // alpha range rather than assuming the progress is one.
                    alpha = (1f - fraction).coerceIn(0f, 1f)
                    translationY = slideDistance.toPx() * fraction
                }
            }

            // The card doesn't hand off to a second copy of itself: the one in the list keeps its
            // slot and is drawn travelling out of it, the way iOS offsets the card from its own
            // measured frame. Measuring the slot (which never carries the offset) rather than the
            // card keeps the measurement out of its own feedback loop.
            //
            // The slot keeps its resting size for the whole expansion: it is what the card scales
            // out of, not something that grows with it. A slot that grew would move its own centre
            // mid-flight, and that centre is what the shift measures from, so the card would travel
            // against itself — a frame behind its own size the whole way.
            var cardSlotCenterY by remember { mutableFloatStateOf(0f) }
            val displayCenterY = LocalWindowInfo.current.containerSize.height / 2f
            val cardShift = remember(displayCenterY, expansion) {
                {
                    if (cardSlotCenterY <= 0f) 0f
                    // The overdrag rides on the card's position alone: pushed down past full
                    // screen the card has nowhere left to go, so it gives a little where it
                    // stands while its size and every fade hold where the expansion left them.
                    else (displayCenterY - cardSlotCenterY) * (progress() + expansion.overdrag)
                }
            }

            // Swiping the expanded card back up puts it away. The card's own travel is the
            // gesture's travel: expanding walks the card DOWN out of its slot into the middle of
            // the display — which is what the "Full Screen" chevron points at, and why "Close"
            // points back up — so the way out is up, and a finger that covers the distance the card
            // has left to go closes it exactly. The card stays under the finger the whole way and
            // springs to whichever end the release picks.
            //
            // It goes on the page rather than on the card, as iOS's does: expanded, the card IS the
            // page, and a pull that has to land on the card exactly is a pull that misses — the
            // card is 302dp of a display wider than that, with live margin either side. Nothing
            // here scrolls while the card is up (see userScrollEnabled below), so there is no
            // scroll for the drag to take events from.
            val density = LocalDensity.current
            val minTravel = with(density) { MinDragTravel.toPx() }
            val flingVelocity = with(density) { MinFlingVelocity.toPx() }
            val touchSlop = LocalViewConfiguration.current.touchSlop
            // The same distance cardShift moves the card, so the card keeps pace with the finger
            // exactly. It holds still for the length of the gesture without having to be pinned:
            // the slot it measures from no longer grows with the card.
            val dragTravel = (displayCenterY - cardSlotCenterY).coerceAtLeast(minTravel)
            val cardDrag = Modifier.draggable(
                state = rememberDraggableState { delta -> expansion.dragBy(delta / dragTravel) },
                orientation = Orientation.Vertical,
                enabled = cardExpanded,
                onDragStarted = { expansion.startDrag(touchSlop / dragTravel) },
                onDragStopped = { velocity ->
                    expansion.settle(velocity / dragTravel, flingVelocity / dragTravel)
                },
            )

            MenuList(
                modifier = Modifier
                    .fillMaxSize()
                    .then(cardDrag),
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
                            expansion = progress,
                            slideAway = slideAway,
                            cardScale = cardScale,
                            cardShift = cardShift,
                            onCardSlotPositioned = { cardSlotCenterY = it },
                            onToggleFullScreen = { expansion.toggle() },
                            onCopyLink = { viewModel.dispatchEvent(Event.CopyTipLink) },
                            onShare = { viewModel.dispatchEvent(Event.ShareTipCard) },
                            onDownload = { viewModel.dispatchEvent(Event.DownloadTipCard) },
                            onClaim = { viewModel.dispatchEvent(Event.ClaimTipCard) },
                            usernameProgress = state.usernameProgress,
                            usernameMinimumBalance = state.usernameMinimumBalance,
                            onClaimUsername = { viewModel.dispatchEvent(Event.ClaimUsername) },
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
                    bottom = bottomInset,
                ),
                onItemClick = {
                    // The faded-out rows are still laid out under the expanded card; don't let them
                    // take a tap meant for the card.
                    if (!cardExpanded) viewModel.dispatchEvent(it.action)
                }
            )

            // Close sits at the foot of the display rather than under the card (node 9277:121410).
            // It fades on the same progress as everything else rather than on a transition of its
            // own, so a swipe held half-way leaves it half-faded instead of fully drawn.
            // Gated on a derived boolean rather than on the progress itself: the row has to leave
            // the layout when the card is down (nothing invisible left at the foot of the page for
            // a tap or TalkBack to find), but reading the progress here would recompose the page
            // every frame. Derived, it only recomposes when the answer flips.
            val closeVisible by remember { derivedStateOf { expansion.progress > 0f } }
            if (closeVisible) {
                FullScreenToggle(
                    label = stringResource(R.string.action_closeFullScreen),
                    chevronRotation = 180f,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Drawn over the list, so it needs the pull too — a hand reaching for
                        // "Close" and pulling instead is the likeliest place to start one.
                        .then(cardDrag)
                        .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
                        .navigationBarsPadding()
                        .padding(bottom = CloseBottomSpacing)
                        .noRippleClickable(enabled = cardExpanded) { expansion.collapse() },
                )
            }
        }
    }
}

/** Distance from the status bar to the top of the tip card (node 9278:7301: 74). */
private val CardTopSpacing: Dp
    @Composable get() = CodeTheme.dimens.grid.x15

/**
 * The card at rest (node 9278:7301: 241.636) and expanded (node 9277:121410: 302.21), both measured
 * on a 402-wide frame and kept here as the fraction of the display they were drawn at rather than
 * the dp they happened to measure on it. iOS pins 302 outright; as a fraction the card holds its
 * proportion of a narrower or wider display instead of crowding one and stranding the other.
 */
private const val YouCardWidthFraction = 0.60f
private const val FullScreenCardWidthFraction = 0.75f

private val YouCardWidth: Dp
    @Composable get() = CodeTheme.dimens.screenWidth * YouCardWidthFraction

private val FullScreenCardWidth: Dp
    @Composable get() = CodeTheme.dimens.screenWidth * FullScreenCardWidthFraction

/** Gap between the Close row and the system nav bar (node 9277:121410). */
private val CloseBottomSpacing: Dp
    @Composable get() = CodeTheme.dimens.grid.x2

/**
 * Clearance between the last settings row's divider and the version footer. iOS spends 32 above the
 * footer plus 12 of the footer's own vertical padding on top of the row's 25 inset; the Android row
 * already pays that same 25, so the difference lands here.
 */
private val VersionFooterTopSpacing: Dp
    @Composable get() = CodeTheme.dimens.grid.x9

/** How far the page's content slides down as it fades out under the expanding card. */
private val ContentSlideDistance: Dp
    @Composable get() = CodeTheme.dimens.grid.x12

/**
 * How much of the expansion the "Full Screen" caption has to be gone within — a fraction of the
 * progress, not of the duration, so a slow drag fades it on exactly the terms a spring does.
 *
 * It is short because the card is chasing it. The card's lower edge travels down from the slot at
 * the sum of its own growth and its shift to the middle of the display — about 224dp of travel per
 * unit of progress on a 1080x2424 screen, and more on a taller one — against the 24dp of clearance
 * between the two. So the card is over this spot by a tenth of the way out, and the caption cannot
 * still be legible when it gets there.
 */
private const val CaptionFadeTravel = 0.08f

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
    expansion: () -> Float,
    slideAway: Modifier,
    cardScale: () -> Float,
    cardShift: () -> Float,
    onCardSlotPositioned: (Float) -> Unit,
    onToggleFullScreen: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onClaim: () -> Unit,
    usernameProgress: UsernameProgress?,
    usernameMinimumBalance: String,
    onClaimUsername: () -> Unit,
) {
    when (tipCardState) {
        TipCardState.Unknown -> Unit
        is TipCardState.Unclaimed -> UnclaimedTipCardPrompt(
            placeholder = tipCardState.placeholder,
            // An unclaimed stand-in never expands, so it is only ever the resting card.
            cardWidth = YouCardWidth,
            enabled = enabled,
            onClaim = onClaim,
        )
        is TipCardState.Claimed -> ClaimedTipCard(
            card = tipCardState.card,
            link = tipCardState.link,
            enabled = enabled,
            expansion = expansion,
            slideAway = slideAway,
            cardScale = cardScale,
            cardShift = cardShift,
            onCardSlotPositioned = onCardSlotPositioned,
            onToggleFullScreen = onToggleFullScreen,
            onCopyLink = onCopyLink,
            onShare = onShare,
            onDownload = onDownload,
            usernameProgress = usernameProgress,
            usernameMinimumBalance = usernameMinimumBalance,
            onClaimUsername = onClaimUsername,
        )
    }
}

/**
 * The claimed card and everything that hangs off it.
 *
 * The caller drives the full-screen state: it scales the card ([cardScale]) and draws it out of its
 * slot towards the middle of the display ([cardShift], off the slot position reported by
 * [onCardSlotPositioned]). It also hands down [slideAway] — the fade-and-slide every non-card
 * element shares — plus [expansion] for the caption, which iOS fades in place rather than sliding.
 *
 * All four arrive as lambdas so they are read inside the graphics layers that use them, off the
 * composition. Read as values, the whole page would recompose on every frame of the animation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimedTipCard(
    card: Scannable.TipCard,
    link: String?,
    enabled: Boolean,
    expansion: () -> Float,
    slideAway: Modifier,
    cardScale: () -> Float,
    cardShift: () -> Float,
    onCardSlotPositioned: (Float) -> Unit,
    onToggleFullScreen: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    usernameProgress: UsernameProgress?,
    usernameMinimumBalance: String,
    onClaimUsername: () -> Unit,
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
                    // The slot is the card at rest, pinned: the card grows by scaling out of it,
                    // so the slot's centre — which cardShift measures from — has to hold still.
                    .size(YouCardWidth, YouCardWidth * TipCardAspectRatio)
                    .onGloballyPositioned { onCardSlotPositioned(it.boundsInRoot().center.y) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        // Measured unbounded so the card can be drawn at its full width inside the
                        // smaller slot; the slot's constraints would otherwise squeeze it back down
                        // to the resting size and there would be nothing to scale up to.
                        .wrapContentSize(unbounded = true)
                        // Ahead of the gesture modifiers, so the drag and the tap travel with the
                        // card rather than staying behind at the slot it left. Both reads happen
                        // here rather than in composition: the layer re-runs this block by itself
                        // when they change, which is a render-node transform and no relayout.
                        .graphicsLayer {
                            val scale = cardScale()
                            scaleX = scale
                            scaleY = scale
                            translationY = cardShift()
                        }
                        .noRippleClickable { onToggleFullScreen() },
                ) {
                    ScannableRenderer(scannable = card, tipCardWidth = FullScreenCardWidth)
                }
            }
        }

        Spacer(Modifier.height(CodeTheme.dimens.grid.x6))

        // The caption belongs to the card, so it fades where it stands instead of sliding off with
        // the rest of the page — and it is gone well before the card is over it. The caption is a
        // later sibling than the card and so paints on top of it, while the card grows and travels
        // down across this very spot; faded over the whole expansion it would still be legible at
        // the point it ends up printed across the card's face. See [CaptionFadeTravel].
        FullScreenToggle(
            label = stringResource(R.string.action_viewFullScreen),
            chevronRotation = 0f,
            modifier = Modifier
                .graphicsLayer { alpha = (1f - expansion() / CaptionFadeTravel).coerceIn(0f, 1f) }
                .noRippleClickable { onToggleFullScreen() },
        )

        // Everything under the card gets out of the way so the card can own the display.
        Column(
            modifier = slideAway.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x13))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x5),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                if (link != null) {
                    TipLinkRow(link = link, enabled = enabled, onCopy = onCopyLink)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CodeTheme.dimens.grid.x18),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
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

                // Only under a claimed card: an account with no display name is already being asked
                // for one, and a second nudge under the blurred stand-in isn't in the design. Gone
                // entirely once a handle exists — the caller nulls it out.
                if (usernameProgress != null) {
                    UsernameProgressCard(
                        progress = usernameProgress,
                        minimumBalance = usernameMinimumBalance,
                        onClick = onClaimUsername,
                    )
                }
            }

            Spacer(Modifier.height(CodeTheme.dimens.grid.x4))
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
                // claimed card's would (iOS `YouScreen.placeholderName`). The handle goes with it —
                // this card is a stand-in, and a real `@handle` under a stand-in name is neither.
                val stoodIn = remember(placeholder, placeholderName) {
                    placeholder.copy(
                        user = placeholder.user.copy(
                            displayName = placeholderName,
                            username = null,
                        )
                    )
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
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    text = stringResource(CoreR.string.subtitle_tipIntro),
                    style = CodeTheme.typography.textSmall,
                    // Full strength, not secondary: it sits over the blurred code's glow.
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x4)
                        .clip(CircleShape)
                        .background(CodeTheme.colors.textMain)
                        .clickable(enabled = enabled, onClick = onClaim)
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x5,
                            vertical = CodeTheme.dimens.grid.x2,
                        ),
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
private val PlaceholderBorderWidth: Dp
    @Composable get() = CodeTheme.dimens.border

/** Margin between the claim prompt and the stand-in card's edges (iOS: 16 across the pair). */
private val PromptInset: Dp
    @Composable get() = CodeTheme.dimens.grid.x2

/**
 * Gap between the unclaimed stand-in and the first settings row. Wider than the claimed card's 19,
 * because the claimed card pays part of its clearance in the Share / Download tiles that the
 * unclaimed state doesn't draw (iOS `YouScreen`: `.padding(.top, displayName == nil ? 48 : 19)`).
 */
private val UnclaimedRowsGap: Dp
    @Composable get() = CodeTheme.dimens.grid.x10

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
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
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
            .height(CodeTheme.dimens.grid.x8)
            .clip(CodeTheme.shapes.extraSmall)
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
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
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
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .clickable(enabled = enabled) { onClick() },
        verticalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x1,
            Alignment.CenterVertically,
        ),
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
            viewModel.dispatchEvent(Event.PresentDepositOptions())
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
