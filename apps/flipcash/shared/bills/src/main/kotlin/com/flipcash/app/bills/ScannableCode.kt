package com.flipcash.app.bills

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.flipcash.shared.bills.R
import com.getcode.opencode.model.financial.Token
import com.getcode.utils.decodeBase64
import com.kik.kikx.kincodes.KikCodeContentView

@Composable
internal fun ScannableCode(
    modifier: Modifier = Modifier,
    token: Token,
    data: List<Byte>,
) {
    val tokenBillImageBase64 = token.launchpadMetadata?.billCustomizations?.icon
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth(),
            factory = { context ->
                KikCodeContentView(context).apply {
                    this.logo = if (tokenBillImageBase64 != null) {
                        val bytes = tokenBillImageBase64.decodeBase64()
                        val bitmap: Bitmap? = BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size,
                        )
                        bitmap?.toDrawable(context.resources)
                    } else {
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_logo_round_white
                        )
                    }

                    this.encodedKikCode = data.toByteArray()
                }
            },
            update = { }
        )
    }
}