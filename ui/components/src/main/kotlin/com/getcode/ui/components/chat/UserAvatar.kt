package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import java.util.UUID

@OptIn(ExperimentalCoilApi::class)
@Composable
fun UserAvatar(
    data: Any?,
    modifier: Modifier = Modifier,
    anonymousRender: AnonymousRender = AnonymousRender.Gradient,
    showEmptyWhenUnknownData: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = { },
) {
    Box(modifier = modifier) {
        var imgLoading by rememberSaveable(data) { mutableStateOf(true) }
        var loadedSize by remember(data) { mutableStateOf(Size.Zero) }
        var isError by rememberSaveable(data) { mutableStateOf(false) }

        val context = LocalContext.current
        val request = remember(data) {
            ImageRequest.Builder(context)
                .crossfade(true)
                .data(data)
                .allowHardware(true)
                .build()
        }
        AsyncImage(
            modifier = Modifier.matchParentSize(),
            model = request,
            contentDescription = null,
            onError = {
                isError = true
            },
            onLoading = {
                imgLoading = true
            },
            onSuccess = {
                loadedSize = with(it.result.image) { Size(width.toFloat(), height.toFloat()) }
                imgLoading = false
            }
        )

        if (imgLoading) {
            Box(modifier = Modifier.matchParentSize().background(CodeTheme.colors.brandDark))
        }

        if (isError) {
            if (data is List<*> && data.isNotEmpty()) {
                if (data.first() is Byte) {
                    AnonymousAvatar(
                        modifier = Modifier.matchParentSize(),
                        data = data as List<Byte>,
                        type = anonymousRender,
                        overlay = overlay,
                    )
                }
            } else if (data is UUID) {
                AnonymousAvatar(
                    modifier = Modifier.matchParentSize(),
                    memberId = data,
                    type = anonymousRender,
                    icon = overlay,
                )
            } else {
                if (!showEmptyWhenUnknownData) {
                    Image(
                        modifier = Modifier.matchParentSize(),
                        painter = painterResource(id = R.drawable.ic_placeholder_user),
                        contentDescription = null
                    )
                } else {
                    Spacer(Modifier.matchParentSize())
                }
            }
        }
    }
}