package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.text.markup.Markup

@Composable
internal fun MarkupTouchHandler(
    options: MessageNodeOptions,
    content: @Composable (onTap: (contentPadding: PaddingValues, touchOffset: Offset) -> Unit) -> Unit,
) {
    MarkupTouchHandler(onMarkupClicked = options.onMarkupClicked, content = content)
}

@Composable
fun MarkupTouchHandler(
    onMarkupClicked: ((Markup.Interactive) -> Unit)? = null,
    content: @Composable (onTap: (contentPadding: PaddingValues, touchOffset: Offset) -> Unit) -> Unit,
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val handleTouchedContent = { offset: Int ->
        layoutResult?.layoutInput?.text?.getStringAnnotations(
            tag = Markup.RoomNumber.TAG,
            start = offset,
            end = offset
        )?.firstOrNull()?.let { annotation ->
            onMarkupClicked?.invoke(Markup.RoomNumber(annotation.item.toLong()))
        }

        layoutResult?.layoutInput?.text?.getStringAnnotations(
            tag = Markup.Url.TAG,
            start = offset,
            end = offset
        )?.firstOrNull()?.let { annotation ->
            onMarkupClicked?.invoke(Markup.Url(annotation.item))
        }

        layoutResult?.layoutInput?.text?.getStringAnnotations(
            tag = Markup.Phone.TAG,
            start = offset,
            end = offset
        )?.firstOrNull()?.let { annotation ->
            onMarkupClicked?.invoke(Markup.Phone(annotation.item))
        }
    }

    val density = LocalDensity.current
    val ldr = LocalLayoutDirection.current
    CompositionLocalProvider(LocalTextLayoutResult provides { layoutResult = it }) {
        content { padding, touchOffset ->
            layoutResult?.let { layoutResult ->
                val adjustedOffset = Offset(
                    x = touchOffset.x - with(density) { padding.calculateStartPadding(ldr).toPx() },
                    y = touchOffset.y - with(density) { padding.calculateTopPadding().toPx() })
                val position = layoutResult.getOffsetForPosition(adjustedOffset)
                handleTouchedContent(position)
            }
        }
    }
}

val LocalTextLayoutResult: ProvidableCompositionLocal<(TextLayoutResult) -> Unit> =
    staticCompositionLocalOf { { } }