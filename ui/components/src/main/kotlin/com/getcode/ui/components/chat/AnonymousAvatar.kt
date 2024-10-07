package com.getcode.ui.components.chat

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.network.repository.sha512
import com.getcode.theme.DesignSystem
import com.getcode.ui.utils.UUIDPreviewParameterProvider
import com.getcode.ui.utils.deriveTargetColor
import com.getcode.ui.utils.toAGColor
import com.getcode.utils.bytes
import java.util.UUID
import kotlin.experimental.and
import kotlin.math.roundToInt

enum class AnonymousRender {
    EightBit, Gradient
}

@Composable
fun AnonymousAvatar(
    memberId: UUID,
    modifier: Modifier = Modifier,
    type: AnonymousRender = AnonymousRender.EightBit
) {
    AnonymousAvatar(modifier = modifier, data = memberId.bytes, type = type)
}

@Composable
fun AnonymousAvatar(
    data: List<Byte>,
    modifier: Modifier = Modifier,
    type: AnonymousRender = AnonymousRender.EightBit
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
                        val avatar = if (size.isEmpty().not()) {
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
                            data.toByteArray().sha512()
                        }.getOrNull()

                        val gradient = if (hash != null) {
                            val sourceColor = rgbFromHash(hash.copyOfRange(0, 3))
                            val derivedColor = deriveTargetColor(sourceColor, 0.3f, 0.5f)

                            Brush.verticalGradient(
                                colors = listOf(sourceColor, derivedColor)
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
                        }
                    }
                }
            }
    )
}

@Preview
@Composable
fun Preview_Avatars() {
    DesignSystem {
        val provider = UUIDPreviewParameterProvider(40)
        LazyVerticalGrid(columns = GridCells.Fixed(8)) {
            items(provider.values.toList()) {
                Box(modifier = Modifier.padding(8.dp)) {
                    AnonymousAvatar(modifier = Modifier.fillMaxSize(), memberId = it)
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