package com.flipcash.app.bills.components

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
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.utils.decodeBase64
import com.kik.kikx.kincodes.KikCodeContentView

@Composable
internal fun ScannableCode(
    data: List<Byte>,
    token: Token,
    modifier: Modifier = Modifier,
) {
    val tokenBillImageBase64 = token.billCustomizations?.icon
    ScannableCode(modifier = modifier, data = data, icon = tokenBillImageBase64)
}

@Composable
internal fun ScannableCode(
    data: List<Byte>,
    modifier: Modifier = Modifier,
    billCustomizations: TokenBillCustomizations? = null,
) {
    val tokenBillImageBase64 = billCustomizations?.icon
    ScannableCode(modifier = modifier, data = data, icon = tokenBillImageBase64)
}

@Composable
internal fun ScannableCode(
    data: List<Byte>,
    modifier: Modifier = Modifier,
    icon: ByteArray? = null,
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
                    this.logo = if (icon != null) {
                        val bytes = icon.decodeBase64()
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