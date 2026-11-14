package com.flipcash.app.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.flipcash.app.cardexpand.CardExpansionController
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the card-expand overlay over a screen that draws a token card deck (iOS `WalletScreen`
 * structure): the expanded currency-info is drawn over [content] but still WITHIN that screen's nav
 * entry, so a pushed action screen (Give / Convert / Withdraw) covers it with correct z-order and the
 * deck reorganises behind it — while a plain dismiss collapses the overlay back into the deck.
 *
 * The wallet hosts it with the app-root [CardExpansionController] (see the app's `CardExpandHost`);
 * the Group Money shell's currencies screen hosts it with a controller of its own, so neither host
 * draws the other's expansion.
 */
@Composable
fun CurrencyCardExpandHost(
    controller: CardExpansionController?,
    content: @Composable () -> Unit,
) = CardExpandHostLayout(
    controller = controller,
    overlay = { expansion, mint, collapse ->
        CurrencyInfoExpansion(controller = expansion, mint = mint, onCollapse = collapse)
    },
    content = content,
)

/** [CurrencyCardExpandHost] with the overlay as a slot, so a host test can swap in a stand-in. */
@Composable
internal fun CardExpandHostLayout(
    controller: CardExpansionController?,
    overlay: @Composable (controller: CardExpansionController, mint: Mint, onCollapse: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
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

    // A source-less expansion (a deeplink — CardExpansionController.beginExpanded) has no deck card to
    // fly from, so there is nothing to animate: it lands already open, like iOS's
    // `WalletScreen.openCardImmediately`. Wait for the overlay's hero slot to report its frame before
    // snapping, so the first fully-visible frame already has its card in place rather than an empty slot.
    LaunchedEffect(controller.expandedKey) {
        val key = controller.expandedKey ?: return@LaunchedEffect
        if (controller.sourceBounds != null) return@LaunchedEffect
        snapshotFlow { controller.heroBounds }.filterNotNull().first()
        if (controller.expandedKey === key) {
            controller.snapTo(1f)
        }
    }

    Box(modifier = Modifier) {
        content()

        (controller.expandedKey as? Mint)?.let { mint ->
            overlay(controller, mint, collapse)
            BackHandler(onBack = collapse)
        }
    }
}
