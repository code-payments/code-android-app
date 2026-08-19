package com.flipcash.app.internal.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.tokens.CurrencyInfoExpansion
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.launch

/**
 * Hosts the wallet card-expand overlay INSIDE the wallet nav entry (iOS `WalletScreen` structure): the
 * expanded currency-info is drawn over [content] but still WITHIN this entry, so a pushed action screen
 * (Give / Convert / Withdraw) covers it with correct z-order and the deck reorganises behind it — while
 * a plain dismiss collapses the overlay back into the deck.
 *
 * The [CardExpansionController] itself is provided at the app root (see NewAppContent), so its fly-state
 * (progress, source/hero bounds, expandedKey) SURVIVES this entry's composition being torn down when a
 * screen is pushed over the wallet — the overlay re-inflates from that surviving controller state on the
 * way back, rather than reopening from scratch.
 */
@Composable
internal fun CardExpandHost(content: @Composable () -> Unit) {
    val controller = LocalCardExpansion.current
    if (controller == null) {
        content()
        return
    }

    val scope = rememberCoroutineScope()
    val collapse: () -> Unit = {
        scope.launch {
            controller.animateTo(0f, CardExpansionController.CollapseSpring)
            controller.clear()
        }
    }

    Box(modifier = Modifier) {
        content()

        (controller.expandedKey as? Mint)?.let { mint ->
            CurrencyInfoExpansion(
                controller = controller,
                mint = mint,
                onCollapse = collapse,
            )
            BackHandler(onBack = collapse)
        }
    }
}
