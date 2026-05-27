package com.flipcash.app.permissions.internal.contacts.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.ScreenFrame
import com.getcode.theme.CodeTheme
import com.getcode.theme.White20
import kotlinx.coroutines.delay

private data class ContactRow(val nameFraction: Float, val subtitleFraction: Float)

private val contactRows = listOf(
    ContactRow(0.60f, 0.50f),
    ContactRow(0.45f, 0.40f),
    ContactRow(0.70f, 0.55f),
    ContactRow(0.40f, 0.35f),
    ContactRow(0.55f, 0.45f),
    ContactRow(0.35f, 0.30f),
)

@Composable
private fun ContactRowPlaceholder(row: ContactRow) {
    val barShape = RoundedCornerShape(CodeTheme.dimens.grid.x1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.grid.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x7)
                .background(White20, CircleShape),
        )
        Spacer(modifier = Modifier.width(CodeTheme.dimens.grid.x2))
        Column(
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.nameFraction)
                    .height(CodeTheme.dimens.grid.x2)
                    .background(White20, barShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.subtitleFraction)
                    .height(CodeTheme.dimens.grid.x2)
                    .background(White20, barShape),
            )
        }
    }
}

private val bottomFadeBrush = Brush.verticalGradient(
    0f to Color.Black,
    0.4f to Color.Black,
    0.85f to Color.Transparent,
)

@Composable
internal fun AnimatedContactListPreview(animate: Boolean = true) {
    val alpha = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animate) {
            delay(300)
            alpha.animateTo(1f, tween(600))
        }
    }

    Box(
        modifier = Modifier
            .height(340.dp)
            .graphicsLayer {
                this.alpha = alpha.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = bottomFadeBrush, blendMode = BlendMode.DstIn)
            },
    ) {
        ScreenFrame(
            modifier = Modifier.wrapContentSize(unbounded = true, align = Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                contactRows.forEach { row ->
                    ContactRowPlaceholder(row = row)
                }
            }
        }
    }
}
