package com.flipcash.app.scanner.internal.bills

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.flipcash.features.scanner.R
import com.kik.kikx.kincodes.KikCodeContentView

@Composable
internal fun ScannableCode(
    modifier: Modifier = Modifier,
    data: List<Byte>,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth(),
            factory = { context ->
                KikCodeContentView(context).apply {
                    this.logo =
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_logo_round_white
                        )
                    this.encodedKikCode = data.toByteArray()
                }
            },
            update = { }
        )
    }
}