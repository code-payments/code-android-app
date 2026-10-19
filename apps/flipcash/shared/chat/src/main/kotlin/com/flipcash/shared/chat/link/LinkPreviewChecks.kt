package com.flipcash.shared.chat.link

/**
 * The checks a sender runs before attaching a `messaging.v1.LinkPreview` to a text message: the
 * url must be a whole link in the text, and the scraped title and description are cleaned to what
 * the contract accepts. Pinned by `link_preview_send.json`, which the iOS app runs too; a
 * disagreement is fixed in the canonical fixture, never here.
 *
 * Lengths are Unicode code points, as the contract counts them. Neither [String.length] (UTF-16
 * units) nor a grapheme count agrees with that, so nothing here uses them for a limit.
 */
object LinkPreviewChecks {

    const val URL_MAX = 2048
    const val TITLE_MAX = 256
    const val DESCRIPTION_MAX = 512

    // Unicode White_Space, spelled out: Char.isWhitespace() counts U+001C-U+001F and omits U+00A0.
    private val whiteSpace: Set<Int> = buildSet {
        addAll(listOf(0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0x85, 0xA0, 0x1680))
        addAll(0x2000..0x200A)
        addAll(listOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000))
    }
    private val whiteSpaceChars: Set<Char> = whiteSpace.map { it.toChar() }.toSet()

    private val bidiEmbeddings: Set<Int> = (0x202A..0x202E).toSet() + (0x2066..0x2069).toSet()
    private val bidiInUrl: Set<Int> = bidiEmbeddings + setOf(0x061C, 0x200E, 0x200F)

    private const val OPENERS = "([{<\"'"
    private const val TRAILERS = ".,;:!?)]}>\"'"
    private val labelChars: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + '-').toSet()
    private val hexChars: Set<Char> = (('0'..'9') + ('a'..'f') + ('A'..'F')).toSet()

    /**
     * Whether [url] stands in [text] as a whole link the server will accept: a valid http(s) url
     * that appears byte for byte, bounded by whitespace once surrounding openers and trailing
     * punctuation are set aside.
     */
    fun wholeLink(text: String, url: String): Boolean {
        if (!urlOk(url)) return false
        var start = text.indexOf(url)
        while (start != -1) {
            var i = start
            while (i > 0 && text[i - 1] in OPENERS) i--
            val before = i == 0 || text[i - 1] in whiteSpaceChars
            var j = start + url.length
            while (j < text.length && text[j] in TRAILERS) j++
            val after = j == text.length || text[j] in whiteSpaceChars
            if (before && after) return true
            start = text.indexOf(url, start + 1)
        }
        return false
    }

    /** The title to send for a scraped [raw] one, or null when nothing visible is left and the preview is dropped. */
    fun cleanTitle(raw: String): String? = clean(raw, TITLE_MAX, keepLineFeeds = false)

    /** The description to send for a scraped [raw] one; empty for none. */
    fun cleanDescription(raw: String): String = clean(raw, DESCRIPTION_MAX, keepLineFeeds = true).orEmpty()

    private fun urlOk(url: String): Boolean {
        val length = url.codePointCount(0, url.length)
        if (length !in 1..URL_MAX) return false
        val rest = when {
            url.startsWith("https://") -> url.removePrefix("https://")
            url.startsWith("http://") -> url.removePrefix("http://")
            else -> return false
        }
        if (url.codePoints().anyMatch { it in whiteSpace || it in bidiInUrl }) return false
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) return false
        var host = authority
        if (':' in authority) {
            host = authority.substringBeforeLast(':')
            val port = authority.substringAfterLast(':')
            if (port.isEmpty() || !port.all { it in '0'..'9' }) return false
            val n = port.toLongOrNull() ?: return false
            if (n !in 1..65535) return false
        }
        return hostOk(host)
    }

    private fun hostOk(raw: String): Boolean {
        val host = raw.removeSuffix(".")
        if (host.isEmpty()) return false
        val labels = host.split('.')
        if (labels.any { it.isEmpty() || !it.all { c -> c in labelChars } }) return false
        val last = labels.last()
        if (last.all { it in '0'..'9' }) return false
        if (last.take(2).lowercase() == "0x" && last.drop(2).all { it in hexChars }) return false
        return true
    }

    private fun clean(raw: String, limit: Int, keepLineFeeds: Boolean): String? {
        val s = raw.replace("\r\n", "\n").replace('\r', '\n')
        val out = StringBuilder()
        s.codePoints().forEach { cp ->
            when {
                cp == 0x0A || cp == 0x2028 || cp == 0x2029 -> out.append(if (keepLineFeeds) '\n' else ' ')
                cp == 0x09 || cp == 0x0B || cp == 0x0C -> out.append(' ')
                Character.getType(cp) == Character.CONTROL.toInt() || cp in bidiEmbeddings -> Unit
                else -> out.appendCodePoint(cp)
            }
        }
        var cleaned = out.toString().trimWhiteSpace()
        if (cleaned.codePointCount(0, cleaned.length) > limit) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, limit))
        }
        cleaned = cleaned.trimWhiteSpace()
        return cleaned.takeIf { visible(it) }
    }

    private fun String.trimWhiteSpace(): String = trim { it in whiteSpaceChars }

    private fun visible(s: String): Boolean = s.codePoints().anyMatch {
        it !in whiteSpace && Character.getType(it) != Character.FORMAT.toInt()
    }
}
