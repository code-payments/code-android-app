package com.flipcash.app.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.tokens.SwapResult
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.navigateForResult
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.money.formattedAppreciation
import com.flipcash.app.core.ui.TokenCard
import com.flipcash.app.tokens.internal.components.info.CurrencyInfoContentV2
import com.flipcash.app.tokens.internal.components.info.CurrencyInfoTitlePill
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import dev.chrisbanes.haze.rememberHazeState
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import kotlin.math.roundToInt

/**
 * The expanded currency-info detail, drawn as an overlay above the wallet (iOS #587 model). The whole
 * thing is driven by [CardExpansionController.progress]:
 *  - phase A (0 → [CardExpansionController.HeroPhase]): the hero card flies between the tapped deck
 *    slot ([CardExpansionController.sourceBounds]) and its expanded frame; the wallet deck (behind)
 *    reorganises in step.
 *  - phase B ([CardExpansionController.HeroPhase] → 1): the detail content + chrome + background fade
 *    in while the card stays fixed at its expanded frame.
 *
 * So expand reads card-flies-then-detail-appears and collapse reads detail-fades-then-card-returns.
 * The hero itself is drawn HERE (flying); the detail's own hero slot is an invisible placeholder that
 * reports its window bounds as the fly target.
 */
@Composable
fun CurrencyInfoExpansion(
    controller: CardExpansionController,
    mint: Mint,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ONE reused VM for the overlay — deliberately NO per-mint key. The overlay is hosted at the app
    // level, so a key-per-mint hiltViewModel parked a distinct VM (each with ~14 always-on polling
    // collectors: balance, rate, market cap, recent activity) in the Activity store for every token ever
    // opened; they never stopped and piled up, starving later open transitions (the intermittent lag over
    // many opens). A single keyless instance is reused across opens and re-pointed by OnMintProvided
    // below — its collectors are set up once and its flatMapLatest chains cancel the previous mint's work
    // — so nothing accumulates.
    val viewModel = hiltViewModel<TokenInfoViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(mint) {
        viewModel.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint))
    }

    // The wallet-tap detail is an app-root overlay, not a nav entry — so route its action tiles
    // (Give / Convert / Withdraw) through the reused VM's events the same way the pushed
    // TokenInfoScreen does. Pushed screens land ON the back stack and cover this overlay, which stays
    // composed (moved aside by NewAppContent while covered) so returning reveals it with no reopen.
    // Mirrors iOS, where CurrencyInfoScreen.give pushes over the currency-info overlay. Exit collapses.
    val navigator = LocalCodeNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenInfoViewModel.Event.Exit>()
            .onEach { onCollapse() }
            .launchIn(this)
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenInfoViewModel.Event.OpenScreen>()
            .map { it.screen }
            .onEach { screen ->
                when (screen) {
                    is AppRoute.Token.Swap -> {
                        navigator.navigateForResult<SwapResult>(screen) { result ->
                            if (result is NavResultOrCanceled.ReturnValue &&
                                result.value is SwapResult.OpenDeposit) {
                                navigator.push(AppRoute.Transfers.Deposit(showOtherOptions = false))
                            }
                        }
                    }
                    else -> navigator.push(screen)
                }
            }.launchIn(this)
    }

    val listState = rememberLazyListState()

    // Frosted-glass state: the scrolling detail registers as the haze source (inside CurrencyInfoContentV2)
    // and the app bar's close/share buttons + title pill sample it for their liquid-glass blur.
    val hazeState = rememberHazeState()

    // Title pill reveal: it fades in once the hero card has scrolled up under the bar (matches the pushed
    // token-info screen). Mirrors TokenInfoScreen's threshold + spring.
    val revealThresholdPx = with(LocalDensity.current) { CodeTheme.dimens.staticGrid.x12.toPx() }
    val showPill by remember(listState, revealThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > revealThresholdPx
        }
    }
    val pillProgress by animateFloatAsState(
        targetValue = if (showPill) 1f else 0f,
        label = "titlePill",
    )

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val appBarHeight = 56.dp
    // Breathing room between the app bar and the hero card (the bare bar height alone reads too tight).
    val heroTopGap = CodeTheme.dimens.grid.x4

    // Seed from the controller's surviving hero bounds: when this overlay is hosted inside the wallet nav
    // entry, a pushed action tears down its composition, so a plain `remember(null)` would come back null
    // on return — the flying hero is gated on a non-null target, so it would stay INVISIBLE until the
    // placeholder re-measured. The controller keeps the last measured bounds (it's app-root), so re-seed
    // from it and the hero is drawable again on the very first frame back.
    var heroTarget by remember { mutableStateOf(controller.heroBounds) }

    // Pull-to-close: the detail's own scroll drives it. Once the list is at the very top, further
    // DOWNWARD drag is overscroll — it comes back to us as leftover in the nested-scroll connection below,
    // rides the card down and fades the detail. When the list can still scroll, the drag scrolls it (so
    // dragging anywhere — including over the card — is never dead space). Release past the threshold
    // collapses (resuming from the finger via releasedOffset), release short springs back.
    val pullDistancePx = with(LocalDensity.current) { PullFullDistance.toPx() }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var releasedOffset by remember { mutableFloatStateOf(0f) }
    val pullScope = rememberCoroutineScope()
    val onPullEnd = {
        if (pullPx >= pullDistancePx * PullCloseFraction) {
            releasedOffset = pullPx
            pullPx = 0f
            onCollapse()
        } else if (pullPx > 0f) {
            pullScope.launch { animate(pullPx, 0f) { v, _ -> pullPx = v } }
        }
    }
    // The overlay/real-card handoff pivots on this: the flying overlay card is only shown while the list
    // is at the very TOP (where the flight happens and a pull-to-close lives, and where it's coincident
    // with the real card's slot). The instant the list scrolls, we hand off to the real in-list card so it
    // scrolls NATIVELY under the app bar (full fling, no overlay tracking to jank or halt at the bar).
    // Read live in deferred layers so the swap costs no recomposition.
    val atTop = {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
    // Pull-to-close: while the list is pinned at the top, leftover DOWNWARD drag (overscroll) rides the
    // card down and fades the detail; dragging back up unwinds it; release past the threshold collapses.
    // Normal scrolling is untouched (native fling), so this only engages as a genuine top-overscroll —
    // which in this unified card+content scroll simply IS pulling the card down.
    val pullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullPx > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    val raw = minOf(-available.y, pullPx / PullResistance)
                    pullPx = (pullPx - raw * PullResistance).coerceAtLeast(0f)
                    return Offset(0f, -raw)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pullPx = (pullPx + available.y * PullResistance).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx > 0f) {
                    onPullEnd()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // Publish the hero's live pull translation so the deck's own card coincides with it (no second card).
    // Do this from a snapshotFlow collector, NOT by reading progress/pullPx in the composition body — those
    // are per-frame animating states, so reading them here would recompose this whole (large) overlay on
    // every animation frame, loading UI-thread work onto exactly the frames that must stay cheap (the
    // settle). That dropped the settle frame and read as a "snap" (and it's CPU-bound, so it showed up
    // even on a software-GPU emulator). snapshotFlow observes them off the composition — no recomposition.
    LaunchedEffect(controller) {
        snapshotFlow {
            pullPx + releasedOffset * CardExpansionController.heroProgress(controller.progress.value)
        }.collect { controller.pullOffset = it }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Only the card rides the overscroll down; the detail + chrome stay put and just fade — faster
        // than the pull travels, so the screen is mostly gone by the time the card is barely moved (iOS).
        // Fade off the SAME effective offset the card rides — pullPx while dragging, then releasedOffset*p
        // once released — so committing a dismiss doesn't zero pullPx and snap the detail back to full
        // opacity (a flash) before the collapse re-fades it; it stays faded continuously through release.
        val pullFade = {
            val effective = pullPx +
                releasedOffset * CardExpansionController.heroProgress(controller.progress.value)
            (1f - effective / (pullDistancePx * PullFadeFraction)).coerceIn(0f, 1f)
        }
        // The detail BACKGROUND ramps to opaque FAST at the start of phase B (≈first third), so it covers
        // the compressed deck before the translucent Give/Convert/Withdraw tiles fade in over it — otherwise
        // the squeezed deck bleeds through those tiles. It also stays opaque through a pull so the wallet
        // never peeks through while the card is dragged down.
        val bgAlpha = {
            (CardExpansionController.detailProgress(controller.progress.value) * 3f).coerceIn(0f, 1f)
        }
        // The detail CONTENT + chrome fade in at the slower phase-B pace (and additionally fade with the
        // pull), appearing over the already-opaque background rather than over the deck.
        val detailAlpha = {
            CardExpansionController.detailProgress(controller.progress.value) * pullFade()
        }
        val heroP = { CardExpansionController.heroProgress(controller.progress.value) }

        // Detail background — fades in during phase B (transparent early so the reorganising deck shows).
        // ModulateAlpha: a solid fill needs no offscreen layer to fade, so skip the per-frame 1440×3120
        // saveLayer (and its alloc/free at the settle) that CompositingStrategy.Auto would incur.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = bgAlpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .background(CodeTheme.colors.background),
        )

        // Detail content. The nested-scroll connection turns top-overscroll into the pull-to-close;
        // normal scrolling stays native (fling). The hero item is the REAL card, shown once the list
        // scrolls (see heroPlaceholderAlpha) so it scrolls under the app bar natively; while pinned at
        // the top it stays invisible and the flying overlay card (below) stands in.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullConnection)
                // ModulateAlpha: the content is opaque cards/text over the already-opaque background, so
                // per-op alpha modulation looks identical to compositing the whole list as one layer — but
                // it avoids rendering the entire scrolling list (incl. the market-cap chart) into a
                // full-screen offscreen buffer every frame of the fade, which was the dominant transition
                // GPU cost and the source of the dropped settle frame ("snap") on high-refresh panels.
                .graphicsLayer {
                    alpha = detailAlpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            CurrencyInfoContentV2(
                shortfall = null,
                state = state,
                listState = listState,
                contentPadding = PaddingValues(
                    top = statusBar + appBarHeight + heroTopGap,
                    bottom = bottomInset + CodeTheme.dimens.grid.x8,
                ),
                hazeState = hazeState,
                heroAsPlaceholder = true,
                // Hidden while pinned at the top (the flying overlay card stands in there); shown the
                // instant the list scrolls, so the real card carries the scroll natively. The overlay and
                // this card are coincident at offset 0, so the swap is invisible. Read in a deferred layer.
                heroPlaceholderAlpha = { if (atTop()) 0f else 1f },
                // Latch the hero's fly target on the first measurement and hold it — a stable target for the
                // flight (the overlay only ever flies to/pulls from the top slot; scrolling is the real card).
                onHeroBounds = {
                    if (heroTarget == null) {
                        heroTarget = it
                        controller.reportHeroBounds(it)
                    }
                },
                dispatch = viewModel::dispatchEvent,
            )
        }

        // The flying hero card — from the tapped deck slot to its expanded frame, staying opaque.
        // Only draw it once the reused VM's state has actually re-pointed to THIS mint. Until then
        // state still carries the previously-opened card's token/balance, and drawing it would flash
        // that stale card — worse, its balance would visibly roll from the old amount to the new one
        // (TokenCard renders the amount with AnimatedNumberText). During that brief stale window we're
        // at progress ≈ 0, where the deck's own (coincident) card stands in, so skipping the overlay
        // hero here is invisible — the correct-mint hero simply appears a frame or two into the flight.
        val token = (state.token as? Loadable.Loaded)?.data?.takeIf { it.address == mint }
        val source = controller.sourceBounds
        // Prefer the surviving controller bounds so a torn-down/re-inflated overlay (returning from a
        // pushed action) can draw the hero before its own placeholder re-measures.
        val target = heroTarget ?: controller.heroBounds
        if (token != null && source != null && target != null && target.width > 0f) {
            val density = LocalDensity.current
            val isHeld = state.showTransactionHistory || state.balance.nativeAmount.isPositive
            val appreciationText = state.appreciation
                ?.takeIf { isHeld && state.showAppreciation && it != LocalFiat.MIN_VALUE }
                ?.nativeAmount
                ?.formattedAppreciation()
            Box(
                modifier = Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                    .graphicsLayer {
                        // A single opaque card — no offscreen needed to fade it as it crossfades with the
                        // deck's own card, so modulate alpha per-op instead of compositing a layer.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val p = heroP()
                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(source.width / target.width, 1f, p)
                        scaleX = scale
                        scaleY = scale
                        translationX = (source.left - target.left) * (1f - p)
                        // The card is the only thing that rides the pull-to-close overscroll down; on
                        // release the offset is carried home by the collapse (releasedOffset * p).
                        translationY = (source.top - target.top) * (1f - p) + pullPx + releasedOffset * p
                        // Shown while the list is at the top (flight, settle, and pull all happen there);
                        // the instant the list scrolls we hand off to the coincident real in-list card, so
                        // this overlay hides and never fights native scroll.
                        //
                        // Opacity ramps to FULL by [HeroPhase] and holds — it is NOT a plain `p` fade, and
                        // NOT a flat 1. Both extremes break one end of the transition:
                        //  • A plain `alpha = p` DIMS the card on OPEN: the detail background ramps opaque
                        //    just after [HeroPhase] and covers the deck's own card (the opaque backing this
                        //    hero rode on top of), after which the only card left is this hero at p<1 over a
                        //    dark background — a ~10% dim that recovers at p=1 (a one-time flicker).
                        //  • A flat `alpha = 1` fixes that but SNAPS the z-order on CLOSE: this overlay draws
                        //    above the whole deck, so a fully-opaque hero sits ON TOP of its neighbours the
                        //    entire way down and only yields to the natural-z (under-neighbour) deck card when
                        //    the overlay clears at p=0 — the card pops from above the stack to behind it in one
                        //    frame.
                        // Ramping to 1 by [HeroPhase] gives both: opaque before the background covers the
                        // backing (no open dim), and — since the hero is pixel-coincident with the deck's
                        // identical card — a fade back through the low-p range on close, where the card
                        // descends among its neighbours, so the hand-off to the natural-z card dissolves.
                        alpha = if (atTop()) (p / CardExpansionController.HeroPhase).coerceIn(0f, 1f) else 0f
                    },
            ) {
                TokenCard(
                    token = token,
                    balanceText = if (isHeld) state.balance.nativeAmount.formatted() else "",
                    displayName = token.name,
                    appreciationText = appreciationText,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Chrome (top scrim + frosted app bar: close, title pill, share), fading with the detail.
        // ModulateAlpha: no offscreen layer for the fade (the chrome doesn't self-overlap), so no
        // per-frame buffer alloc/free at the settle.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = detailAlpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            // Soft top fade so content dims as it scrolls up under the app bar (matches the pushed screen).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .height(statusBar + appBarHeight * 0.6f)
                    .background(
                        Brush.verticalGradient(
                            0f to CodeTheme.colors.background,
                            1f to Color.Transparent,
                        ),
                    ),
            )
            // The same frosted app bar the pushed token-info screen uses: leading close (✕), a title pill
            // that reveals on scroll, and a share action — all liquid-glass, sampling the detail via haze.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            ) {
                AppBarWithTitle(
                    titleContent = {
                        token?.let {
                            CurrencyInfoTitlePill(
                                token = it,
                                marketCap = state.marketCap,
                                progress = pillProgress,
                                hazeState = hazeState,
                            )
                        }
                    },
                    titleAlignment = Alignment.Start,
                    onBackIconClicked = onCollapse,
                    leadingDismiss = true,
                    hazeState = hazeState,
                    endContent = {
                        if (!state.isCashReserve) {
                            AppBarDefaults.Share(hazeState = hazeState) {
                                viewModel.dispatchEvent(TokenInfoViewModel.Event.Share)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Drag distance at which the pull-to-close reaches full progress. */
private val PullFullDistance = 260.dp

/** How far the card trails the finger during the pull (1 = point-per-point). */
private const val PullResistance = 0.5f

/** Fraction of [PullFullDistance] past which releasing commits the collapse (else it springs back). */
private const val PullCloseFraction = 0.55f

/** Fraction of [PullFullDistance] over which the detail content fades fully out (fast, iOS-like). */
private const val PullFadeFraction = 0.4f
