package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun HostableUserAvatar(
    imageData: Any?,
    modifier: Modifier = Modifier,
    size: Dp = CodeTheme.dimens.staticGrid.x8,
    crownOffset: Density.() -> IntOffset = {
        IntOffset(
            x = -(5.dp).roundToPx(),
            y = -(5.dp).roundToPx()
        )
    },
    isHost: Boolean = false,
) {
    Box(
        modifier = modifier,
    ) {
        UserAvatar(
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            data = imageData,
            overlay = {
                Image(
                    modifier = Modifier.padding(5.dp),
                    imageVector = Icons.Default.Person,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            }
        )

        if (isHost) {
            Image(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { crownOffset() }
                    .size(CodeTheme.dimens.staticGrid.x4)
                    .background(color = Color(0xFFE9C432), shape = CircleShape)
                    .padding(4.dp),
                painter = painterResource(R.drawable.ic_crown),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.brand)
            )
        }
    }
}