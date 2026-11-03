package com.getcode.libs.emojis.reactions

/** Answers whether this device draws an emoji as one glyph, matching iOS's `EmojiDrawability`. */
object EmojiDrawability {

    /**
     * Returns whether this device draws [emoji] as a single glyph.
     *
     * On Android, `Paint.hasGlyph` asks the platform's font fallback whether a string resolves to
     * one glyph (a ligature for a ZWJ/skin-tone sequence, or the base glyph for a plain emoji)
     * rather than falling apart into several tofu or component glyphs side by side — the same
     * question iOS's `CTLine` run probe answers by counting AppleColorEmoji glyphs.
     */
    fun isDrawable(emoji: String, probe: GlyphProbe = deviceGlyphProbe()): Boolean = probe.isSingleGlyph(emoji)

    /** Seam so the merge logic that consumes drawability can be tested without a real typeface. */
    fun interface GlyphProbe {
        fun isSingleGlyph(text: String): Boolean
    }
}

/** The probe backed by the running platform's fonts. */
expect fun deviceGlyphProbe(): EmojiDrawability.GlyphProbe
