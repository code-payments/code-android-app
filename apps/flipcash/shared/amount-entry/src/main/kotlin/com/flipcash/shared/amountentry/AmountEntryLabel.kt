package com.flipcash.shared.amountentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.flipcash.app.core.onramp.ui.AnnotatedButtonLabel

/**
 * Label for the amount-entry confirm action.
 *
 * [text] is the plain-text form used by the slide-to-confirm style and as a fallback,
 * while [annotated] is the composition-built form used by the button style — it may
 * include inline content such as a wallet icon.
 */
sealed interface AmountEntryLabel {
    val text: String

    @Composable
    fun annotated(enabled: Boolean): AnnotatedButtonLabel

    /** Simple text label with no inline content. */
    data class Plain(override val text: String) : AmountEntryLabel {
        @Composable
        override fun annotated(enabled: Boolean): AnnotatedButtonLabel = AnnotatedString(text) to emptyMap()
    }

    /**
     * Label with inline content (e.g. text + wallet icon). The [builder] is invoked in
     * composition since [AnnotatedButtonLabel] needs stringResource/painterResource/theme.
     * It receives the button's `enabled` state so inline icons can tint accordingly.
     * [text] is a plain fallback used by non-annotated surfaces (e.g. slide-to-confirm).
     */
    data class Annotated(
        override val text: String,
        val builder: @Composable (enabled: Boolean) -> AnnotatedButtonLabel,
    ) : AmountEntryLabel {
        @Composable
        override fun annotated(enabled: Boolean): AnnotatedButtonLabel = builder(enabled)
    }
}
