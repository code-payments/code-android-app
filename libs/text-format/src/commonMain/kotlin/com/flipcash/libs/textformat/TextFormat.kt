package com.flipcash.libs.textformat

/** What a detected range is. The parser never detects these itself; callers pass them in. */
enum class RangeKind { Link, Mention }

/**
 * A link or mention that detection found in the raw text. [start] and [end] are UTF-16 code unit
 * offsets (Kotlin `String` indices), half-open. A marker inside a range is part of it and never
 * opens or closes a style.
 */
data class ProtectedRange(
    val start: Int,
    val end: Int,
    val kind: RangeKind,
)

/** The styles the parser produces. */
enum class FormatStyle { Bold, Italic, Strike, Code, CodeBlock, Quote, Bullet, Numbered }

/** A [style] over [FormattedText.display], UTF-16 code unit offsets, half-open. */
data class StyledSpan(
    val start: Int,
    val end: Int,
    val style: FormatStyle,
)

/**
 * An input range moved to [FormattedText.display] offsets. A masked link `[text](url)` becomes a
 * [RangeKind.Link] over `text` with the URL as [target]. A range without a [target] opens its own
 * text.
 */
data class DisplayRange(
    val start: Int,
    val end: Int,
    val kind: RangeKind,
    val target: String? = null,
)

/**
 * The parser's result. [display] is the raw text minus the consumed markers. [spans] are
 * normalized per style (touching or overlapping runs merged) and ordered by start, then longest
 * first, then style name. [displayRanges] are ordered by start, end, kind, then target.
 */
data class FormattedText(
    val display: String,
    val spans: List<StyledSpan>,
    val displayRanges: List<DisplayRange>,
)
