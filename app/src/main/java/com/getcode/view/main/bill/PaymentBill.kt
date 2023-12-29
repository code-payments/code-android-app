package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.getcode.R
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.theme.CodeTheme
import com.getcode.view.main.home.components.PriceWithFlag
import com.kik.kikx.kincodes.KikCodeContentView

@Composable
internal fun PaymentBill(
    modifier: Modifier = Modifier,
    data: List<Byte>,
    amount: KinAmount,
    currencyCode: CurrencyCode?,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .requiredHeight(screenHeight * 0.62f)
            .background(CodeTheme.colors.onBackground, shape = RoundedCornerShape(8.dp))
            .then(modifier)
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
                                this.encodedKikCode = data.toByteArray()
                            }
                        },
                        update = { }
                    )
                }
            }

            if (currencyCode != null) {
                PriceWithFlag(currency = currencyCode, amount = amount)
            }
        }
    }
}