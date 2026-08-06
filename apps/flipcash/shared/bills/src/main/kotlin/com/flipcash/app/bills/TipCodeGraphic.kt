package com.flipcash.app.bills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.app.bills.components.ScannableCode

/**
 * Just the scannable tip-code graphic (with the default Flipcash logo), no card chrome or label.
 *
 * Public entry point over the internal [ScannableCode] so callers outside this module — e.g. the
 * offscreen share-preview renderer — can draw the code on its own at an explicit size.
 */
@Composable
fun TipCodeGraphic(
    payloadData: List<Byte>,
    modifier: Modifier = Modifier,
) {
    ScannableCode(
        modifier = modifier,
        data = payloadData,
        icon = null,
    )
}
