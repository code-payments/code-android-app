package com.getcode.ui.components.chat

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.model.ID
import com.getcode.theme.DesignSystem
import com.getcode.ui.components.R
import com.getcode.ui.utils.IDPreviewParameterProvider
import com.getcode.ui.utils.UUIDPreviewParameterProvider
import com.getcode.ui.utils.toAGColor
import com.getcode.utils.bytes
import com.getcode.utils.sha512
import java.util.UUID
import kotlin.experimental.and
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AnonymousRender {
    EightBit, Gradient
}

@Composable
fun AnonymousAvatar(
    memberId: UUID,
    modifier: Modifier = Modifier,
    type: AnonymousRender = AnonymousRender.Gradient,
    icon: @Composable BoxScope.() -> Unit = { }
) {
    AnonymousAvatar(modifier = modifier, data = memberId.bytes, type = type, overlay = icon)
}

@Composable
fun AnonymousAvatar(
    data: List<Byte>,
    modifier: Modifier = Modifier,
    type: AnonymousRender = AnonymousRender.EightBit,
    overlay: @Composable BoxScope.() -> Unit = { }
) {
    Box(
        modifier = modifier
            .background(Color(0xFFE6F0FA), CircleShape)
            .aspectRatio(1f)
            .clip(CircleShape)
            .fillMaxSize()
            .drawWithCache {
                when (type) {
                    AnonymousRender.EightBit -> {
                        val avatar = if (size
                                .isEmpty()
                                .not()
                        ) {
                            generateAvatar(data, size)
                        } else {
                            null
                        }

                        onDrawWithContent {
                            if (avatar != null) {
                                drawImage(avatar)
                            } else {
                                drawRect(Color.Transparent)
                            }
                        }
                    }

                    AnonymousRender.Gradient -> {
                        val hash = runCatching {
                            data
                                .toByteArray()
                                .sha512()
                        }.getOrNull()

                        val gradient = if (hash != null) {
                            val colors = generateSampledGradientColors(hash.toList())

                            Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.14f to colors[0],
                                    0.38f to colors[1],
                                    0.67f to colors[2],
                                ),
                                start = Offset(Float.POSITIVE_INFINITY, 0f),
                                end = Offset(0f, Float.POSITIVE_INFINITY)
                            )
                        } else {
                            null
                        }

                        onDrawWithContent {
                            if (gradient != null) {
                                drawRect(brush = gradient)
                            } else {
                                drawRect(Color.Transparent)
                            }
                            drawContent()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        overlay()
    }
}

@Preview
@Composable
fun Preview_Avatars() {
    DesignSystem {
        val provider = IDPreviewParameterProvider(40)
        LazyVerticalGrid(columns = GridCells.Fixed(8)) {
            items(provider.values.toList()) {
                Box(modifier = Modifier.padding(8.dp)) {
                    AnonymousAvatar(
                        modifier = Modifier.fillMaxSize(),
                        data = it,
                        type = AnonymousRender.Gradient
                    ) {
                        Image(
                            modifier = Modifier.padding(5.dp),
                            painter = painterResource(R.drawable.ic_chat),
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

private fun generateAvatar(data: List<Byte>, size: Size): ImageBitmap? {
    val hash = runCatching { data.toByteArray().sha512() }.getOrNull() ?: return null
    val foregroundColor = rgbFromHash(hash.copyOfRange(0, 3))

    val bitmap = Bitmap.createBitmap(
        size.width.roundToInt(),
        size.height.roundToInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint()

    val length = 10
    val rCount = length
    val cCount = length / 2
    val cellSize = size.width / length.toFloat()
    val inset = cellSize * 0.6f

    val bounds = RectF(0f, 0f, size.width, size.height)
    val fullPath = Path().apply { addOval(bounds, Path.Direction.CCW) }
    val maskPath = Path().apply { addOval(bounds.apply { inset(inset, inset) }, Path.Direction.CW) }

    val delta = Path().apply {
        op(fullPath, maskPath, Path.Op.DIFFERENCE)
    }

    val paths = mutableListOf<Path>()

    for (r in 0 until rCount) {
        for (c in 0 until cCount) {
            val i = r * cCount + c
            val isEven = (hash[i] and 1) == 0.toByte()
            if (isEven) {
                val leftPath = createPath(r, c, cellSize)
                if (!delta.intersects(leftPath)) {
                    paths.add(leftPath)
                }

                val rightPath = createPath(r, length - c - 1, cellSize)
                if (!delta.intersects(rightPath)) {
                    paths.add(rightPath)
                }
            }
        }
    }

    paint.color = foregroundColor.toAGColor()
    paths.forEach { canvas.drawPath(it, paint) }

    return bitmap.asImageBitmap()
}

private fun createPath(row: Int, col: Int, size: Float): Path {
    return Path().apply {
        addRect(col * size, row * size, (col + 1) * size, (row + 1) * size, Path.Direction.CW)
    }
}

private fun Path.intersects(other: Path): Boolean {
    val result = Path()
    result.op(this, other, Path.Op.INTERSECT)
    return !result.isEmpty
}

private fun rgbFromHash(hash: ByteArray): Color {
    return Color(hash[0].toInt() and 0xFF, hash[1].toInt() and 0xFF, hash[2].toInt() and 0xFF)
}

fun generateSampledGradientColors(identifier: List<Byte>): List<Color> {
    // Sample bytes across the whole identifier to produce varied hue, saturation, and lightness values
    val sampledBytes = identifier.chunked(3).take(3)  // Take 3 chunks for 3 colors

    return sampledBytes.mapIndexed { index, segment ->
        // Calculate a primary hue based on the first byte in the segment
        val primaryHue = (abs(segment[0].toInt()) % 360).toFloat()

        // Generate a complementary hue by shifting 180 degrees
        val hue = if (index % 2 == 0) primaryHue else (primaryHue + 180) % 360

        // Calculate saturation and lightness based on remaining bytes
        val saturation = 0.8f + (abs(segment[1].toInt() % 20) / 100f)  // Keep saturation high
        val lightness = 0.6f + (abs(segment[2].toInt() % 20) / 100f)   // Keep lightness moderate

        hslToColor(hue, saturation, lightness)
    }
}

// Helper function to convert HSL to Compose Color
private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1 - abs(2 * lightness - 1)) * saturation
    val x = c * (1 - abs((hue / 60) % 2 - 1))
    val m = lightness - c / 2

    val (r, g, b) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color((r + m), (g + m), (b + m), 1f)
}

