package com.flipcash.app.tokens.internal.components.info

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.money.formattedAppreciation
import com.flipcash.app.core.ui.TokenCard
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.app.core.ui.transitions.CardExpandTransition
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.getcode.opencode.model.financial.Token
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.flipcash.shared.transactionhistory.recentActivitySection
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.text.ExpandableText
import com.getcode.ui.core.addIf
import com.getcode.ui.theme.CodeCircularProgressIndicator
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import com.getcode.util.format

/**
 * V2 currency-info layout: LazyColumn with hero card → action tiles → recent activity
 * → market cap → about section → created footer. No bottom bar — actions are inline tiles.
 */
@Composable
internal fun CurrencyInfoContentV2(
    shortfall: Fiat?,
    state: TokenInfoViewModel.State,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    // Overlay (card-expand) presentation: the hero is drawn by the expansion host (flying from the deck
    // slot), so here the hero item only RESERVES its slot and reports its window bounds as the fly target.
    // Its alpha is driven by [heroPlaceholderAlpha]: hidden (0) while the list is pinned at the top (the
    // flying overlay card stands in there), shown (1) once the list scrolls so THIS real card carries the
    // scroll natively under the app bar. Default (pushed/deeplink presentation) draws the hero normally.
    heroAsPlaceholder: Boolean = false,
    heroPlaceholderAlpha: () -> Float = { 0f },
    onHeroBounds: (Rect) -> Unit = {},
    dispatch: (TokenInfoViewModel.Event) -> Unit,
) {
    val inset = CodeTheme.dimens.inset
    val grid = CodeTheme.dimens.grid

    LazyColumn(
        // Content fills behind the overlaid app bar and scrolls under it; [hazeState] marks it as the
        // blur source so the frosted (liquid-glass) bar chrome frosts it. [contentPadding] insets the
        // first item below the bar (like the chat screen), and the bar draws its own bg->transparent
        // scrim for the soft top fade.
        modifier = Modifier
            .fillMaxSize()
            .addIf(hazeState != null) { Modifier.hazeSource(hazeState!!) },
        state = listState,
        contentPadding = contentPadding,
    ) {
        when (state.token) {
            is Loadable.Loading -> {
                item {
                    Box(modifier = Modifier.fillParentMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(0.24f)
                                .aspectRatio(1f)
                                .align(Alignment.Center),
                        ) {
                            CodeCircularProgressIndicator(
                                modifier = Modifier.matchParentSize(),
                                strokeWidth = grid.x1,
                                color = Color.White,
                                backgroundColor = Color.White.copy(0.30f),
                                strokeCap = StrokeCap.Butt,
                            )
                        }
                    }
                }
            }

            is Loadable.Error -> {
                item {
                    Box(modifier = Modifier.fillParentMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(0.24f)
                                .aspectRatio(1f)
                                .align(Alignment.Center),
                        ) {
                            Image(
                                modifier = Modifier.matchParentSize(),
                                painter = painterResource(R.drawable.ic_circle_exclamation_large),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }

            is Loadable.Loaded -> {
                val loadedToken = state.token as Loadable.Loaded
                val token = loadedToken.data

                val isUsdf = state.isCashReserve
                val isHeld = state.showTransactionHistory || state.balance.nativeAmount.isPositive

                // 1. Hero bill card
                item {
                    val appreciationText = state.appreciation
                        ?.takeIf { isHeld && state.showAppreciation && it != LocalFiat.MIN_VALUE }
                        ?.nativeAmount
                        ?.formattedAppreciation()

                    val heroModifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = inset)
                        .padding(top = grid.x2)

                    if (heroAsPlaceholder) {
                        // Reserve the slot, report its window bounds as the fly target, and reveal this
                        // real card once the list scrolls (the flying overlay hides then — they're
                        // coincident at the top, so the swap is invisible). Deferred alpha = no recompose.
                        TokenCard(
                            token = token,
                            balanceText = if (isHeld) state.balance.nativeAmount.formatted() else "",
                            displayName = token.name,
                            appreciationText = appreciationText,
                            modifier = heroModifier
                                .onGloballyPositioned { onHeroBounds(it.boundsInWindow()) }
                                .graphicsLayer { alpha = heroPlaceholderAlpha() },
                        )
                    } else {
                        // Pushed/deeplink presentation: fly from the tapped wallet deck card (same mint
                        // key), overlay-hosted while opening and in-layer while closing.
                        val heroInOverlay = if (LocalInspectionMode.current) {
                            true
                        } else {
                            LocalNavAnimatedContentScope.current.transition.targetState !=
                                EnterExitState.PostExit
                        }
                        TokenCard(
                            token = token,
                            balanceText = if (isHeld) state.balance.nativeAmount.formatted() else "",
                            displayName = token.name,
                            appreciationText = appreciationText,
                            modifier = heroModifier
                                .sharedBoundsTransition(
                                    key = SharedTransition.TokenCard(token.address).key,
                                    enter = EnterTransition.None,
                                    exit = ExitTransition.None,
                                    boundsTransform = CardExpandTransition.boundsTransform,
                                    renderInOverlayDuringTransition = heroInOverlay,
                                ),
                        )
                    }
                }

                // 2. Action tiles row
                item {
                    CurrencyActionTiles(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = inset)
                            .padding(top = grid.x3),
                        isHeld = isHeld,
                        tokenMint = token.address,
                        shortfall = shortfall,
                        dispatch = dispatch,
                    )
                }

                // 3. Recent transactions (only when held and non-empty) — shared with the wallet screen.
                if (isHeld && state.transactions.isNotEmpty()) {
                    recentActivitySection(
                        transactions = state.transactions,
                        modifier = Modifier
                            .clickable {
                                dispatch(
                                    TokenInfoViewModel.Event.OpenScreen(
                                        AppRoute.Token.Transactions(token.address)
                                    )
                                )
                            }
                            .padding(horizontal = inset)
                            .padding(top = grid.x5, bottom = grid.x1),
                        itemPadding = PaddingValues(horizontal = inset),
                        onItemClick = { item ->
                            dispatch(
                                TokenInfoViewModel.Event.OpenScreen(
                                    AppRoute.Main.TransactionDetails(item.messageId)
                                )
                            )
                        },
                    )
                }

                // 4. Market cap (non-USDF only)
                if (!isUsdf) {
                    state.marketCap?.let { mcap ->
                        val historicalData = state.historicalMarketCapData[state.selectedPeriod]
                            ?: Loadable.Loaded(emptyList())
                        item {
                            MarketCapSection(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .padding(top = grid.x5),
                                contentPadding = PaddingValues(horizontal = inset),
                                marketCap = mcap,
                                selectedPeriod = state.selectedPeriod,
                                rawHistoricalData = historicalData,
                                // New v2 UI: no chart draw-in on open (it appears with the card-expand).
                                animateChartOpen = false,
                                onRetry = {
                                    dispatch(
                                        TokenInfoViewModel.Event.LoadHistoricalDataForPeriod(
                                            state.selectedPeriod
                                        )
                                    )
                                },
                                onPeriodSelected = {
                                    dispatch(TokenInfoViewModel.Event.OnMarketCapPeriodSelected(it))
                                },
                            )
                        }
                    }
                }

                // 5. About section
                val description = token.description
                val socialLinks = token.socialLinks
                if (description.isNotBlank() || socialLinks.isNotEmpty()) {
                    item {
                        CurrencyAboutSection(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(top = grid.x5),
                            description = description,
                            socialLinks = socialLinks,
                            isExpanded = state.descriptionExpanded,
                            inset = inset,
                            onToggleExpand = {
                                dispatch(
                                    TokenInfoViewModel.Event.ExpandDescription(!state.descriptionExpanded)
                                )
                            },
                        )
                    }
                }

                // 6. Created footer (non-USDF with a known creation date)
                if (!isUsdf) {
                    token.createdAt?.let { createdAt ->
                        item {
                            val formattedDate = createdAt.format("MMMM dd, yyyy")
                            Text(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .padding(top = grid.x6, bottom = grid.x6)
                                    .padding(horizontal = inset),
                                text = stringResource(R.string.label_createdAt, formattedDate).uppercase(),
                                style = CodeTheme.typography.caption,
                                color = CodeTheme.colors.textMain.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private composables
// ---------------------------------------------------------------------------

@Composable
private fun CurrencyActionTiles(
    isHeld: Boolean,
    tokenMint: Mint,
    shortfall: Fiat?,
    dispatch: (TokenInfoViewModel.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val giveTile: @Composable RowScope.() -> Unit = {
        ActionTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_give),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_banknote),
                    contentDescription = null,
                    tint = CodeTheme.colors.textMain,
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                )
            },
            onClick = {
                dispatch(
                    TokenInfoViewModel.Event.OpenScreen(
                        AppRoute.Main.Give(mint = tokenMint, fromTokenInfo = true)
                    )
                )
            },
        )
    }

    val convertTile: @Composable RowScope.() -> Unit = {
        ActionTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.action_convert),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_convert),
                    contentDescription = null,
                    tint = CodeTheme.colors.textMain,
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                )
            },
            onClick = { dispatch(TokenInfoViewModel.Event.OnConvert) },
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        when {
            !isHeld -> {
                // Single full-width "Buy In" tile
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.action_buyIn),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            contentDescription = null,
                            tint = CodeTheme.colors.textMain,
                            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                        )
                    },
                    onClick = { dispatch(TokenInfoViewModel.Event.OnBuy(shortfall)) },
                )
            }

            // Dollars keeps Withdraw: there is no "buy more" of the cash reserve, and cashing
            // out is the action people come to this screen for.
            tokenMint == Mint.usdf -> {
                giveTile()
                convertTile()
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.action_withdraw),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_up_large),
                            contentDescription = null,
                            tint = CodeTheme.colors.textMain,
                            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                        )
                    },
                    onClick = {
                        // Preselect the currency being viewed: Dollars detours through the
                        // "Withdraw as USDC" intro, anything else opens straight on the amount screen.
                        dispatch(
                            TokenInfoViewModel.Event.OpenScreen(
                                AppRoute.Transfers.Withdrawal(preselectedMint = tokenMint)
                            )
                        )
                    },
                )
            }

            // Held non-USDF token: Give + Buy More + Convert
            else -> {
                giveTile()
                ActionTile(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.action_buyMore),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            contentDescription = null,
                            tint = CodeTheme.colors.textMain,
                            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                        )
                    },
                    onClick = { dispatch(TokenInfoViewModel.Event.OnBuy(shortfall)) },
                )
                convertTile()
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(CodeTheme.dimens.staticGrid.x18)
            .clip(CodeTheme.shapes.extraSmall)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(CodeTheme.dimens.grid.x1))
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun CurrencyAboutSection(
    description: String,
    socialLinks: List<SocialLink>,
    isExpanded: Boolean,
    inset: Dp,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (description.isNotBlank()) {
            Text(
                modifier = Modifier.padding(horizontal = inset),
                text = stringResource(R.string.subtitle_about),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain.copy(alpha = 0.6f),
            )
            ExpandableText(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = description,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
                isExpanded = isExpanded,
                contentPadding = PaddingValues(horizontal = inset),
                onToggle = onToggleExpand,
            )
        }

        if (socialLinks.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x4),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                contentPadding = PaddingValues(horizontal = inset),
            ) {
                items(socialLinks, key = { it.uri }) { link ->
                    SocialChip(link)
                }
            }
        }
    }
}

/**
 * Scroll-revealed leading title pill (icon + name + market cap). Fades in — driven by [progress]
 * (0 hidden → 1 shown) — once the hero card's own title has scrolled under the app bar, matching the
 * iOS "Liquid Glass" pill. A frosted translucent capsule: a grey lifted off the (near-black)
 * background so it reads as glass over the dark chrome. Market cap is omitted for tokens without one
 * (e.g. USDF).
 */
@Composable
internal fun CurrencyInfoTitlePill(
    token: Token,
    marketCap: Fiat?,
    progress: Float,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val shape = CircleShape
    val backdrop = CodeTheme.colors.background
    val glassBlurRadius = CodeTheme.dimens.grid.x4
    val glassTint = lerp(backdrop, Color.White, 0.18f)
    // Real liquid glass over the scrolling content when a HazeState is supplied; falls back to a
    // translucent capsule otherwise. `clip` precedes `hazeBlur` so the blur is bounded to the pill.
    val fill = if (hazeState != null) {
        // The HazeBlurStyle builder is not a @Composable scope, so theme reads are hoisted above it.
        val liquidGlass = HazeBlurStyle {
            blurRadius(glassBlurRadius)
            backgroundColor(backdrop)
            colorEffects(listOf(HazeColorEffect.tint(glassTint.copy(alpha = 0.72f))))
        }
        Modifier
            .clip(shape)
            .hazeBlur(HazeInput.Sources(hazeState), liquidGlass)
            .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), shape)
    } else {
        Modifier
            .clip(shape)
            .background(glassTint.copy(alpha = 0.9f), shape)
            .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), shape)
    }
    Row(
        modifier = modifier
            .graphicsLayer { alpha = progress }
            .then(fill)
            .padding(
                // Extra trailing room after the name/market-cap so the capsule breathes on the right
                // like iOS (the leading side is tighter — the icon sits close to the edge).
                start = CodeTheme.dimens.grid.x2,
                end = CodeTheme.dimens.grid.x3,
                top = CodeTheme.dimens.grid.x1,
                bottom = CodeTheme.dimens.grid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        TokenIcon(token = token, modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5))
        Column {
            Text(
                text = token.name,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
            )
            marketCap?.let {
                Text(
                    text = it.formatted(),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
