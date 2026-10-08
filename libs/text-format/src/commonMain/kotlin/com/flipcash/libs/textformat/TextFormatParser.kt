package com.flipcash.libs.textformat

/**
 * Parses chat text markup (bold, italic, strike, code, code blocks, quotes, lists, masked links).
 *
 * [ranges] are the links and mentions that detection found in [text]; a marker inside one never
 * opens or closes a style. Offsets in and out are UTF-16 code units. The rules are in
 * `docs/superpowers/specs/2026-10-07-chat-text-formatting-design.md` in the orchestrator repo, and
 * `text_format.json` is the authority.
 */
fun parseTextFormat(text: String, ranges: List<ProtectedRange>): FormattedText =
    TextFormatParser(text, ranges).parse()

private val INLINE_MARKERS = charArrayOf('*', '_', '~')
private const val FENCE = "```"

// A backslash before one of these is removed and the character after it is literal.
private const val ESCAPABLE = "\\*_~`>-.[]"

private fun styleOf(marker: Int): FormatStyle = when (marker) {
    '*'.code -> FormatStyle.Bold
    '_'.code -> FormatStyle.Italic
    else -> FormatStyle.Strike
}

/** Works on code points, as the reference parser does; offsets convert at the edges. */
private class TextFormatParser(private val text: String, inputRanges: List<ProtectedRange>) {
    // Code points of [text], and the UTF-16 offset where each starts (one extra entry at the end).
    private val cps: IntArray
    private val u16: IntArray
    private val n: Int

    private val protected: BooleanArray
    private val inRange: BooleanArray
    private val removed: BooleanArray
    private val escaped: BooleanArray

    private class RawRange(val kind: RangeKind, val start: Int, val end: Int)
    private class Masked(val textStart: Int, val textEnd: Int, val urlStart: Int, val urlEnd: Int)
    private class RawSpan(val style: FormatStyle, val start: Int, val end: Int)

    private val ranges: List<RawRange>
    private val links: List<RawRange>
    private val masked = ArrayList<Masked>()
    private val spans = ArrayList<RawSpan>()
    private val blocksRaw = ArrayList<IntArray>()

    init {
        val points = ArrayList<Int>(text.length)
        val starts = ArrayList<Int>(text.length + 1)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            starts.add(i)
            if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                points.add(((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000)
                i += 2
            } else {
                points.add(c.code)
                i += 1
            }
        }
        starts.add(text.length)
        cps = points.toIntArray()
        u16 = starts.toIntArray()
        n = cps.size

        // UTF-16 offset -> code point index. An offset inside a surrogate pair rounds down.
        val toCp = IntArray(text.length + 1)
        for (k in 0 until n) for (u in u16[k] until u16[k + 1]) toCp[u] = k
        toCp[text.length] = n
        ranges = inputRanges.map {
            RawRange(it.kind, toCp[it.start.coerceIn(0, text.length)], toCp[it.end.coerceIn(0, text.length)])
        }
        links = ranges.filter { it.kind == RangeKind.Link }

        protected = BooleanArray(n)
        inRange = BooleanArray(n)
        removed = BooleanArray(n)
        escaped = BooleanArray(n)
        for (r in ranges) for (k in r.start until r.end) {
            protected[k] = true
            inRange[k] = true
        }
    }

    // --- character classes (spec rule 2) ---

    private fun classOf(cp: Int) = TextFormatUnicode.classify(cp)

    private fun isWord(cp: Int): Boolean = when (classOf(cp)) {
        CodePointClass.LETTER, CodePointClass.MARK, CodePointClass.DIGIT -> true
        else -> false
    }

    private fun isSpace(cp: Int): Boolean =
        cp == '\t'.code || cp == '\r'.code || cp == '\n'.code || classOf(cp) == CodePointClass.SPACE

    // CommonMark whitespace; null (a line edge) counts too.
    private fun isCmSpace(cp: Int?): Boolean =
        cp == null || cp == '\t'.code || cp == '\n'.code || cp == 0x0C || cp == '\r'.code ||
            classOf(cp) == CodePointClass.SPACE

    private fun isCmPunct(cp: Int?): Boolean = cp != null && classOf(cp) == CodePointClass.PUNCT

    // --- lookups ---

    private fun markerAt(i: Int, m: Int): Boolean = i in 0 until n && cps[i] == m && !escaped[i]

    private fun inRun(i: Int): Boolean {
        val m = cps[i]
        return markerAt(i - 1, m) || markerAt(i + 1, m)
    }

    /** The code point before [i], skipping combining marks back to their base. */
    private fun before(i: Int): Int? {
        var j = i - 1
        while (j >= 0 && classOf(cps[j]) == CodePointClass.MARK) j--
        return if (j >= 0) cps[j] else null
    }

    private fun after(i: Int): Int? = if (i + 1 < n) cps[i + 1] else null

    private fun canOpen(i: Int): Boolean {
        if (protected[i] || inRun(i)) return false
        if (cps[i] == '_'.code) {
            val prev = before(i)
            val nxt = after(i)
            val (left, right) = flanking(prev, nxt)
            return left && (!right || isCmPunct(prev))
        }
        val prev = before(i)
        val nxt = after(i)
        if (prev != null && isWord(prev)) return false
        return nxt != null && !isSpace(nxt)
    }

    private fun canClose(i: Int): Boolean {
        if (protected[i] || inRun(i)) return false
        val prev = before(i)
        val nxt = after(i)
        if (cps[i] == '_'.code) {
            val (left, right) = flanking(prev, nxt)
            return right && (!left || isCmPunct(nxt))
        }
        if (nxt != null && isWord(nxt)) return false
        return prev != null && !isSpace(prev)
    }

    /** CommonMark 0.31.2 left- and right-flanking, for `_`. */
    private fun flanking(prev: Int?, nxt: Int?): Pair<Boolean, Boolean> {
        val left = !isCmSpace(nxt) && (!isCmPunct(nxt) || isCmSpace(prev) || isCmPunct(prev))
        val right = !isCmSpace(prev) && (!isCmPunct(prev) || isCmSpace(nxt) || isCmPunct(nxt))
        return left to right
    }

    private fun startsWithFence(i: Int): Boolean =
        i + 3 <= n && cps[i] == '`'.code && cps[i + 1] == '`'.code && cps[i + 2] == '`'.code

    private fun fenceAt(i: Int): Boolean =
        startsWithFence(i) && !(i > 0 && cps[i - 1] == '\\'.code) &&
            !(protected[i] || protected[i + 1] || protected[i + 2])

    // --- rule 3: code blocks, before anything else ---

    private fun codeBlocks() {
        var i = 0
        while (i < n) {
            val lineStart = i == 0 || cps[i - 1] == '\n'.code
            if (lineStart && fenceAt(i)) {
                var close = -1
                for (k in i + 3 until n - 2) {
                    val lineEnd = k + 3 == n || cps[k + 3] == '\n'.code
                    if (lineEnd && fenceAt(k)) {
                        close = k
                        break
                    }
                }
                if (close >= 0) {
                    var a = i + 3
                    var b = close
                    if (a < b && cps[a] == '\n'.code) a++
                    if (a < b && cps[b - 1] == '\n'.code) b--
                    if (a < b) {
                        for (k in i until a) removed[k] = true
                        for (k in b until close + 3) removed[k] = true
                        spans.add(RawSpan(FormatStyle.CodeBlock, a, b))
                        for (k in i until close + 3) protected[k] = true
                        blocksRaw.add(intArrayOf(i, close + 3))
                        i = close + 3
                        continue
                    }
                }
            }
            i++
        }
    }

    // --- rule 4: quote, then list, at the start of a line ---

    private fun blocks(a: Int, b: Int) {
        var c = a
        if (b - a >= 3 && cps[a] == '>'.code && cps[a + 1] == ' '.code && !protected[a]) {
            removed[a] = true
            removed[a + 1] = true
            spans.add(RawSpan(FormatStyle.Quote, a + 2, b))
            c = a + 2
        }
        if (c < n && protected[c]) return
        if (b - c >= 3 && (cps[c] == '-'.code || cps[c] == '*'.code) && cps[c + 1] == ' '.code) {
            spans.add(RawSpan(FormatStyle.Bullet, c, b))
            return
        }
        var d = c
        while (d < b && d - c < 3 && cps[d] >= '0'.code && cps[d] <= '9'.code) d++
        if (c < d && b - d >= 3 && cps[d] == '.'.code && cps[d + 1] == ' '.code) {
            spans.add(RawSpan(FormatStyle.Numbered, c, b))
        }
    }

    // --- rule 5: escapes and inline code in one left-to-right pass ---

    private fun escapesAndCode(a: Int, b: Int) {
        var i = a
        while (i < b) {
            if (cps[i] == '\\'.code && !protected[i] && i + 1 < b &&
                cps[i + 1] < 0x80 && ESCAPABLE.indexOf(cps[i + 1].toChar()) >= 0 && !protected[i + 1]
            ) {
                removed[i] = true
                escaped[i + 1] = true
                protected[i + 1] = true
                i += 2
                continue
            }
            if (cps[i] == '`'.code && canOpen(i)) {
                var j = -1
                for (k in i + 2 until b) {
                    if (cps[k] == '`'.code && canClose(k)) {
                        j = k
                        break
                    }
                }
                if (j >= 0) {
                    spans.add(RawSpan(FormatStyle.Code, i + 1, j))
                    removed[i] = true
                    removed[j] = true
                    for (k in i..j) protected[k] = true
                    i = j + 1
                    continue
                }
            }
            i++
        }
    }

    private fun styles(a: Int, b: Int, allowed: Set<Int>) {
        var i = a
        while (i < b) {
            val m = cps[i]
            if (m in allowed && canOpen(i)) {
                var j = -1
                for (k in i + 2 until b) {
                    if (cps[k] == m && canClose(k)) {
                        j = k
                        break
                    }
                }
                if (j >= 0) {
                    spans.add(RawSpan(styleOf(m), i + 1, j))
                    removed[i] = true
                    removed[j] = true
                    styles(i + 1, j, allowed - m)
                    i = j + 1
                    continue
                }
            }
            i++
        }
    }

    // --- rule 6: masked links, after escapes and code, before the other styles ---

    private fun maskedLinks(a: Int, b: Int) {
        var i = a
        while (i < b) {
            if (cps[i] == '['.code && !protected[i]) {
                var j = -1
                for (k in i + 1 until b) {
                    if (cps[k] == ']'.code && !protected[k]) {
                        j = k
                        break
                    }
                }
                var url: RawRange? = null
                if (j >= 0 && j > i + 1 && j + 1 < b && cps[j + 1] == '('.code) {
                    url = links.firstOrNull { it.start == j + 2 }
                }
                if (url != null && url.end < b && cps[url.end] == ')'.code &&
                    (i + 1 until j).none { inRange[it] }
                ) {
                    val close = url.end
                    removed[i] = true
                    removed[j] = true
                    removed[j + 1] = true
                    removed[close] = true
                    for (k in url.start until url.end) removed[k] = true
                    protected[i] = true
                    protected[j] = true
                    protected[j + 1] = true
                    protected[close] = true
                    masked.add(Masked(i + 1, j, url.start, url.end))
                    i = close + 1
                    continue
                }
            }
            i++
        }
    }

    // --- driver ---

    fun parse(): FormattedText {
        codeBlocks()
        val allowed = INLINE_MARKERS.map { it.code }.toSet()
        var a = 0
        while (a <= n) {
            var b = a
            while (b < n && cps[b] != '\n'.code) b++
            // A code block starts at a line start and ends at a line end, so a line is either
            // wholly inside one or wholly outside.
            if (blocksRaw.none { it[0] <= a && b <= it[1] }) {
                blocks(a, b)
                escapesAndCode(a, b)
                maskedLinks(a, b)
                styles(a, b, allowed)
            }
            a = b + 1
        }
        return output()
    }

    private fun output(): FormattedText {
        // raw code point index -> UTF-16 offset in display
        val displayOffset = IntArray(n + 1)
        val display = StringBuilder(text.length)
        for (k in 0 until n) {
            displayOffset[k] = display.length
            if (!removed[k]) display.appendRange(text, u16[k], u16[k + 1])
        }
        displayOffset[n] = display.length

        val styled = spans.map { StyledSpan(displayOffset[it.start], displayOffset[it.end], it.style) }
        val consumed = masked.map { it.urlStart to it.urlEnd }.toSet()
        val out = ArrayList<DisplayRange>()
        for (r in ranges) {
            if ((r.start to r.end) in consumed) continue
            out.add(DisplayRange(displayOffset[r.start], displayOffset[r.end], r.kind))
        }
        for (m in masked) {
            val target = text.substring(u16[m.urlStart], u16[m.urlEnd])
            out.add(DisplayRange(displayOffset[m.textStart], displayOffset[m.textEnd], RangeKind.Link, target))
        }
        out.sortWith(
            compareBy<DisplayRange>({ it.start }, { it.end }, { kindName(it.kind) }, { it.target ?: "" }),
        )
        return FormattedText(display.toString(), normalize(styled), out)
    }

    // Matches the fixture's lowercase kind names, so ordering agrees with the reference.
    private fun kindName(kind: RangeKind): String = if (kind == RangeKind.Link) "link" else "mention"

    /** Per style, merge spans that touch or overlap; order by start, longest first, then style. */
    private fun normalize(input: List<StyledSpan>): List<StyledSpan> {
        val merged = ArrayList<StyledSpan>()
        for ((style, group) in input.filter { it.start < it.end }.groupBy { it.style }) {
            val sorted = group.sortedBy { it.start }
            var curA = sorted[0].start
            var curB = sorted[0].end
            for (s in sorted.drop(1)) {
                if (s.start <= curB) {
                    curB = maxOf(curB, s.end)
                } else {
                    merged.add(StyledSpan(curA, curB, style))
                    curA = s.start
                    curB = s.end
                }
            }
            merged.add(StyledSpan(curA, curB, style))
        }
        return merged.sortedWith(
            compareBy<StyledSpan>({ it.start }, { -it.end }, { styleName(it.style) }),
        )
    }

    private fun styleName(style: FormatStyle): String = when (style) {
        FormatStyle.Bold -> "bold"
        FormatStyle.Italic -> "italic"
        FormatStyle.Strike -> "strike"
        FormatStyle.Code -> "code"
        FormatStyle.CodeBlock -> "codeBlock"
        FormatStyle.Quote -> "quote"
        FormatStyle.Bullet -> "bullet"
        FormatStyle.Numbered -> "numbered"
    }
}
