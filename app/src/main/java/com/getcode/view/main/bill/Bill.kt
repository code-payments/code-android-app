package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import com.kik.kikx.kincodes.KikCodeContentView
import com.getcode.R
import com.getcode.util.nonScaledSp
import com.getcode.util.toDp

@Composable
@Preview
fun Bill(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    payloadData: List<Byte> = listOf(),
    amount: String = ""
) {
    ConstraintLayout(
        modifier = modifier
            .aspectRatio(0.555f)
            .heightIn(0.dp, 800.dp)
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 40.dp)
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
            text = amount
        )

        BillAmount(
            modifier = Modifier
                .offset(y = -(amountOffsetY).dp, x = -(amountOffsetX).dp)
                .constrainAs(billText2) {
                    end.linkTo(parent.end)
                    bottom.linkTo(billImage.bottom)
                },
            text = amount
        )
        Text(
            modifier = Modifier
                .offset(y = -(40).dp, x = (10).dp)
                .constrainAs(addressText) {
                    start.linkTo(billImage.start)
                    bottom.linkTo(parent.bottom)
                },
            text = "kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6",
            fontSize = 8.nonScaledSp
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