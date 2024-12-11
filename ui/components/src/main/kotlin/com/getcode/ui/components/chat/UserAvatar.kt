package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
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
                loadedSize = with(it.result.image) { Size(width.toFloat(), height.toFloat()) }
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