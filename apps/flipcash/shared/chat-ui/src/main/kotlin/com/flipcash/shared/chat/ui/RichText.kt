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
            if (!authorityIsAscii(url)) continue
            if (!endsOnAsciiBoundary(text, matcher.end())) continue
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

    /**
     * A non-ASCII character glued to the host retargets the link: the reader sees
     * `send.flipcash.com\uD83D\uDE00/c/...` and the resolver gets a different host once the
     * authority is IDNA-mapped. iOS rejects the same message through
     * `LinkDetector.authorityIsASCII`; this is the same guard.
     *
     * Authority only. A non-ASCII path or query is ordinary content and changes nothing about
     * where the link points.
     */
    private fun authorityIsAscii(url: String): Boolean {
        val schemeEnd = url.indexOf("://")
        var i = if (schemeEnd < 0) 0 else schemeEnd + 3
        while (i < url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') return true
            if (c.code >= 0x80) return false
            i++
        }
        return true
    }

    /**
     * The same attack in the shape where the matcher stops short of the non-ASCII character
     * instead of swallowing it. `Patterns.WEB_URL` does that when there is no scheme:
     * `example.com\uD83D\uDE00` matches only `example.com`, which [authorityIsAscii] then sees as
     * clean. The character is still glued to the host in the text the reader is looking at.
     *
     * Trailing side only. A leading emoji is ordinary message text and the link after it is
     * exactly the link it appears to be.
     */
    private fun endsOnAsciiBoundary(text: String, end: Int): Boolean =
        end >= text.length || text.codePointAt(end) < 0x80
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
