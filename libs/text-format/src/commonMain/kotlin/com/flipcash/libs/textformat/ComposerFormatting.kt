package com.flipcash.libs.textformat

/** The inline styles a composer button wraps a selection in. */
enum class InlineFormat(val marker: Char, val style: FormatStyle) {
    Bold('*', FormatStyle.Bold),
    Italic('_', FormatStyle.Italic),
    Strike('~', FormatStyle.Strike),
    Code('`', FormatStyle.Code),
}

/** The styles a composer button adds to, or removes from, every line the selection touches. */
enum class LineFormat { Bullet, Numbered, Quote, CodeBlock }

/** The draft after a formatting button: [text], and the selection in it. */
data class ComposerEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * The links and mentions in a draft, as the parser wants them. Detection lives with each app, so
 * the composer is handed it rather than doing its own.
 */
fun interface DraftRanges {
    fun of(text: String): List<ProtectedRange>

    companion object {
        val None = DraftRanges { emptyList() }
    }
}

/**
 * The draft with [format]'s markers around the selection, or with them taken off when they are
 * already there. Null when the button does nothing: the selection is only whitespace, or the
 * markers would not parse (a selection that starts mid-word), which is also when the button shows
 * as disabled. The parser is the only judge of that.
 *
 * With no selection it inserts the pair and puts the cursor between, which arms the style for what
 * is typed next. The cursor between a pair that is already there takes it out.
 */
fun toggleInline(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    format: InlineFormat,
    ranges: DraftRanges = DraftRanges.None,
): ComposerEdit? {
    var start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    var end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val m = format.marker

    if (start == end) {
        if (start >= 1 && start < text.length && text[start - 1] == m && text[start] == m) {
            return ComposerEdit(text.removeRange(start - 1, start + 1), start - 1, start - 1)
        }
        val next = text.substring(0, start) + m + m + text.substring(start)
        return ComposerEdit(next, start + 1, start + 1)
    }

    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start == end) return null

    removalFor(text, start, end, format, ranges)?.let { return it }

    val wrapped = text.substring(0, start) + m + text.substring(start, end) + m + text.substring(end)
    val before = parseTextFormat(text, ranges.of(text))
    val after = parseTextFormat(wrapped, ranges.of(wrapped))
    // The new markers parse when the display text is no longer than it was and still carries the
    // style: markers that stayed literal would have made it two characters longer.
    val consumed = after.display.length == before.display.length &&
        after.spans.count { it.style == format.style } >= before.spans.count { it.style == format.style } &&
        after.spans.any { it.style == format.style }
    return if (consumed) ComposerEdit(wrapped, start + 1, end + 1) else null
}

/** Whether [format] already covers the selection, so a button shows as selected. */
fun isInlineActive(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    format: InlineFormat,
    ranges: DraftRanges = DraftRanges.None,
): Boolean {
    var start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    var end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    if (start == end) return false
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return start < end && removalFor(text, start, end, format, ranges) != null
}

/**
 * The markers around, or at the ends of, the trimmed selection, taken out. Only when the parser
 * agrees the text carries [format] at all, so a stray `*` is not mistaken for bold.
 */
private fun removalFor(
    text: String,
    start: Int,
    end: Int,
    format: InlineFormat,
    ranges: DraftRanges,
): ComposerEdit? {
    val m = format.marker
    val parsed = parseTextFormat(text, ranges.of(text))
    if (parsed.spans.none { it.style == format.style }) return null
    val around = start >= 1 && end < text.length && text[start - 1] == m && text[end] == m
    if (around) {
        return ComposerEdit(text.removeRange(end, end + 1).removeRange(start - 1, start), start - 1, end - 1)
    }
    val within = end - start >= 3 && text[start] == m && text[end - 1] == m
    if (within) {
        return ComposerEdit(text.removeRange(end - 1, end).removeRange(start, start + 1), start, end - 2)
    }
    return null
}

private const val QUOTE = "> "
private val LIST_PREFIX = Regex("""^(?:[-*] |\d{1,3}\. )""")
private const val FENCE = "```"

/**
 * [format] added to every line the selection touches, or removed from all of them when each
 * already has it. A quote prefix goes first on a line, ahead of a list marker, and a list marker
 * replaces the other kind.
 */
fun toggleLine(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    format: LineFormat,
): ComposerEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val blockStart = text.lastIndexOf('\n', start - 1).let { if (start == 0) 0 else it + 1 }
    val lastChar = if (end > start && text[end - 1] == '\n') end - 1 else end
    val blockEnd = text.indexOf('\n', lastChar).let { if (it < 0) text.length else it }
    val region = text.substring(blockStart, blockEnd)

    if (format == LineFormat.CodeBlock) {
        val lines = region.split('\n')
        return if (lines.size >= 3 && lines.first() == FENCE && lines.last() == FENCE) {
            val inner = lines.subList(1, lines.size - 1).joinToString("\n")
            ComposerEdit(
                text.substring(0, blockStart) + inner + text.substring(blockEnd),
                blockStart,
                blockStart + inner.length,
            )
        } else {
            val wrapped = "$FENCE\n$region\n$FENCE"
            ComposerEdit(
                text.substring(0, blockStart) + wrapped + text.substring(blockEnd),
                blockStart + FENCE.length + 1,
                blockStart + FENCE.length + 1 + region.length,
            )
        }
    }

    val lines = region.split('\n')
    val allHave = lines.filter { it.isNotBlank() }.let { filled ->
        filled.isNotEmpty() && filled.all { hasPrefix(it, format) }
    }
    var number = 0
    val deltas = IntArray(lines.size)
    val rewritten = lines.mapIndexed { index, line ->
        val out = when {
            allHave -> removePrefix(line, format)
            line.isBlank() && lines.size > 1 -> line
            else -> addPrefix(line, format, ++number)
        }
        deltas[index] = out.length - line.length
        out
    }

    // A cursor moves with the prefix added on its own line and every line before it.
    fun map(position: Int): Int {
        var consumed = blockStart
        var shift = 0
        lines.forEachIndexed { index, line ->
            shift += deltas[index]
            if (position <= consumed + line.length || index == lines.lastIndex) {
                val lineStart = blockStart + rewritten.take(index).sumOf { it.length + 1 }
                return (position + shift).coerceAtLeast(lineStart)
            }
            consumed += line.length + 1
        }
        return position
    }

    val body = rewritten.joinToString("\n")
    return ComposerEdit(
        text.substring(0, blockStart) + body + text.substring(blockEnd),
        map(start),
        map(end),
    )
}

/** Whether every non-blank line the selection touches already has [format]. */
fun isLineActive(text: String, selectionStart: Int, selectionEnd: Int, format: LineFormat): Boolean {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val blockStart = text.lastIndexOf('\n', start - 1).let { if (start == 0) 0 else it + 1 }
    val lastChar = if (end > start && text[end - 1] == '\n') end - 1 else end
    val blockEnd = text.indexOf('\n', lastChar).let { if (it < 0) text.length else it }
    val lines = text.substring(blockStart, blockEnd).split('\n')
    if (format == LineFormat.CodeBlock) {
        return lines.size >= 3 && lines.first() == FENCE && lines.last() == FENCE
    }
    val filled = lines.filter { it.isNotBlank() }
    return filled.isNotEmpty() && filled.all { hasPrefix(it, format) }
}

private fun splitLine(line: String): Triple<String, String, String> {
    val quote = if (line.startsWith(QUOTE)) QUOTE else ""
    val afterQuote = line.removePrefix(quote)
    val list = LIST_PREFIX.find(afterQuote)?.value.orEmpty()
    return Triple(quote, list, afterQuote.removePrefix(list))
}

private fun hasPrefix(line: String, format: LineFormat): Boolean {
    val (quote, list, _) = splitLine(line)
    return when (format) {
        LineFormat.Quote -> quote.isNotEmpty()
        LineFormat.Bullet -> list == "- " || list == "* "
        LineFormat.Numbered -> list.isNotEmpty() && list[0].isDigit()
        LineFormat.CodeBlock -> false
    }
}

private fun removePrefix(line: String, format: LineFormat): String {
    val (quote, list, rest) = splitLine(line)
    return when (format) {
        LineFormat.Quote -> list + rest
        LineFormat.Bullet, LineFormat.Numbered -> quote + rest
        LineFormat.CodeBlock -> line
    }
}

private fun addPrefix(line: String, format: LineFormat, number: Int): String {
    val (quote, list, rest) = splitLine(line)
    return when (format) {
        LineFormat.Quote -> QUOTE + list + rest
        LineFormat.Bullet -> quote + "- " + rest
        LineFormat.Numbered -> "$quote$number. $rest"
        LineFormat.CodeBlock -> line
    }
}

/** The draft with a link written at the selection: `[selection](url)`, or [url] alone with none. */
fun insertLink(text: String, selectionStart: Int, selectionEnd: Int, url: String): ComposerEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val written = if (start == end) url else "[${text.substring(start, end)}]($url)"
    val next = text.substring(0, start) + written + text.substring(end)
    return ComposerEdit(next, start + written.length, start + written.length)
}

/**
 * The offsets in [raw] of the characters the parser consumed, so a field can dim the markers it
 * shows. [parsed] is `parseTextFormat(raw, ...)`; consumed characters are the ones the display text
 * does not account for, matched left to right.
 */
fun consumedRanges(raw: String, parsed: FormattedText): List<IntRange> {
    val display = parsed.display
    if (display == raw) return emptyList()
    val out = mutableListOf<IntRange>()
    var j = 0
    var runStart = -1
    for (i in raw.indices) {
        if (j < display.length && raw[i] == display[j]) {
            if (runStart >= 0) out += runStart until i
            runStart = -1
            j++
        } else if (runStart < 0) {
            runStart = i
        }
    }
    if (runStart >= 0) out += runStart until raw.length
    return out
}
