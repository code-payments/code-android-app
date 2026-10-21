package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.bills.components.cards.TipCardFlattened
import com.flipcash.app.core.AppRoute
import com.flipcash.app.menu.internal.CardOverlay
import com.flipcash.app.menu.internal.ProfileCardViewModel
import com.getcode.theme.CodeTheme

/**
 * A person's profile card, full screen, behind `AppRoute.Menu.ProfileCard`: the viewer's own or
 * anyone else's, opened from the share sheet's Show Profile Card tile. It presents like
 * a scanned bill: the backdrop fades up over the share sheet, which stays open beneath it, while
 * the card pops in on a spring. Closing, by button or back, fades the whole screen out before the
 * route pops; the route is a [com.getcode.navigation.FullscreenOverlay], so navigation draws no
 * transition of its own. Download, where the You tab keeps its settings gear, hands the card to
 * the system share sheet as an image.
 */
@Composable
fun ProfileCardScreen(route: AppRoute.Menu.ProfileCard) {
    val viewModel = hiltViewModel<ProfileCardViewModel, ProfileCardViewModel.Factory>(
        creationCallback = { factory -> factory.create(route.userId) },
    )
    val card = viewModel.card

    CardOverlay(
        // Nothing to export until the card resolves (or if it never does).
        onDownload = if (card != null) viewModel::download else null,
        downloadTestTag = "you-download-button",
        closeTestTag = null,
    ) { cardModifier ->
        if (card != null) {
            // Static backdrop, so draw the card opaque at its flattened tone, as the You tab did.
            CompositionLocalProvider(
                LocalTipCardColor provides TipCardFlattened,
                LocalTipCardBaseAlpha provides 1f,
            ) {
                ScannableRenderer(
                    modifier = cardModifier.align(Alignment.Center),
                    scannable = card,
                    tipCardWidth = CodeTheme.dimens.screenWidth * CardWidthFraction,
                )
            }
        }
    }
}

/** How much of the display the card spans, the proportion the earlier full-screen card used. */
private const val CardWidthFraction = 0.75f
