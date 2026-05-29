package com.flipcash.app.bills

import android.R.attr.fillType
import android.annotation.SuppressLint
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.zIndex
import com.flipcash.app.core.money.formatted
import com.flipcash.shared.bills.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.BlendMode as PlaygroundBlendMode
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.blendMode
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.patternBlend
import com.getcode.ui.core.punchCircle
import com.getcode.ui.core.punchRectangle
import com.getcode.ui.utils.Geometry
import com.getcode.ui.utils.deriveTargetColor
import com.getcode.ui.utils.hexToColor
import com.getcode.ui.utils.hls
import com.getcode.ui.utils.nonScaledSp
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class Punch {
    SecurityStrip, Code
}

@Suppress("ConstPropertyName")
private object CashBillDefaults {
    const val AspectRatio = 0.555f

    fun billColor(
        token: Token,
        alpha: Float = 1f,
        startY: Float = 0f,
        endY: Float = Float.POSITIVE_INFINITY,
    ): Brush {
        val billCustomizations = token.billCustomizations ?: return Brush.verticalGradient(
            listOf(Color(0xFF06450F), Color(0xFF06450F))
        )

        when (val background = billCustomizations.background) {
            is BillBackground.Gradient -> {
                val colors = background.colors.map { hexToColor(it) }
                val lastIndex = (colors.size - 1).coerceAtLeast(1)
                val colorStops = colors.mapIndexed { index, color ->
                    (index.toFloat() / lastIndex) to color.copy(alpha)
                }.toTypedArray()
                return Brush.verticalGradient(
                    colorStops = colorStops,
                    startY = startY,
                    endY = endY
                )
            }

            is BillBackground.Solid -> {
                val color = hexToColor(background.colorHex)
                return Brush.verticalGradient(
                    listOf(color, color)
                )
            }
        }
    }

    const val CodeBackgroundOpacity = 0.8f

    @Composable
    fun punchBrushIn(punch: Punch, token: Token): Brush {
        val billCustomizations = token.billCustomizations
        if (billCustomizations?.background == null) {
            val color = Black.copy(0.15f)
                .compositeOver(CodeTheme.colors.cashBillColor.copy(alpha = CodeBackgroundOpacity))
            return Brush.verticalGradient(
                colors = listOf(color, color)
            )
        }

        return when (val bg = billCustomizations.background) {
            is BillBackground.Gradient -> {
                when (punch) {
                    Punch.SecurityStrip -> {
                        val color = Black.copy(0.15f)
                            .compositeOver(
                                hexToColor(bg.colors.first())
                                    .copy(alpha = CodeBackgroundOpacity)
                            )

                        Brush.verticalGradient(
                            colors = listOf(color, color)
                        )
                    }

                    Punch.Code -> {
                        punchBrushFrom(bg.colors.map { hexToColor(it) })
                    }
                }
            }

            is BillBackground.Solid -> {
                when (punch) {
                    Punch.SecurityStrip -> {
                        val color = Black.copy(0.15f)
                            .compositeOver(hexToColor(bg.colorHex).copy(alpha = 0.62f))

                        Brush.verticalGradient(
                            colors = listOf(color, color)
                        )
                    }

                    Punch.Code -> {
                        punchBrushFrom( listOf(hexToColor(bg.colorHex)))
                    }
                }
            }
        }
    }

    const val SecurityStripCount = 3

    val DecorColor: Color
        @Composable get() = CodeTheme.colors.cashBillDecorColor
}

private class CashBillGeometry(width: Dp, height: Dp) : Geometry(width, height) {

    val brandWidth: Dp
        get() = ceil(size.width.value * 0.18f).dp

    override val codeSize: Dp
        get() = size.width * 0.65f

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
        get() = ceil(size.width.value * 0.014f).dp

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

    private val isCompressed: Boolean
        get() = size.width < 300.dp

    private val isMini: Boolean
        get() = size.width < 222.dp

    val mintFontSize: TextUnit
        @Composable get() = if (isCompressed) {
            if (isMini) {
                5.5.nonScaledSp
            } else {
                7.nonScaledSp
            }
        } else {
            8.nonScaledSp
        }

    val amountTextStyle: TextStyle
        @Composable get() = if (isCompressed) {
            CodeTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.W600,
                fontSize = if (isMini) 20.nonScaledSp else 35.nonScaledSp
            )
        } else {
            CodeTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.W600,
                fontSize = 40.nonScaledSp
            )
        }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CashBill(
    modifier: Modifier = Modifier,
    payloadData: List<Byte>,
    token: Token,
    amount: LocalFiat,
) {
    val exchange = LocalExchange.current
    val customTexture = token.billCustomizations?.texture
    val hasCustomTexture = customTexture != null

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(CashBillDefaults.AspectRatio, matchHeightConstraintsFirst = true)
                .fillMaxHeight()
                .fillMaxWidth(0.95f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clipToBounds()
                .testTag("cash_bill")
        ) {
            val geometry = remember(maxWidth, maxHeight) {
                CashBillGeometry(maxWidth, maxHeight)
            }

            val punchShape = remember(geometry) {
                BillPunchShape(codeSize = geometry.codeSize)
            }

            // Background with punch hole
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CashBillDefaults.billColor(token), punchShape)
            )

            // Texture layers — clipped with even-odd punch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(punchShape)
            ) {
                if (hasCustomTexture) {
                    val context = LocalContext.current
                    val resources = LocalResources.current
                    val pattern = runCatching {
                        resources.getIdentifier("bill_texture_${customTexture.index}", "drawable", context.packageName)
                    }.getOrNull()

                    if (pattern != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .patternBlend(
                                    pattern = ImageBitmap.imageResource(resources, pattern),
                                    blendMode = when (customTexture.blendMode) {
                                        PlaygroundBlendMode.Normal -> null
                                        PlaygroundBlendMode.Lighten -> BlendMode.Lighten
                                        PlaygroundBlendMode.Screen -> BlendMode.Screen
                                        PlaygroundBlendMode.ColorDodge -> BlendMode.ColorDodge
                                        PlaygroundBlendMode.PlusLighter -> BlendMode.Softlight
                                    },
                                    strength = customTexture.strength,
                                ),
                        )
                    }
                } else {
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
                        )
                    )
                }

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

                if (!hasCustomTexture) {
                    // Waves
                    Image(
                        modifier = Modifier
                            .requiredWidth(geometry.globeWidth)
                            .fillMaxHeight()
                            .offset { IntOffset(x = geometry.wavesPosition.x.toInt(), y = 0) },
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        painter = painterResource(R.drawable.ic_bill_waves),
                    )
                }
            }

            // Security strip
            SecurityStrip(geometry = geometry, token = token)

            // Bill Value Top Left
            BillAmount(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(start = geometry.valuePadding),
                text = amount.formatted(),
                flag = exchange.getFlagByCurrency(amount.rate.currency.name),
                textStyle = geometry.amountTextStyle,
            )

            // Bill Value Bottom Right
            BillAmount(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = geometry.topStripHeight + geometry.securityStripSize.height * 0.5f)
                    .padding(end = geometry.valuePadding),
                text = amount.formatted(),
                flag = exchange.getFlagByCurrency(amount.rate.currency.name),
                textStyle = geometry.amountTextStyle,
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
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.grid.x1),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // October
                    Lines(count = 10, spacing = geometry.lineSpacing)
                    // Oct 31
                    Lines(count = 31, spacing = geometry.lineSpacing)
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.padding(bottom = geometry.mintPadding)
                ) {
                    // Mint
                    Text(
                        text = token.address.base58(),
                        fontSize = geometry.mintFontSize,
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
                            R.drawable.ic_flipcash_logo_offwhite_small
                        ),
                        colorFilter = ColorFilter.tint(CashBillDefaults.DecorColor),
                        contentDescription = "",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 2000
                    Lines(
                        count = 2,
                        spacing = geometry.lineSpacing
                    )

                    Spacer(modifier = Modifier.weight(0.5f))

                    // 2008
                    Lines(
                        modifier = Modifier
                            .padding(end = CodeTheme.dimens.grid.x2),
                        count = 8,
                        spacing = geometry.lineSpacing
                    )
                }
            }

            // Scan code
            BillCode(
                modifier = Modifier.align(Alignment.Center),
                geometry = geometry,
                data = payloadData,
                token = token
            )
        }
    }
}

@Composable
private fun SecurityStrip(
    modifier: Modifier = Modifier,
    geometry: CashBillGeometry,
    token: Token,
) {
    Row(
        modifier = modifier
            .size(geometry.securityStripSize)
            .offset(geometry.securityStripPosition.x, geometry.securityStripPosition.y)
            .punchRectangle(CashBillDefaults.punchBrushIn(punch = Punch.SecurityStrip, token)),
    ) {
        repeat(CashBillDefaults.SecurityStripCount) {
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
        repeat(count) {
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
private fun BillCode(
    modifier: Modifier = Modifier,
    geometry: CashBillGeometry,
    data: List<Byte>,
    token: Token
) {
    Box(
        modifier = modifier
            .punchCircle(
                brush = CashBillDefaults.punchBrushIn(punch = Punch.Code, token),
            ),
        contentAlignment = Alignment.Center
    ) {
        if (data.isNotEmpty()) {
            ScannableCode(
                modifier = Modifier
                    .size(geometry.codeSize),
                data = data,
                token = token
            )
        }
    }
}

@Composable
private fun loadBillAsset(drawableRes: Int): ImageBitmap {
    val option = BitmapFactory.Options()
    option.inPreferredConfig = Bitmap.Config.ARGB_8888
    return BitmapFactory.decodeResource(
        LocalResources.current,
        drawableRes,
        option
    ).asImageBitmap()
}

private class BillPunchShape(
    private val codeSize: Dp,
) : Shape {
    override fun createOutline(
        size: Size, layoutDirection: LayoutDirection, density: Density
    ): Outline {
        val radius = with(density) { (codeSize / 2f).toPx() }
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addOval(
                Rect(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius
                )
            )
        }
        return Outline.Generic(path)
    }
}

fun punchColorsFrom(billColors: List<Color>): List<Color> {
    return billColors.map { color ->
        lerp(color, Color.Black, 0.30f)
    }
}

fun punchBrushFrom(billColors: List<Color>): Brush {
    val punched = punchColorsFrom(billColors)
    return when {
        punched.size == 1 -> SolidColor(punched[0])
        else -> Brush.verticalGradient(punched.map { it.copy(0.83f) })
    }
}