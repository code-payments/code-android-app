package com.flipcash.shared.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
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

private val paymentBubbles = listOf(
    ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Cash(
            intentId = RandomId,
            amount = 25.toFiat(),
            mint = Mint.usdf,
        ),
        isFromSelf = false,
        timestamp = Clock.System.now().minus(5.minutes),
    ),
    ChatListItem.ContentBubble(
        messageId = 2,
        contentIndex = 0,
        content = MessageContent.Cash(
            intentId = RandomId,
            amount = 60.toFiat(),
            mint = Mint.usdf,
        ),
        isFromSelf = true,
        timestamp = Clock.System.now().minus(2.minutes),
    ),
)

@Composable
fun AnimatedConversationPaymentsPreview(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val alpha = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animate) {
            delay(300.milliseconds)
            alpha.animateTo(1f, tween(600))
        }
    }

    val previewTypography = codeTypography.copy(
        displayMedium = codeTypography.displaySmall,
    )

    CompositionLocalProvider(LocalCodeTypography provides previewTypography) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    this.clip = false
                },
            contentAlignment = Alignment.Center,
        ) {
            val received = paymentBubbles[0]
            val sent = paymentBubbles[1]
            val shape = bubbleShape(BubblePosition.Solo, false)

            CompositionLocalProvider(LocalInspectionMode provides true) {
                val opaqueModifier = Modifier
                    .bubbleShadow(shape)
                    .background(CodeTheme.colors.background, shape)

                // Sent bubble — upper right, behind, tilted clockwise
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .graphicsLayer {
                            translationX = size.width * 0.12f
                            translationY = -size.height * 0.10f
                            rotationZ = 4f
                        },
                ) {
                    ContentBubble(
                        item = sent,
                        modifier = opaqueModifier,
                        position = BubblePosition.Solo,
                    )
                }

                // Received bubble — lower left, in front, tilted counter-clockwise
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .graphicsLayer {
                            translationX = -size.width * 0.12f
                            translationY = size.height * 0.10f
                            rotationZ = -6f
                        },
                ) {
                    ContentBubble(
                        item = received,
                        modifier = opaqueModifier,
                        position = BubblePosition.Solo,
                    )
                }
            }
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun PreviewConversationPaymentsPreview() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        Box(modifier = Modifier.wrapContentSize()) {
            AnimatedConversationPaymentsPreview(animate = false)
        }
    }
}
