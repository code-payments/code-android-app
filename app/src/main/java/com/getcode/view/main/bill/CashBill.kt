package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import com.getcode.LocalCurrencyUtils
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.theme.CodeTheme
import com.getcode.util.formatted
import com.getcode.util.formattedRaw
import com.getcode.util.nonScaledSp
import com.getcode.util.toDp
import com.kik.kikx.kincodes.KikCodeContentView

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashBill(
    modifier: Modifier = Modifier,
    payloadData: List<Byte>,
    amount: KinAmount,
) {
    ConstraintLayout(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .aspectRatio(0.555f)
            .heightIn(0.dp, 800.dp)
            .fillMaxHeight(0.85f)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x8)
    ) {
        val (billImage, code, billText1, billText2, addressText) = createRefs()
        var sizeBillImage by remember { mutableStateOf(IntSize.Zero) }

        Image(
            painterResource(
                R.drawable.ic_bill2
            ),
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .constrainAs(billImage) {
                    centerTo(parent)
                }
                .onGloballyPositioned {
                    sizeBillImage = it.size
                }
        )

        val whRatio = 0.555f
        val amountOffsetY = 50
        val amountOffsetX = 10

        val ratio = if (sizeBillImage.height == 0) {
            0
        } else {
            sizeBillImage.width / sizeBillImage.height
        }

        val widthI: Float = if (ratio > whRatio) {
            sizeBillImage.height * whRatio
        } else {
            sizeBillImage.width.toFloat()
        }

        BillAmount(
            modifier = Modifier
                .offset(y = (amountOffsetY).dp, x = (amountOffsetX).dp)
                .constrainAs(billText1) {
                    start.linkTo(parent.start)
                    top.linkTo(billImage.top)
                },
            text = amount.formattedRaw()
        )

        BillAmount(
            modifier = Modifier
                .offset(y = -(amountOffsetY).dp, x = -(amountOffsetX).dp)
                .constrainAs(billText2) {
                    end.linkTo(parent.end)
                    bottom.linkTo(billImage.bottom)
                },
            text = amount.formattedRaw()
        )
        Text(
            modifier = Modifier
                .offset(y = -(40).dp, x = (10).dp)
                .constrainAs(addressText) {
                    start.linkTo(billImage.start)
                    bottom.linkTo(parent.bottom)
                },
            text = "kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6",
            fontSize = 8.nonScaledSp,
            color = CodeTheme.colors.onBackground.copy(alpha = 0.60f)
        )

        if (payloadData.isNotEmpty()) {
            AndroidView(
                modifier = Modifier
                    .constrainAs(code) {
                        start.linkTo(billImage.start)
                        end.linkTo(billImage.end)
                        top.linkTo(billImage.top)
                        bottom.linkTo(billImage.bottom)
                        height = Dimension.fillToConstraints
                    }
                    .width(((widthI).toDp * 0.65f).dp),
                factory = { context ->
                    KikCodeContentView(context).apply {
                        this.logo =
                            ContextCompat.getDrawable(context, R.drawable.ic_logo_round_white)
                        this.encodedKikCode = payloadData.toByteArray()
                    }
                },
                update = { }
            )
        }
    }
}