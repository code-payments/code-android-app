package com.flipcash.app.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme

/**
 * The amount column of an activity/transaction row: the entry in the viewer's own currency, the
 * only currency the app shows. A tip of 7,500 pesos reads "-$5.00" to a viewer in dollars.
 *
 * The amount carries [signPrefix] — see [sign] for the amount that signs itself.
 */
@Composable
fun ActivityAmount(
    amount: LocalFiat,
    modifier: Modifier = Modifier,
    signPrefix: String? = null,
) {
    val viewer = amount.inDisplayCurrency()

    Text(
        modifier = modifier,
        text = viewer.formatted(extraPrefix = viewer.sign(signPrefix)),
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
    )
}

/**
 * The sign to render ahead of this amount, or null when the value already carries its own. Activity
 * amounts arrive as magnitudes with their direction alongside them, which is why the row supplies a
 * sign at all; a value that is genuinely negative formats its own "-", and prefixing that as well
 * would read "--$5.00".
 */
internal fun Fiat.sign(prefix: String?): String? = prefix?.takeUnless { isNegative }

