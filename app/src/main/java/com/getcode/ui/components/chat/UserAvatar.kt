package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import com.getcode.R
import com.getcode.theme.CodeTheme
import java.util.UUID

@OptIn(ExperimentalCoilApi::class)
@Composable
fun UserAvatar(
    data: Any?,
    modifier: Modifier = Modifier,
    anonymousType: AnonymousRender = AnonymousRender.Gradient
) {
    Box(modifier = modifier) {
        var imgLoading by remember(data) { mutableStateOf(true) }
        var loadedSize by remember(data) { mutableStateOf(Size.Zero) }
        var isError by remember(data) { mutableStateOf(false) }

        AsyncImage(
            modifier = Modifier.matchParentSize(),
            model = data,
            contentDescription = null,
            onError = {
                isError = true
            },
            onLoading = {
                imgLoading = true
            },
            onSuccess = {
                loadedSize = with (it.result.image) { Size(width.toFloat(), height.toFloat()) }
                imgLoading = false
            }
        )

        if (imgLoading) {
            Box(modifier = Modifier.matchParentSize())
        }

        if (isError) {
            if (data is List<*> && data.isNotEmpty()) {
                if (data.first() is Byte) {
                    AnonymousAvatar(
                        modifier = Modifier.matchParentSize(),
                        data = data as List<Byte>,
                        type = anonymousType
                    )
                }
            } else if (data is UUID) {
                AnonymousAvatar(
                    modifier = Modifier.matchParentSize(),
                    memberId = data,
                    type = anonymousType
                )
            } else {
                Image(
                    modifier = Modifier.matchParentSize(),
                    painter = painterResource(id = R.drawable.ic_placeholder_user),
                    contentDescription = null
                )
            }
        }
    }
}