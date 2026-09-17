package com.flipcash.shared.chat.ui

/**
 * Whether a message is nothing but a few emoji, and how many.
 *
 * A short all-emoji message is a reaction, not a sentence, so it is drawn large and without a
 * bubble — the emoji is the whole message, and a surface around it only makes it smaller. The
 * cut-off is a count of emoji, not of characters: one family sequence is one emoji however many
 * code points it takes to spell.
 *
 * Hand-rolled rather than handed to `BreakIterator` or `EmojiCompat`, because both answer a
 * different question. `BreakIterator` segments *any* text into graphemes, so it counts "abc" as
 * three and says nothing about whether those are emoji; `EmojiCompat` needs its font loaded and so
 * cannot be asked from a unit test. What is needed here is a yes/no on the whole string, which is
 * the check below: every cluster must be an emoji, or the message is ordinary text.
 */
internal object EmojiOnlyText {

    /** Above this, the message reads as a strip of emoji rather than as one gesture. */
    const val MAX_CLUSTERS = 3

    /**
     * The number of emoji in [text] when it holds nothing else and there are at most
     * [MAX_CLUSTERS] of them, or `null` when it is an ordinary message.
     */
    fun clusterCountOrNull(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        var index = 0
        var clusters = 0
        while (index < trimmed.length) {
            val codePoint = trimmed.codePointAt(index)
            // Emoji separated by spaces still read as a run of emoji, so the gaps are skipped
            // rather than counted or rejected.
            if (Character.isWhitespace(codePoint)) {
                index += Character.charCount(codePoint)
                continue
            }
            index = consumeCluster(trimmed, index) ?: return null
            clusters++
            if (clusters > MAX_CLUSTERS) return null
        }
        return clusters.takeIf { it > 0 }
    }

    /**
     * The index just past the emoji starting at [start], or `null` when nothing there is one.
     *
     * The three sequence shapes that are not just "a pictograph and its modifiers" — a flag, a
     * keycap, and a ZWJ join — are spelled out, because each one is only an emoji as a whole: a
     * lone regional indicator, or a digit with no keycap after it, is text.
     */
    private fun consumeCluster(text: String, start: Int): Int? {
        val first = text.codePointAt(start)

        if (isRegionalIndicator(first)) {
            val second = start + Character.charCount(first)
            if (second >= text.length) return null
            val pair = text.codePointAt(second)
            if (!isRegionalIndicator(pair)) return null
            return second + Character.charCount(pair)
        }

        if (isKeycapBase(first)) {
            var index = start + Character.charCount(first)
            if (index < text.length && text.codePointAt(index) == VARIATION_SELECTOR_EMOJI) index++
            if (index < text.length && text.codePointAt(index) == COMBINING_KEYCAP) return index + 1
            return null
        }

        if (!isEmojiBase(text, start)) return null
        var index = consumeModifiers(text, start + Character.charCount(first))

        while (index < text.length && text.codePointAt(index) == ZERO_WIDTH_JOINER) {
            val joined = index + 1
            if (joined >= text.length || !isEmojiBase(text, joined)) return null
            index = consumeModifiers(text, joined + Character.charCount(text.codePointAt(joined)))
        }
        return index
    }

    /** Skin tones, presentation selectors and the tag characters that spell a subdivision flag. */
    private fun consumeModifiers(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val isModifier = codePoint == VARIATION_SELECTOR_EMOJI ||
                    codePoint == VARIATION_SELECTOR_TEXT ||
                    isSkinTone(codePoint) ||
                    isTag(codePoint)
            if (!isModifier) break
            index += Character.charCount(codePoint)
        }
        return index
    }

    /**
     * Whether the code point at [index] opens an emoji.
     *
     * Astral pictographs are emoji on their own. The ones down in the BMP are shared with ordinary
     * typography — `©`, `™`, `←` are punctuation until a variation selector asks for the emoji
     * face — so those are admitted only when they default to an emoji presentation or carry that
     * selector. Without this, a message of "©" would be drawn as a jumbo emoji.
     */
    private fun isEmojiBase(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)
        if (!isPictographic(codePoint)) return false
        if (codePoint >= 0x1F000 || isEmojiPresentationByDefault(codePoint)) return true
        val next = index + Character.charCount(codePoint)
        return next < text.length && text.codePointAt(next) == VARIATION_SELECTOR_EMOJI
    }

    private fun isPictographic(codePoint: Int): Boolean =
        PICTOGRAPHIC_RANGES.any { codePoint in it }

    private fun isEmojiPresentationByDefault(codePoint: Int): Boolean =
        BMP_EMOJI_PRESENTATION_RANGES.any { codePoint in it }

    private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF

    private fun isSkinTone(codePoint: Int) = codePoint in 0x1F3FB..0x1F3FF

    private fun isTag(codePoint: Int) = codePoint in 0xE0020..0xE007F

    private fun isKeycapBase(codePoint: Int) =
        codePoint in 0x30..0x39 || codePoint == 0x23 || codePoint == 0x2A

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_TEXT = 0xFE0E
    private const val VARIATION_SELECTOR_EMOJI = 0xFE0F
    private const val COMBINING_KEYCAP = 0x20E3

    /** `Extended_Pictographic`, coarsened to the blocks that are wholly or near-wholly emoji. */
    private val PICTOGRAPHIC_RANGES = listOf(
        0x00A9..0x00A9,
        0x00AE..0x00AE,
        0x203C..0x203C,
        0x2049..0x2049,
        0x2122..0x2122,
        0x2139..0x2139,
        0x2194..0x21AA,
        0x231A..0x231B,
        0x2328..0x2328,
        0x23CF..0x23CF,
        0x23E9..0x23F3,
        0x23F8..0x23FA,
        0x24C2..0x24C2,
        0x25AA..0x25AB,
        0x25B6..0x25B6,
        0x25C0..0x25C0,
        0x25FB..0x25FE,
        0x2600..0x27BF,
        0x2934..0x2935,
        0x2B05..0x2B07,
        0x2B1B..0x2B1C,
        0x2B50..0x2B50,
        0x2B55..0x2B55,
        0x3030..0x3030,
        0x303D..0x303D,
        0x3297..0x3297,
        0x3299..0x3299,
        0x1F000..0x1FAFF,
        0x1FC00..0x1FFFD,
    )

    /** The BMP pictographs that are drawn as emoji with no variation selector asking for it. */
    private val BMP_EMOJI_PRESENTATION_RANGES = listOf(
        0x231A..0x231B,
        0x23E9..0x23EC,
        0x23F0..0x23F0,
        0x23F3..0x23F3,
        0x25FD..0x25FE,
        0x2614..0x2615,
        0x2648..0x2653,
        0x267F..0x267F,
        0x2693..0x2693,
        0x26A1..0x26A1,
        0x26AA..0x26AB,
        0x26BD..0x26BE,
        0x26C4..0x26C5,
        0x26CE..0x26CE,
        0x26D4..0x26D4,
        0x26EA..0x26EA,
        0x26F2..0x26F3,
        0x26F5..0x26F5,
        0x26FA..0x26FA,
        0x26FD..0x26FD,
        0x2705..0x2705,
        0x270A..0x270B,
        0x2728..0x2728,
        0x274C..0x274C,
        0x274E..0x274E,
        0x2753..0x2755,
        0x2757..0x2757,
        0x2795..0x2797,
        0x27B0..0x27B0,
        0x27BF..0x27BF,
        0x2B1B..0x2B1C,
        0x2B50..0x2B50,
        0x2B55..0x2B55,
    )
}
