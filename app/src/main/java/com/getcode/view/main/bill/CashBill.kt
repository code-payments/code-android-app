package com.getcode.view.main.bill

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.solana.keys.Key32.Companion.kinMint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.util.formattedRaw
import com.getcode.ui.utils.nonScaledSp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

object CashBillDefaults {
    val AspectRatio = 0.555f
    val BillColor: Color = Color(red = 44, green = 42, blue = 65)


    val CodeBackgroundOpacity = 0.7f

    val SecurityStripCount = 3
}

object CashBillAssets {

    var hexagons: ImageBitmap? = null
        private set

    var globe: ImageBitmap? = null
        private set

    var grid: ImageBitmap? = null
        private set

    var waves: ImageBitmap? = null
        private set

    fun load(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            hexagons = getBitmapFromImage(
                context = context,
                drawable = R.drawable.ic_bill_hexagons,
                ratio = 0.15f,
                alpha = 0.6f
            ).asImageBitmap()

            globe = getBitmapFromImage(
                context = context,
                drawable = R.drawable.ic_bill_globe,
                ratio = 0.18f,
            ).asImageBitmap()

            grid = getBitmapFromImage(
                context = context,
                ratio = 0.3f,
                drawable = R.drawable.ic_bill_grid,
                alpha = 0.5f
            ).asImageBitmap()

            waves = getBitmapFromImage(
                context = context,
                ratio = 0.3f,
                drawable = R.drawable.ic_bill_waves,
            ).asImageBitmap()
        }
    }
}

private class CashBillGeometry(width: Dp, height: Dp) {
    var size by mutableStateOf(DpSize.Zero)
        private set

    val brandWidth: Dp
        get() = ceil(size.width.value * 0.18f).dp
    val codeSize: Dp
        get() = size.width * 0.6f

    val globePosition: Offset
        get() = Offset(
            x = -(size.width.value * 1.25f),
            y = size.height.value * 1.2f
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

    init {
        size = DpSize(width, height)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashBill(
    modifier: Modifier = Modifier,
    payloadData: List<Byte>,
    amount: KinAmount,
) {
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
        ) {
            val geometry = remember(maxWidth, maxHeight) {
                CashBillGeometry(maxWidth, maxHeight)
            }

            // Security strip
            SecurityStrip(geometry = geometry)

            Canvas(modifier = Modifier
                .matchParentSize()
                .clipToBounds()) {
                // Hexagons
                CashBillAssets.hexagons?.let {
                    drawImage(
                        image = it,
                        blendMode = BlendMode.Multiply,
                    )
                }

                // Grid pattern
                CashBillAssets.grid?.let {
                    drawImage(image = it,)
                }

                // Globe
                CashBillAssets.globe?.let {
                    drawImage(image = it, topLeft = geometry.globePosition)
                }

                // Waves
                CashBillAssets.waves?.let {
                    drawImage(image = it)
                }
            }


            // Bill Value Top Left
            BillAmount(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(start = geometry.valuePadding),
                text = amount.formattedRaw()
            )

            // Bill Value Bottom Right
            BillAmount(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(end = geometry.valuePadding),
                text = amount.formattedRaw()
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
                        text = kinMint.base58(),
                        fontSize = 8.nonScaledSp,
                        color = CodeTheme.colors.onBackground.copy(alpha = 0.60f)
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
                        painter = painterResource(
                            R.drawable.ic_code_logo_offwhite_small
                        ),
                        colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground.copy(alpha = 0.60f)),
                        contentDescription = "",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 2017
                    Lines(count = 17, spacing = geometry.lineSpacing)
                }
            }

            // Scan code
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .drawWithContent {
                        drawCircle(
                            CashBillDefaults.BillColor.copy(CashBillDefaults.CodeBackgroundOpacity),
                            blendMode = BlendMode.Src
                        )

                        drawContent()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (payloadData.isNotEmpty()) {
                    ScannableCode(
                        modifier = Modifier
                            .size(geometry.codeSize),
                        data = payloadData
                    )
                }
            }
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
            .drawWithContent {
                drawRect(
                    CashBillDefaults.BillColor,
                    blendMode = BlendMode.Clear
                )
                drawRect(
                    Color.Black.copy(alpha = 0.4f),
                    blendMode = BlendMode.SrcOut
                )

                drawContent()
            },
    ) {
        for (i in 0 until CashBillDefaults.SecurityStripCount) {
            Image(
                modifier = Modifier.weight(1f),
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
                    .background(White50)
                    .fillMaxHeight()
                    .width(1.dp)
            )
        }
    }
}

private fun getBitmapFromImage(
    context: Context,
    drawable: Int,
    ratio: Float,
    alpha: Float = 1f
): Bitmap {

    // on below line we are getting drawable
    val db = ContextCompat.getDrawable(context, drawable)

    // in below line we are creating our bitmap and initializing it.
    val bit = Bitmap.createBitmap(
        db!!.intrinsicWidth, db.intrinsicHeight, Bitmap.Config.ARGB_8888
    )

    var width = bit.width
    var height = bit.height
    val bitmapRatio = width.toFloat() / height.toFloat()

    if (bitmapRatio > 1) {
        width = bit.width
        height = (width / bitmapRatio).toInt()
    } else {
        height = bit.height
        width = (height * bitmapRatio).toInt()
    }

    val compressed: Bitmap = Bitmap.createScaledBitmap(
        bit, (width * ratio).roundToInt(), (height * ratio).roundToInt(), false
    )


    // on below line we are
    // creating a variable for canvas.
    val canvas = android.graphics.Canvas(compressed)

    // on below line we are setting bounds for our bitmap.
    db.setBounds(0, 0, canvas.width, canvas.height)

    db.alpha = (255 * alpha).roundToInt()

    // on below line we are simply
    // calling draw to draw our canvas.
    db.draw(canvas)

    // on below line we are
    // returning our bitmap.
    return compressed
}