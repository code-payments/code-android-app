package com.flipcash.app.balance.internal

import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_addDollars
import com.flipcash.core.resources.action_addMoney
import com.flipcash.core.resources.action_withdraw
import com.flipcash.core.resources.label_currencyCount
import com.flipcash.core.resources.title_tabMoney
import com.getcode.util.resources.ic_chevron_right
import com.getcode.util.resources.Res as UiRes
import org.jetbrains.compose.resources.pluralStringResource
import com.flipcash.core.resources.action_createYourOwnCurrency
import com.flipcash.core.resources.action_currencyLeaderboard
import com.flipcash.core.resources.action_withdrawDollars
import com.flipcash.core.resources.ic_coins_add
import com.flipcash.core.resources.ic_globe
import com.flipcash.core.resources.ic_menu_deposit
import com.flipcash.core.resources.ic_menu_withdraw
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.core.AppRoute
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.flipcash.app.core.ui.AppreciationStyle
import com.flipcash.app.core.ui.TokenCardArrival
import com.flipcash.app.core.ui.TokenCardExpansion
import com.flipcash.app.core.ui.TokenCardStack
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.core.ui.onboarding.AddMoneyRow
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.ui.TileButton
import com.flipcash.app.core.ui.TileButtonStyle
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.shared.transactionhistory.moneyActivitySection
import com.flipcash.shared.transactionhistory.recentActivitySection
import com.flipcash.app.core.tokens.TokenPurpose
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator

private const val TokenStackKey = "tokenStack"

@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    tokenViewModel: SelectTokenViewModel,
    money: Boolean = false,
    profileAvatar: @Composable () -> Unit = {},
) {
    val balanceState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()
    WalletScreenContent(
        balanceState = balanceState,
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent,
        money = money,
        profileAvatar = profileAvatar,
    )
}

@Composable
internal fun WalletScreenContent(
    balanceState: WalletViewModel.State,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (WalletViewModel.Event) -> Unit,
    money: Boolean = false,
    profileAvatar: @Composable () -> Unit = {},
) {
    // Time-to-full-display for a logged-in launch. This tab is where the app lands, and the gate
    // below means "landed" is not the same frame as "composed" -- registering a reporter here holds
    // the activity's report open until the content below the gate has actually been drawn. Keyed to
    // the same condition as that gate: what the launch is finished for is the balance and the deck.
    ReportDrawnWhenSeam { !tokenState.isAwaitingTokens }

    // The tab waits on the token set and nothing else. Tokens are what the balance and the card deck
    // are made of, so there is no drawing the tab without them; the activity preview and the Add Money
    // row are separate reads that arrive later and fill in underneath.
    //
    // Waiting on the activity feed here as well (the earlier gate was `isAwaitingTokens ||
    // isAwaitingActivity`) held a resolved balance behind an unresolved history. The two things that
    // gate bought are both kept below without the wait: the activity section draws only once there
    // are rows to draw, and by then tokens have resolved, so a convert row titles itself
    // "USDF -> Dad Cash" from both mints rather than falling back to the server's bare "Converted";
    // and the Add Money row, the one thing that would be *wrong* rather than late if drawn from a
    // still-empty cache -- telling an established account it is new -- keeps the activity wait
    // explicitly. BalanceHeader's own spinner stays dead code: the header is only ever drawn once
    // the balance has resolved.
    if (tokenState.isAwaitingTokens) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = LocalTabBarPadding.current.calculateBottomPadding()),
            contentAlignment = Alignment.Center,
        ) {
            CodeCircularProgressIndicator()
        }
        return
    }

    // A claim the user just accepted, held until the bill it came from is off screen so the money
    // arrives rather than being there already. Reported as displayed *after* the loading gate above,
    // so a slow tab doesn't spend its share of the hold behind a spinner.
    val reveal = balanceState.reveal
    LaunchedEffect(reveal != null) {
        if (reveal != null) dispatchEvent(WalletViewModel.Event.OnRevealDisplayed)
    }

    // A card in a currency the wallet did not hold. Set while the reveal is up so the deck has it in
    // its layout from the first frame — held off-stage, not withheld — and outlives the reveal, which
    // is the moment it is released to rise into the slot already made for it.
    var arrivingMint by remember { mutableStateOf<Mint?>(null) }
    LaunchedEffect(reveal?.mint, reveal?.isNewToken) {
        val mint = reveal?.mint ?: return@LaunchedEffect
        if (reveal.isNewToken) arrivingMint = mint
    }
    LaunchedEffect(arrivingMint, reveal == null) {
        if (arrivingMint == null || reveal != null) return@LaunchedEffect
        // Timed from the reveal ending rather than from the flag being set, because that is the frame
        // the arrival starts on — the hold before it has no fixed length. Long enough to cover the
        // rise; clearing it stops a later return to the tab replaying the animation.
        delay(ArrivalRetention)
        arrivingMint = null
    }

    val listState = rememberLazyListState()
    // Px the token stack has scrolled above the viewport top, read live so the stack collapses (then
    // releases and scrolls off) as the list scrolls. A lambda so the stack reads it in its placement
    // phase without recomposing.
    val scrolledPast = {
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TokenStackKey }
            ?.let { -it.offset.toFloat() } ?: 0f
    }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val grid = CodeTheme.dimens.grid

    // Card-expand (iOS #587): tapping a card asks the app-level host (LocalCardExpansion) to expand its
    // currency-info as an overlay above this screen. The deck stays composed and reorganises in step with
    // the shared progress scalar — cards part around the tapped one (which is hidden here; the overlay
    // draws the flying hero) and the header fades.
    val cardExpansion = LocalCardExpansion.current
    val expansionScope = rememberCoroutineScope()
    val expandingMint = cardExpansion?.expandedKey as? Mint
    val heroProgress = {
        cardExpansion?.let { CardExpansionController.heroProgress(it.progress.value) } ?: 0f
    }

    LazyColumn(
        state = listState,
        // Only ever in the tree past the loading gate above, so it is the anchor for "the wallet
        // drew its content" -- what the startup benchmark waits on before it stops the trace. Same
        // condition as ReportDrawnWhenSeam above, so the trace stays open exactly as long as the report.
        modifier = Modifier
            .fillMaxSize()
            .testTag("wallet_content"),
        contentPadding = PaddingValues(
            // Money opens on its title, so it clears the status bar itself; the wallet's header starts
            // 96 dp down and never needed to.
            top = if (money) statusBarInset + CodeTheme.dimens.grid.x2 else CodeTheme.dimens.inset,
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            // Clearance under the last row (the action tiles) once the list is scrolled to the end. The
            // tab-bar inset already covers the floating bar and its own 15 dp drop, so this is only the
            // visible gap above the bar: 40 dp, matching iOS (a 96 pt trailing spacer against a 58 pt bar).
            bottom = LocalTabBarPadding.current.calculateBottomPadding() + CodeTheme.dimens.grid.x8,
        ),
    ) {
        if (money) {
            moneyHeader(
                balance = reveal?.let { LocalFiat.fromUsd(it.totalBefore, tokenState.rate) }
                    ?: tokenState.totalBalance,
                currencyCount = tokenState.tokens?.size ?: 0,
                balanceAlpha = { 1f - heroProgress() },
                profileAvatar = profileAvatar,
                dispatchEvent = dispatchEvent,
            )
        } else item {
            // v2 wallet header: 96 dp top / 44 dp bottom per Figma node 8966:1578.
            BalanceHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fade the balance out as the deck parts behind the opening card (iOS deckOpacity).
                    .graphicsLayer { alpha = 1f - heroProgress() },
                // AnimatedNumberText rolls whichever digits change, so opening on the pre-claim
                // total is all the tick-up needs.
                balance = reveal?.let { LocalFiat.fromUsd(it.totalBefore, tokenState.rate) }
                    ?: tokenState.totalBalance,
                appreciation = tokenState.aggregateAppreciation,
                topPadding = 96.dp,
                bottomPadding = 44.dp,
                appreciationStyle = AppreciationStyle.Pill,
            ) {
                dispatchEvent(WalletViewModel.Event.OpenCurrencySelection)
            }
        }

        if (!money) item {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
        }

        // Money has its own Add Money button, so the row that stands in for one is the wallet's alone.
        // The added-money answer is a read of a local cache that starts empty on a fresh login, so
        // an established account would be shown this row until its history arrived. It has to wait
        // the feed out (see State.showAddMoney) -- drawing it early is wrong, not merely early.
        if (!money && balanceState.showAddMoney) {
            item {
                AddMoneyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = CodeTheme.dimens.grid.x5),
                ) {
                    dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
                }
            }
        }

        // Money shows the currencies as a count that opens their list instead of the deck.
        if (!money) tokenState.tokens
            ?.let { tokens ->
                // A currency the wallet already held: hold that card's own number back too, so it
                // rolls in step with the total above it instead of sitting there already updated. A
                // card that is only now arriving has no earlier number to roll from — it rises into
                // the deck showing what it holds.
                if (reveal == null || reveal.isNewToken) tokens
                else tokens.map { entry ->
                    if (entry.token.address != reveal.mint) entry
                    else entry.copy(
                        balance = LocalFiat.fromUsd(
                            usdf = reveal.mintBalanceBefore,
                            rate = tokenState.rate,
                            mint = reveal.mint,
                        )
                    )
                }
            }
            ?.takeIf { it.isNotEmpty() }?.let { tokens ->
            item(key = TokenStackKey) {
                TokenCardStack(
                    tokens = tokens,
                    arrival = arrivingMint?.let { TokenCardArrival(mint = it, held = reveal != null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_token_card_stack"),
                    pinInset = statusBarInset + CodeTheme.dimens.grid.x2,
                    scrolledPast = scrolledPast,
                    expansion = expandingMint?.let { mint ->
                        TokenCardExpansion(
                            mint = mint,
                            heroTarget = cardExpansion?.heroBounds,
                            progress = heroProgress,
                            pullOffset = { cardExpansion?.pullOffset ?: 0f },
                        )
                    },
                    onCardClick = { token, bounds ->
                        // Expand the tapped card's currency-info as an overlay (app-level host draws it);
                        // the deck reorganises here in step with the shared progress scalar.
                        cardExpansion?.let { controller ->
                            controller.begin(token.token.address, bounds)
                            expansionScope.launch {
                                // Snap to a fully-collapsed deck first: tapping a card while a prior one is
                                // still collapsing would otherwise open from that card's mid-progress, so the
                                // whole deck (including the one just closed) jumps into a half-reorganised
                                // pose and animates from there ("stacked" enter animations).
                                controller.snapTo(0f)
                                controller.animateTo(1f)
                            }
                        }
                    },
                )
            }
        }

        if (money && balanceState.transactions.isNotEmpty()) {
            moneyActivitySection(
                transactions = balanceState.transactions,
                modifier = Modifier
                    .padding(top = grid.x6)
                    .clickable {
                        dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Sheets.ActivityHistory))
                    }
                    .padding(top = grid.x4, bottom = grid.x1),
                onItemClick = { item ->
                    dispatchEvent(WalletViewModel.Event.OpenTransaction(item))
                },
            )
        } else if (balanceState.transactions.isNotEmpty()) {
            // Preview of the most recent activity (newest first); the full paged history lives on its own
            // screen. Tap the header to dive in. Shared with the token-info screen for consistency.
            recentActivitySection(
                transactions = balanceState.transactions,
                modifier = Modifier
                    .padding(top = grid.x2)
                    .clickable {
                        dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Sheets.ActivityHistory))
                    }
                    .padding(top = grid.x4, bottom = grid.x1),
                onItemClick = { item ->
                    dispatchEvent(WalletViewModel.Event.OpenTransaction(item))
                },
            )
        }

        // Money moves these to its two buttons and drops the rest (node 13729:744).
        if (!money && balanceState.hasReceivedMoney) {
            item {
                Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
            }

            item {
                // 2x2 action grid. Fixed, width-proportional tile height (like iOS) so tiles are
                // equal height and tall enough for the icon (top) and label (bottom) to sit apart —
                // labels bottom-align even when one wraps to two lines.
                Column(
                    modifier = Modifier.testTag("wallet_tiles"),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        // Currency creation now lives here rather than as a Discover promo.
                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(Res.string.action_createYourOwnCurrency),
                            icon = painterResource(Res.drawable.ic_coins_add),
                        ) {
                            dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Token.CurrencyCreator))
                        }

                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(Res.string.action_currencyLeaderboard),
                            icon = painterResource(Res.drawable.ic_globe),
                        ) {
                            dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Token.Discovery))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        // ic_menu_deposit/ic_menu_withdraw are the app's canonical glyphs for this
                        // pair (the Settings-sheet money tiles) — matched arrows, not ad-hoc icons.
                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(Res.string.action_addDollars),
                            icon = painterResource(Res.drawable.ic_menu_deposit),
                        ) {
                            dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
                        }

                        TileButton(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.45f),
                            style = TileButtonStyle.Spread,
                            text = stringResource(Res.string.action_withdrawDollars),
                            icon = painterResource(Res.drawable.ic_menu_withdraw),
                        ) {
                            // No preselected mint: the flow opens on the currency picker, which lists
                            // every balance (Dollars included).
                            dispatchEvent(
                                WalletViewModel.Event.OpenScreen(
                                    AppRoute.Transfers.Withdrawal(preselectedMint = null)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The top of the Money tab (node 13729:744): its title with the profile avatar that opens You, the
 * balance alone, the number of currencies held, which opens their list, and Add Money beside Withdraw.
 * Both buttons start today's flows, the same ones the wallet's tiles start.
 *
 * @param balanceAlpha The balance's opacity, read in the draw phase so a currency opening over the
 *   tab fades it the way it fades the wallet's.
 */
private fun LazyListScope.moneyHeader(
    balance: LocalFiat?,
    currencyCount: Int,
    balanceAlpha: () -> Float,
    profileAvatar: @Composable () -> Unit,
    dispatchEvent: (WalletViewModel.Event) -> Unit,
) {
    item(key = "money_title") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.title_tabMoney),
                style = CodeTheme.typography.screenTitleLarge,
                color = CodeTheme.colors.textMain,
            )
            Box(
                modifier = Modifier
                    .size(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape)
                    .clickable { dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Tabs.Menu)) }
                    .testTag("money_profile"),
                contentAlignment = Alignment.Center,
            ) {
                profileAvatar()
            }
        }
    }

    item(key = "money_balance") {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = balanceAlpha() }
                .testTag("money_balance"),
            balance = balance,
            appreciation = null,
            bottomPadding = CodeTheme.dimens.grid.x1,
        ) {
            dispatchEvent(WalletViewModel.Event.OpenCurrencySelection)
        }
    }

    item(key = "money_currency_count") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clickable {
                        dispatchEvent(
                            WalletViewModel.Event.OpenScreen(AppRoute.Sheets.TokenSelection(TokenPurpose.Balance))
                        )
                    }
                    .padding(vertical = CodeTheme.dimens.grid.x1),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(Res.plurals.label_currencyCount, currencyCount, currencyCount),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
                Icon(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
                    painter = painterResource(UiRes.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            }
        }
    }

    item(key = "money_actions") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x6),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            MoneyActionButton(
                text = stringResource(Res.string.action_addMoney),
                primary = true,
                modifier = Modifier.weight(1f),
            ) {
                dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
            }
            MoneyActionButton(
                text = stringResource(Res.string.action_withdraw),
                primary = false,
                modifier = Modifier.weight(1f),
            ) {
                // No preselected mint, as from the wallet's tile: the flow opens on the currency picker.
                dispatchEvent(WalletViewModel.Event.OpenScreen(AppRoute.Transfers.Withdrawal(preselectedMint = null)))
            }
        }
    }
}

/**
 * How long a newly-claimed mint stays flagged as arriving — the rise plus slack, after which the
 * card is just another card in the deck.
 */
private val ArrivalRetention = 1500.milliseconds
