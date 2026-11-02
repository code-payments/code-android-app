package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

/**
 * Hides a redacted message's text: the server's placeholder, standing in for a body the viewer may
 * not read. Blurred where the platform can blur, and kept out of the accessibility tree either way,
 * so the placeholder is never read out, selected or copied. Pair it with [redactedTextColor].
 */
@Composable
internal fun Modifier.redactedText(): Modifier = if (canBlurRedactedText) {
    blur(REDACTED_TEXT_BLUR, BlurredEdgeTreatment.Unbounded).clearAndSetSemantics {}
} else {
    // `blur` draws nothing below Android 12; cover the text with a solid block instead.
    background(CodeTheme.colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        .clearAndSetSemantics {}
}

/** What colour redacted text draws in: [color] where it blurs, nothing where a block covers it. */
internal fun redactedTextColor(color: Color): Color =
    if (canBlurRedactedText) color else Color.Transparent

internal val REDACTED_TEXT_BLUR = 6.dp

/** Whether `Modifier.blur` does anything here: it needs RenderEffect, which arrived in Android 12. */
internal expect val canBlurRedactedText: Boolean
