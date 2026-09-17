package com.flipcash.shared.chat.ui

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
        detectUrls(text).forEach { link ->
            addLink(
                LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(style = linkStyle),
                ),
                start = link.start,
                end = link.end,
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
