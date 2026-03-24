package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flipcash.core.R

@Composable
fun DeviceFrame(
    modifier: Modifier = Modifier,
    clipToFrame: Boolean = true,
    contentAlignment: Alignment = Alignment.TopCenter,
    contents: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Image(
            painter = painterResource(id = R.drawable.ic_device_frame),
            contentDescription = "",
        )
        // Inner viewport clipped to frame edge
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 10.dp)
                .then(
                    if (clipToFrame) Modifier.clip(RoundedCornerShape(42.dp)) else Modifier
                ),
            contentAlignment = contentAlignment,
        ) {
            contents()
        }
    }
}