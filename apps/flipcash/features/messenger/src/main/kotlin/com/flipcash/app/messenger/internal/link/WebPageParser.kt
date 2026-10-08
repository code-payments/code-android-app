package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A single-pass scanner for a page's head. Hand-written rather than jsoup because jsoup decodes
 * every HTML entity and the `link_metadata.json` rule keeps unknown named entities as written.
 * Steps follow the fixture's rules and the iOS scanner, in the same order.
 */
internal object WebPageParser {

    /** Null when the page has no title. */
    fun parse(body: ByteArray, finalUrl: String): LinkCard.Web.State.Resolved? {
        val html = String(body, Charsets.UTF_8)
        val head = html.substring(0, headEnd(html))

        val metas = HashMap<String, String>()
        var titleText: String? = null
        var i = 0
        while (true) {
            val lt = head.indexOf('<', i)
            if (lt < 0) break
            i = when {
                head.startsWith("<meta", lt, ignoreCase = true) && isTagEnd(head, lt + 5) -> {
                    val end = readMeta(head, lt + 5, metas)
                    end
                }
                head.startsWith("<title", lt, ignoreCase = true) && isTagEnd(head, lt + 6) -> {
                    val open = head.indexOf('>', lt + 6)
                    if (open < 0) break
                    val close = head.indexOf("</title", open + 1, ignoreCase = true)
                    val text = head.substring(open + 1, if (close < 0) head.length else close)
                    if (titleText == null) titleText = clean(decode(text))
                    if (close < 0) head.length else close + 7
                }
                else -> lt + 1
            }
        }

        val title = metas["og:title"] ?: titleText ?: return null
        val description = metas["og:description"] ?: metas["description"]
        val base = finalUrl.toHttpUrlOrNull()
        // An address the image fetch would refuse draws no slot, so it is dropped here (D11, D12, D13).
        val image = metas["og:image"]
            ?.takeUnless { WebLinks.hasEscapedHost(it) || WebLinks.isUnsafeLocation(it) }
            ?.let { base?.resolve(it) }
            ?.takeIf { it.scheme == "https" && it.port == 443 && WebLinks.isEligibleHost(it.host) }
            ?.toString()
        val host = (base?.host ?: return null).lowercase().removePrefix("www.")
        return LinkCard.Web.State.Resolved(title, description, image, host)
    }

    private fun headEnd(html: String): Int {
        val a = html.indexOf("</head", ignoreCase = true)
        val b = html.indexOf("<body", ignoreCase = true)
        return when {
            a < 0 && b < 0 -> html.length
            a < 0 -> b
            b < 0 -> a
            else -> minOf(a, b)
        }
    }

    /** True when the tag name ends at [index], so `<metadata` is not `<meta`. */
    private fun isTagEnd(s: String, index: Int): Boolean =
        index >= s.length || s[index].isWhitespace() || s[index] == '/' || s[index] == '>'

    /** Reads attributes from [from] to the closing `>`, records the first non-empty value, returns the next index. */
    private fun readMeta(s: String, from: Int, out: MutableMap<String, String>): Int {
        var i = from
        var property: String? = null
        var name: String? = null
        var content: String? = null
        while (i < s.length && s[i] != '>') {
            if (s[i].isWhitespace() || s[i] == '/') { i++; continue }
            val keyStart = i
            while (i < s.length && !s[i].isWhitespace() && s[i] !in "=/>") i++
            val key = s.substring(keyStart, i).lowercase()
            while (i < s.length && s[i].isWhitespace()) i++
            var value = ""
            if (i < s.length && s[i] == '=') {
                i++
                while (i < s.length && s[i].isWhitespace()) i++
                if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
                    val q = s[i]
                    val close = s.indexOf(q, i + 1)
                    val end = if (close < 0) s.length else close
                    value = s.substring(i + 1, end)
                    i = if (close < 0) s.length else close + 1
                } else {
                    val vs = i
                    while (i < s.length && !s[i].isWhitespace() && s[i] != '>') i++
                    value = s.substring(vs, i)
                }
            }
            when (key) {
                "property" -> if (property == null) property = value
                "name" -> if (name == null) name = value
                "content" -> if (content == null) content = value
            }
        }
        val key = (property ?: name)?.let { clean(it) }?.lowercase()
        val text = content?.let { clean(decode(it)) }
        if (key != null && text != null) out.putIfAbsent(key, text)
        return minOf(i + 1, s.length)
    }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 12)) {
                    val decoded = entity(s.substring(i + 1, semi))
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun entity(name: String): String? = when (name) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> " "
        else -> if (name.startsWith("#")) {
            val hex = name.startsWith("#x") || name.startsWith("#X")
            val cp = name.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10)
            cp?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) }
        } else null
    }

    /** NBSP to space, runs collapsed, trimmed; empty becomes null. */
    private fun clean(s: String): String? {
        val sb = StringBuilder(s.length)
        var space = false
        for (c in s) {
            if (c.isWhitespace() || c == ' ') {
                space = sb.isNotEmpty()
            } else {
                if (space) sb.append(' ')
                space = false
                sb.append(c)
            }
        }
        return sb.toString().ifEmpty { null }
    }
}
