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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.solana.keys.Key32.Companion.kinMint
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White50
import com.getcode.ui.utils.debugBounds
import com.getcode.util.formattedRaw
import com.getcode.ui.utils.nonScaledSp
import com.getcode.ui.utils.punchCircle
import com.getcode.ui.utils.punchRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

@Suppress("ConstPropertyName")
object CashBillDefaults {
    const val AspectRatio = 0.555f
    val BillColor: Color = Color(red = 44, green = 42, blue = 65)

    const val CodeBackgroundOpacity = 0.65f

    const val SecurityStripCount = 3
}

object CashBillAssets {

    var globe: ImageBitmap? = null
        private set

    var grid: ImageBitmap? = null
        private set

    fun load(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {

            globe = getBitmapFromImage(
                context = context,
                drawable = R.drawable.ic_bill_globe,
                ratio = 0.18f,
            ).asImageBitmap()

            grid = getBitmapFromImage(
                context = context,
                ratio = 0.3f,
                drawable = R.drawable.ic_bill_grid,
            ).asImageBitmap()
        }
    }

    private fun getBitmapFromImage(
        context: Context,
        drawable: Int,
        ratio: Float = 1f,
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

            // Hexagons
            BillImage(
                modifier = Modifier
                    .fillMaxSize(),
                image = ImageBitmap.imageResource(R.drawable.ic_bill_hexagons),
                blendMode = BlendMode.Multiply,
                alpha = 0.6f,
                fill = true,
            )

            // Waves
            BillImage(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds(),
                image = ImageBitmap.imageResource(R.drawable.ic_bill_waves),
                fill = true,
                topLeft = Offset(
                    x = 0f,
                    y = with(LocalDensity.current) { geometry.securityStripPosition.y.toPx() }),
            )

            // Globe
            BillImage(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds(),
                image = CashBillAssets.globe,
                topLeft = geometry.globePosition
            )

            // Grid pattern
            BillImage(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds(),
                image = CashBillAssets.grid,
                alpha = 0.5f,
                topLeft = Offset(
                    x = 0f,
                    y = with(LocalDensity.current) { geometry.securityStripPosition.y.toPx() }),
            )

            // Security strip
            SecurityStrip(geometry = geometry)


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
                        contentScale = ContentScale.FillWidth,
                        painter = painterResource(
                            R.drawable.ic_code_logo_offwhite_small
                        ),
                        colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
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
                    .punchCircle(CashBillDefaults.BillColor.copy(CashBillDefaults.CodeBackgroundOpacity)),
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
            .punchRectangle(Color.Black.copy(0.6f)),
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

@Composable
private fun BillImage(
    modifier: Modifier = Modifier,
    image: ImageBitmap?,
    alpha: Float = 1f,
    topLeft: Offset = Offset.Zero,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
    fill: Boolean = false,
) {
    Canvas(
        modifier = modifier,
    ) {
        // Hexagons
        image?.let {
            if (fill) {
                drawImage(
                    image = it,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = alpha,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    blendMode = blendMode,
                )
            } else {
                drawImage(
                    image = it,
                    alpha = alpha,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    blendMode = blendMode,
                )
            }
        }
    }
}
