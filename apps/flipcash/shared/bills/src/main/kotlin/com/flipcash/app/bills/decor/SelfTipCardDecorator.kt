package com.flipcash.app.bills.decor

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.bill.Scannable
import com.flipcash.shared.bills.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.core.noRippleClickable

/**
 * Decor for the viewer's OWN tip card, presented full screen from the You tab (node 9277:121410).
 *
 * There's no tip modal here — you can't tip yourself — so the only below-card content is the
 * "Close" affordance that mirrors the "Full Screen" one the card was opened from. Swipe still
 * dismisses; this just makes that discoverable.
 */
internal data object SelfTipCardDecorator : ScannableDecorator {
    @Composable
    override fun BoxScope.Content(context: ScannableDecoratorContext) {
        AnimatedScannableDecorator(
            visible = context.liveBill is Scannable.TipCard,
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
                    .noRippleClickable { context.onDismiss() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_closeFullScreen),
                    style = CodeTheme.typography.textSmall,
                    color = White50,
                )
                Icon(
                    // Same glyph as "Full Screen ⌄", flipped — the affordance reverses.
                    modifier = Modifier.size(16.dp).rotate(180f),
                    painter = painterResource(R.drawable.ic_chevron_down_medium),
                    contentDescription = null,
                    tint = White50,
                )
            }
        }
    }
}
