package com.flipcash.app.internal.ui.navigation.decorators

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.bills.BillOverlay
import com.getcode.navigation.NavMetadataKeys
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.navigation.scrim.ScrimOverlay

/**
 * Draws the app's bill overlay (tip card / payable) — and the scrim beneath it — on top of each
 * NON-sheet navigation entry.
 *
 * Previously the bill was a sibling at the app root, painted *after* the [NavDisplay]; because a
 * bottom sheet is hosted inside that same NavDisplay, the bill always covered it. Hosting the bill
 * as a per-entry decorator instead means:
 *  - it still floats above the current screen (the decorator draws it over `entry.Content()`), but
 *  - it is skipped for the sheet entry, and [NavDisplay] paints the sheet's overlay scene *above*
 *    the base scene — so a sheet opened over a presented bill (the tip token / amount pickers)
 *    renders above the bill as a normal sheet, gestures intact, without a separate window.
 *
 * Opening a sheet doesn't change the base entry, so the bill's animation state stays stable through
 * the interaction. The scrim ([LocalScrimController]) is drawn just beneath the bill so it keeps
 * dimming the screen content below the bill rather than the bill itself.
 */
@Suppress("FunctionName")
fun NavBillOverlayEntryDecorator(): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        Box(modifier = Modifier.fillMaxSize()) {
            entry.Content()

            // Sheets are painted above the base scene by NavDisplay, so they must NOT carry the
            // bill themselves — otherwise the bill would cover the sheet again.
            val isSheet = entry.metadata[NavMetadataKeys.IsSheet.key] == true
            if (!isSheet) {
                ScrimOverlay(LocalScrimController.current)
                BillOverlay()
            }
        }
    }
}

@Composable
fun rememberNavBillOverlayEntryDecorator() = remember { NavBillOverlayEntryDecorator() }
