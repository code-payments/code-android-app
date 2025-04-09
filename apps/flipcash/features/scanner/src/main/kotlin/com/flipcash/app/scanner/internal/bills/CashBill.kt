package com.flipcash.app.scanner.internal.bills

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.flipcash.app.core.money.formatted
import com.flipcash.features.scanner.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.core.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.punchCircle
import com.getcode.ui.core.punchRectangle
import com.getcode.ui.utils.Geometry
import com.getcode.ui.utils.nonScaledSp
import kotlin.math.ceil
import kotlin.math.roundToInt

@Suppress("ConstPropertyName")
private object CashBillDefaults {
    const val AspectRatio = 0.555f
    val BillColor: Color = Color(red = 44, green = 42, blue = 65)

    const val CodeBackgroundOpacity = 0.65f

    const val SecurityStripCount = 3

    val DecorColor: Color = Color(0xFFA9A9B1)
}

private class CashBillGeometry(width: Dp, height: Dp) : Geometry(width, height) {

    val brandWidth: Dp
        get() = ceil(size.width.value * 0.18f).dp

    override val codeSize: Dp
        get() = size.width * 0.6f

    val globeWidth: Dp
        get() = size.width * 1.5f
    val globePosition: Offset
        get() = Offset(
            x = -(size.width.value * 0.75f),
            y = size.height.value * 0.65f
        )

    val gridWidth: Dp
        get() = size.width * 1.75f

    val gridHeight: Dp
        get() = size.height * 0.7f

    val gridPosition: Offset
        get() = Offset(
            x = 0f,
            y = securityStripPosition.y.value + securityStripSize.height.value
        )

    val linesHeight: Dp
        get() = (topStripHeight.value - 2).dp
    val lineSpacing: Dp
        get() = ceil(size.width.value * 0.032f).dp

    val mintPadding: Dp
        get() = ceil(size.height.value * 0.01f).dp
    val securityStripSize: DpSize
        get() = DpSize(size.width, ceil(size.height.value * 0.063f).dp)

    val securityStripPosition: DpOffset
        get() = DpOffset(
            x = 0.dp,
            y = (topStripHeight)
        )

    val topStripHeight: Dp
        get() = ceil(size.height.value * 0.05f).dp

    val valuePadding: Dp
        get() = ceil(size.width.value * 0.025f).dp

    val wavesPosition: Offset
        get() = Offset(
            x = (size.width.value * 0.5f),
            y = (size.height.value * 0.9f)
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashBill(
    modifier: Modifier = Modifier,
    payloadData: List<Byte>,
    amount: LocalFiat,
) {
    val exchange = LocalExchange.current
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x2
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(CashBillDefaults.AspectRatio, matchHeightConstraintsFirst = true)
                .fillMaxHeight()
                .fillMaxWidth(0.95f)
                .background(CashBillDefaults.BillColor)
                .clipToBounds()
        ) {
            val geometry = remember(maxWidth, maxHeight) {
                CashBillGeometry(maxWidth, maxHeight)
            }

            // Hexagons
            BillDecorImage(
                modifier = Modifier
                    .fillMaxSize(),
                image = loadBillAsset(R.drawable.ic_bill_hexagons),
                blendMode = BlendMode.Multiply,
                alpha = 0.6f,
            )

            // Grid pattern
            BillDecorImage(
                modifier = Modifier
                    .fillMaxSize(),
                image = loadBillAsset(R.drawable.ic_bill_grid),
                size = DpSize(width = geometry.gridWidth, height = geometry.gridHeight),
                topLeft = Offset(
                    x = geometry.gridPosition.x,
                    y = geometry.gridPosition.y,
                ),
                alpha = 0.5f,
            )

            // Globe
            Image(
                modifier = Modifier
                    .fillMaxHeight()
                    .requiredWidth(geometry.globeWidth)
                    .offset {
                        IntOffset(
                            x = geometry.globePosition.x.toInt(),
                            y = geometry.globePosition.y.toInt()
                        )
                    },
                painter = painterResource(R.drawable.ic_bill_globe),
                contentDescription = null
            )

            // Waves
            Image(
                modifier = Modifier
                    .requiredWidth(geometry.globeWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(x = geometry.wavesPosition.x.toInt(), y = 0) }
                    .drawWithGradient(
                        color = CashBillDefaults.BillColor.copy(CashBillDefaults.CodeBackgroundOpacity),
                        startY = { it / 2f },
                        blendMode = BlendMode.DstIn
                    ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                painter = painterResource(R.drawable.ic_bill_waves),
            )

            // Security strip
            SecurityStrip(geometry = geometry)


            // Bill Value Top Left
            BillAmount(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(start = geometry.valuePadding),
                text = amount.formatted,
                flag = exchange.getFlagByCurrency(amount.rate.currency.name)
            )

            // Bill Value Bottom Right
            BillAmount(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(end = geometry.valuePadding),
                text = amount.formatted,
                flag = exchange.getFlagByCurrency(amount.rate.currency.name)
            )

            // Lines
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = geometry.valuePadding, end = geometry.valuePadding * 2),
            ) {
                Row(
                    modifier = Modifier
                        .height(geometry.linesHeight)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // September
                    Lines(count = 9, spacing = geometry.lineSpacing)
                    // Sept 12
                    Lines(count = 12, spacing = geometry.lineSpacing)
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.padding(bottom = geometry.mintPadding)
                ) {
                    // Mint
                    Text(
                        text = Mint.kin.base58(),
                        fontSize = 8.nonScaledSp,
                        color = CashBillDefaults.DecorColor,
                    )
                }

                Row(
                    modifier = Modifier
                        .height(geometry.linesHeight)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        modifier = Modifier
                            .width(geometry.brandWidth),
                        contentScale = ContentScale.FillWidth,
                        painter = painterResource(
                            R.drawable.ic_code_logo_offwhite_small
                        ),
                        colorFilter = ColorFilter.tint(CashBillDefaults.DecorColor),
                        contentDescription = "",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 2017
                    Lines(count = 17, spacing = geometry.lineSpacing)
                }
            }

            // Scan code
            BillCode(
                modifier = Modifier.align(Alignment.Center),
                geometry = geometry,
                data = payloadData
            )
        }
    }
}

@Composable
private fun SecurityStrip(
    modifier: Modifier = Modifier,
    geometry: CashBillGeometry,
) {
    Row(
        modifier = modifier
            .size(geometry.securityStripSize)
            .offset(geometry.securityStripPosition.x, geometry.securityStripPosition.y)
            .punchRectangle(CashBillDefaults.BillColor.copy(CashBillDefaults.CodeBackgroundOpacity)),
    ) {
        for (i in 0 until CashBillDefaults.SecurityStripCount) {
            Image(
                modifier = Modifier
                    .weight(1f)
                    .alpha(0.5f),
                contentScale = ContentScale.FillBounds,
                painter = painterResource(id = R.drawable.ic_bill_security_strip),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun Lines(
    modifier: Modifier = Modifier,
    count: Int,
    spacing: Dp,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 0 until count) {
            Box(
                modifier = Modifier
                    .rotate(-18f)
                    .background(CashBillDefaults.DecorColor)
                    .fillMaxHeight()
                    .width(1.dp)
            )
        }
    }
}

@Composable
private fun BillDecorImage(
    modifier: Modifier = Modifier,
    image: ImageBitmap?,
    alpha: Float = 1f,
    size: DpSize = DpSize.Unspecified,
    topLeft: Offset = Offset.Zero,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    Canvas(
        modifier = modifier,
    ) {
        // Hexagons
        image?.let {
            drawImage(
                image = it,
                dstSize = IntSize(
                    width = if (size.isSpecified && size.width.isSpecified) size.width.roundToPx() else this.size.width.toInt(),
                    height = if (size.isSpecified && size.height.isSpecified) size.height.roundToPx() else this.size.height.toInt(),
                ),
                alpha = alpha,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                blendMode = blendMode,
            )
        }
    }
}

@Composable
private fun BillCode(modifier: Modifier = Modifier, geometry: CashBillGeometry, data: List<Byte>) {
    Box(
        modifier = modifier
            .punchCircle(CashBillDefaults.BillColor.copy(0.9f)),
        contentAlignment = Alignment.Center
    ) {
        if (data.isNotEmpty()) {
            ScannableCode(
                modifier = Modifier
                    .size(geometry.codeSize),
                data = data
            )
        }
    }
}

@Composable
private fun loadBillAsset(drawableRes: Int): ImageBitmap {
    val option = BitmapFactory.Options()
    option.inPreferredConfig = Bitmap.Config.ARGB_8888
   return BitmapFactory.decodeResource(
        LocalContext.current.resources,
        drawableRes,
        option
    ).asImageBitmap()
}
