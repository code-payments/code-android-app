package com.getcode.view.main.bill

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.getcode.R
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.theme.CodeTheme
import com.getcode.theme.DashEffect
import com.getcode.theme.receipt
import com.getcode.theme.withRobotoMono
import com.getcode.view.main.home.components.PriceWithFlag
import com.kik.kikx.kincodes.KikCodeContentView

@Composable
internal fun PaymentReceipt(
    modifier: Modifier = Modifier,
    data: List<Byte>,
    amount: KinAmount,
    currencyCode: CurrencyCode?,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(
                top = screenHeight * 0.10f,
                bottom = CodeTheme.dimens.grid.x12
            )
    ) {
        val mW = maxWidth
        val size = remember { mW * 0.65f }

        Column(
            modifier = Modifier
                .background(CodeTheme.colors.onBackground, shape = CodeTheme.shapes.receipt())
                .padding(top = CodeTheme.dimens.grid.x12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5)
        ) {
            if (data.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .size(size)
                        .background(CodeTheme.colors.brandMuted, CircleShape),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = CodeTheme.dimens.grid.x8,
                            bottom = CodeTheme.dimens.grid.x17,
                        )
                        .padding(horizontal = CodeTheme.dimens.inset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x9)
                ) {
                    DoubleDashedLine()
                    ProvideTextStyle(
                        value = CodeTheme.typography.body1
                            .withRobotoMono(weight = FontWeight.W500)
                    ) {
                        PriceWithFlag(currencyCode = currencyCode, amount = amount)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoubleDashedLine(
    modifier: Modifier = Modifier,
    dashColor: Color = DashEffect,
    dashLength: Dp = CodeTheme.dimens.staticGrid.x1,
) {
    @Composable
    fun DashedLine() {
        val density = LocalDensity.current
        val dashLengthPx = with(density) { dashLength.toPx() }
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, dashLengthPx), 0f)
        Canvas(
            Modifier
                .fillMaxWidth(),
        ) {
            drawLine(
                color = dashColor,
                start = Offset(0f, 0f),
                strokeWidth = 1.dp.toPx(),
                end = Offset(this.size.width - dashLengthPx, 0f),
                pathEffect = pathEffect
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1)
    ) {
        DashedLine()
        DashedLine()
    }
}