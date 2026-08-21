package com.flipcash.app.menu.internal.tipcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.features.menu.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.core.noRippleClickable

/**
 * The viewer's own tip card, full screen (node 9277:121410).
 *
 * A route rather than a presented bill: the card is a static thing to hold up to a camera, so it
 * gets the whole screen — the You tab's content and the tab bar leave with the push (the tab bar
 * hides itself on any non-tab route) instead of the card competing with them under a scrim.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TipCardScreenContent(viewModel: TipCardScreenViewModel, onClose: () -> Unit) {
    val card by viewModel.card.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .noRippleClickable { onClose() },
    ) {
        if (card != null) {
            // Static display, no camera behind it: opaque at the design's flattened colour rather
            // than the translucent frosted fill. Figma flattens the card to rgb(16,16,17).
            CompositionLocalProvider(
                LocalTipCardColor provides Color(0xFF101011),
                LocalTipCardBaseAlpha provides 1f,
            ) {
                ScannableRenderer(
                    // The card pads itself off the status bar for its in-scanner placement; here it
                    // is centred on the whole screen (the design centres it on the full frame), so
                    // consume that inset instead of paying it.
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(WindowInsets.statusBarsIgnoringVisibility),
                    scannable = requireNotNull(card),
                    tipCardWidth = fullScreenCardWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = CodeTheme.dimens.grid.x2)
                .noRippleClickable { onClose() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(R.string.action_closeFullScreen),
                style = CodeTheme.typography.textSmall,
                color = White50,
            )
            Icon(
                // Same glyph as the "Full Screen ⌄" this was opened from, flipped — the affordance
                // reverses.
                modifier = Modifier
                    .size(16.dp)
                    .rotate(180f),
                painter = painterResource(R.drawable.ic_chevron_down_medium),
                contentDescription = null,
                tint = White50,
            )
        }
    }
}

/**
 * Node 9277:121410 puts the card at 302.21 on a 402-wide frame, so it scales with the device rather
 * than sitting at a fixed width. The cap only exists to stop a tablet blowing it up past the size a
 * phone camera expects to read.
 */
@Composable
private fun fullScreenCardWidth(): Dp =
    minOf(CodeTheme.dimens.screenWidth * FullScreenCardWidthFraction, FullScreenCardMaxWidth)

private const val FullScreenCardWidthFraction = 302.21f / 402f
private val FullScreenCardMaxWidth: Dp = 360.dp
