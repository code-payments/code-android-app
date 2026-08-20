package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.getcode.navigation.flow.flowSharedViewModel

/**
 * The user's own tip card — a step in the tipping [TippingFlowScreen] flow, so it shares the flow's
 * [TipFlowViewModel]. It is the post-profile-setup landing (the flow seeded at TipCard): the card
 * full-bleed, no chrome. The primary home for the card is the "You" tab (the menu), which also owns
 * settings.
 */
@Composable
fun TipCardScreen() {
    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        TipCardArt(card = state.tipCard, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TipCardArt(card: Scannable.TipCard?, modifier: Modifier = Modifier) {
    if (card == null) return
    CompositionLocalProvider(
        // Static display (no camera behind the card), so render it opaque at the design's flattened
        // colour rather than the translucent frosted fill — the 36% alpha otherwise multiplies the
        // app's Brand background and reads incorrectly. Figma flattens the card to rgb(16,16,17).
        LocalTipCardColor provides Color(0xFF101011),
        LocalTipCardBaseAlpha provides 1f,
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ScannableRenderer(scannable = card)
        }
    }
}
