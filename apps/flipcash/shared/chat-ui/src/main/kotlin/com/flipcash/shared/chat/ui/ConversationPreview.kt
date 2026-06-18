package com.flipcash.shared.chat.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.ScreenFrame
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeTypography
import com.getcode.theme.codeTypography
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val bottomFadeBrush = Brush.verticalGradient(
    0f to Color.Black,
    0.8f to Color.Black,
    0.95f to Color.Transparent,
)

private val messages = listOf(
    ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Cash(
            intentId = RandomId,
            amount = 60.toFiat(),
            mint = Mint.usdf,
        ),
        isFromSelf = true,
        timestamp = Clock.System.now().minus(2.minutes),
    ),
    ChatListItem.ContentBubble(
        messageId = 2,
        contentIndex = 1,
        content = MessageContent.Text(
            text = "Thanks for dinner!"
        ),
        isFromSelf = true,
        timestamp = Clock.System.now().minus(2.minutes),
    ),
    ChatListItem.ContentBubble(
        messageId = 3,
        contentIndex = 0,
        content = MessageContent.Text(
            text = "That's very kind of you. I had a great time last night"
        ),
        isFromSelf = false,
        timestamp = Clock.System.now(),
    ),
)

@Composable
fun AnimatedConversationPreview(animate: Boolean = true) {
    val alpha = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animate) {
            delay(300.milliseconds)
            alpha.animateTo(1f, tween(600))
        }
    }

    // cash bubble prices are rendered at displaySmall sizes in this preview render
    val previewTypography = codeTypography.copy(
        displayMedium = codeTypography.displaySmall,
    )

    CompositionLocalProvider(LocalCodeTypography provides previewTypography) {
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
                        .padding(top = CodeTheme.dimens.inset)
                        .padding(horizontal = CodeTheme.dimens.grid.x2),
                ) {
                    val reversed = messages.asReversed()
                    messages.forEachIndexed { index, item ->
                        if (index > 0) {
                            val sameSender = item.isFromSelf == messages[index - 1].isFromSelf
                            Spacer(
                                Modifier.height(
                                    if (sameSender) CodeTheme.dimens.grid.x2
                                    else CodeTheme.dimens.grid.x3
                                )
                            )
                        }
                        val position = bubblePositionOf(
                            index = messages.lastIndex - index,
                            item = item,
                            messages = reversed,
                            config = SeparatorConfig.TimeGap()
                        )
                        val shape = bubbleShape(position, item.isFromSelf)
                        CompositionLocalProvider(LocalInspectionMode provides true) {
                            ContentBubble(
                                item = item,
                                modifier = Modifier.bubbleShadow(shape),
                                position = position,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.bubbleShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.2f),
    blurRadius: Dp = 12.dp,
    offsetY: Dp = 4.dp,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter =
            BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        translate(top = offsetY.toPx()) {
            canvas.drawOutline(outline, paint)
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun PreviewConversationPreview() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        Box(modifier = Modifier) {
            AnimatedConversationPreview(false)
        }
    }
}
