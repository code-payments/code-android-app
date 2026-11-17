package com.flipcash.app.internal.ui.navigation

import androidx.compose.runtime.Composable
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.cardexpand.LocalCardExpansion
import com.flipcash.app.tokens.CurrencyCardExpandHost

/**
 * Hosts the wallet card-expand overlay INSIDE the wallet nav entry (see [CurrencyCardExpandHost]).
 *
 * The [CardExpansionController] itself is provided at the app root (see AppContent), so its fly-state
 * (progress, source/hero bounds, expandedKey) SURVIVES this entry's composition being torn down when a
 * screen is pushed over the wallet — the overlay re-inflates from that surviving controller state on the
 * way back, rather than reopening from scratch.
 */
@Composable
internal fun CardExpandHost(content: @Composable () -> Unit) {
    CurrencyCardExpandHost(controller = LocalCardExpansion.current, content = content)
}
