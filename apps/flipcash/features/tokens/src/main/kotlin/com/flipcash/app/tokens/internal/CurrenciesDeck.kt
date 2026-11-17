package com.flipcash.app.tokens.internal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenCardExpansion
import com.flipcash.app.core.ui.TokenCardStack
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.launch

/**
 * The Money tab's "N currencies" screen (node 13729:744) draws the wallet's card deck under the Group
 * Money shell; every other picker, and that screen outside the shell, keeps the list.
 */
internal fun showsCurrencyDeck(purpose: TokenPurpose, shell: NavBarShell): Boolean =
    purpose == TokenPurpose.Balance && shell == NavBarShell.GroupMoney

/**
 * The title over the currency screen for [purpose] under [shell]: Currencies where it draws the deck,
 * as iOS titles that screen, and the picker's title everywhere else.
 */
@StringRes
internal fun tokenSelectTitle(purpose: TokenPurpose, shell: NavBarShell): Int =
    if (showsCurrencyDeck(purpose, shell)) R.string.title_currencies else pickerTitle(purpose)

@StringRes
private fun pickerTitle(purpose: TokenPurpose): Int = when (purpose) {
    is TokenPurpose.Swap -> R.string.title_selectPaymentCurrency
    is TokenPurpose.LaunchFunding -> R.string.title_selectPaymentCurrency
    else -> R.string.title_selectCurrency
}

/**
 * The currencies screen's own card expansion, apart from the app-root one the wallet uses. The wallet
 * stays composed under this sheet, so on one controller both hosts would draw the same currency, and
 * one left open here would leave the nav bar faded out on the Money tab after the sheet closed.
 *
 * Held in the screen's entry ViewModel: a pushed Give / Convert / Withdraw tears the entry's
 * composition down but keeps its ViewModel store, so the currency is still open on the way back, and
 * popping the screen drops it.
 */
internal class CurrenciesCardExpansion : ViewModel() {
    val controller = CardExpansionController()
}

@Composable
internal fun rememberCurrenciesCardExpansion(): CardExpansionController {
    val controller = viewModel { CurrenciesCardExpansion() }.controller
    // The app-root controller reads the user's real animation scale (App.kt); follow it, so "animations
    // off" opens a card instantly here too.
    val appController = LocalCardExpansion.current
    SideEffect {
        appController?.let { controller.animationScale = it.animationScale }
    }
    return controller
}

/**
 * The user's currencies as the wallet's [TokenCardStack], wired to [controller] the way the wallet
 * wires it: a tapped card flies to the expanded currency-info that a
 * [com.flipcash.app.tokens.CurrencyCardExpandHost] around this deck draws, and the rest of the deck
 * parts around it.
 */
@Composable
internal fun CurrenciesDeck(
    tokens: List<TokenWithLocalizedBalance>,
    controller: CardExpansionController,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val grid = CodeTheme.dimens.grid

    // Px the deck has scrolled above the list's top, read in the stack's placement phase so scrolling
    // only re-places the cards. Same reading as the wallet's.
    val scrolledPast = {
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == DeckKey }
            ?.let { -it.offset.toFloat() } ?: 0f
    }
    val expandingMint = controller.expandedKey as? Mint

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = grid.x2,
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + grid.x5,
        ),
    ) {
        item(key = DeckKey) {
            TokenCardStack(
                tokens = tokens,
                modifier = Modifier.fillMaxWidth(),
                scrolledPast = scrolledPast,
                expansion = expandingMint?.let { mint ->
                    TokenCardExpansion(
                        mint = mint,
                        heroTarget = controller.heroBounds,
                        progress = { CardExpansionController.heroProgress(controller.progress.value) },
                        pullOffset = { controller.pullOffset },
                    )
                },
                onCardClick = { token, bounds ->
                    controller.begin(token.token.address, bounds)
                    scope.launch {
                        // From a fully collapsed deck, as the wallet does, so a card tapped while
                        // another is still closing does not open from that one's half-parted deck.
                        controller.snapTo(0f)
                        controller.animateTo(1f)
                    }
                },
            )
        }
    }
}

private const val DeckKey = "currencies_deck"
