package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.getcode.R
import com.getcode.model.CodePayload
import com.getcode.network.repository.Request
import com.getcode.theme.CodeTheme
import com.getcode.theme.Typography
import com.getcode.util.CurrencyUtils
import com.getcode.util.FormatAmountUtils
import com.getcode.util.toAGColor
import com.getcode.util.toDp
import com.getcode.utils.FormatUtils
import com.kik.kikx.kincodes.KikCodeContentView
import timber.log.Timber

@Composable
internal fun PaymentBill(
    modifier: Modifier = Modifier,
    request: Request,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(0.62f)
            .fillMaxWidth(0.88f)
            .background(CodeTheme.colors.onBackground, shape = RoundedCornerShape(8.dp))
    ) {
        Image(
            painter = painterResource(
                R.drawable.payment_bill_pattern
            ),
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val mW = maxWidth
        val size = remember { mW * 0.65f }

        val data = remember(request.payload) {
            request.payload.encode().toByteArray()
        }

        Image(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(15.dp),
            painter = painterResource(
                R.drawable.ic_code_logo_offwhite_small
            ),
            contentDescription = "",
        )



        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            if (data.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .size(size)
                        .background(CodeTheme.colors.brandSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .width(size),
                        factory = { context ->
                            KikCodeContentView(context).apply {
                                this.logo =
                                    ContextCompat.getDrawable(
                                        context,
                                        R.drawable.ic_logo_round_white
                                    )
                                this.encodedKikCode = data
                            }
                        },
                        update = { }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currencyCode = request.payload.fiat?.currency?.name
                val flagResId = CurrencyUtils.getFlagByCurrency(currencyCode)
                if (flagResId != null) {
                    Icon(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(20.dp),
                        painter = painterResource(id = flagResId),
                        tint = Color.Unspecified,
                        contentDescription = currencyCode?.let { "$it flag" }
                    )
                    Text(
                        text = FormatAmountUtils.formatAmountString(request.amount),
                        color = Color.Black,
                        style = MaterialTheme.typography.body1
                    )
                }
            }
        }
    }
}