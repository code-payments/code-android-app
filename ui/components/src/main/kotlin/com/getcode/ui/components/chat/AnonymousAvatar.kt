package com.getcode.ui.components.chat

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.DesignSystem
import com.getcode.ui.components.R
import com.getcode.ui.utils.IDPreviewParameterProvider
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.ui.utils.generateEightBitAvatar
import com.getcode.utils.bytes
import com.getcode.utils.decodeBase58
import java.util.UUID

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
                        val avatar = if (size.isEmpty().not()) {
                            generateEightBitAvatar(data, size)
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
                        val colors = generateComplementaryColorPalette(data)
                        val gradient = if (colors != null) {
                            Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.14f to colors.first,
                                    0.38f to colors.second,
                                    0.67f to colors.third,
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
        val provider = IDPreviewParameterProvider(39)
        LazyVerticalGrid(columns = GridCells.Fixed(8)) {
            item {
                Box(modifier = Modifier.padding(8.dp)) {
                    val id = "4T7DtS9CEZKVJrBgujQLcjBYnMqZSzZV6CqJewME6zVp".decodeBase58().toList()
                    AnonymousAvatar(
                        modifier = Modifier.fillMaxSize(),
                        data = id,
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
