package com.flipcash.shared.chat.ui

import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString

fun interface SpanAnnotator {
    fun AnnotatedString.Builder.annotate(text: String)
}

class UrlAnnotator(private val linkStyle: SpanStyle) : SpanAnnotator {
    override fun AnnotatedString.Builder.annotate(text: String) {
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val url = matcher.group() ?: continue
            val resolved = if (!url.startsWith("http")) "https://$url" else url
            addLink(
                LinkAnnotation.Url(
                    url = resolved,
                    styles = TextLinkStyles(style = linkStyle),
                ),
                start = matcher.start(),
                end = matcher.end(),
            )
        }
    }
}

@Composable
fun rememberRichText(
    text: String,
    annotators: List<SpanAnnotator>,
): AnnotatedString = remember(text, annotators) {
    buildAnnotatedString {
        append(text)
        annotators.forEach { with(it) { annotate(text) } }
    }
}
