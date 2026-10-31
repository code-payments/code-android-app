package com.getcode.libs.emojis.reactions

import android.graphics.Paint

/** Answers whether this device draws an emoji as one glyph, matching iOS's `EmojiDrawability`. */
object EmojiDrawability {

    /**
     * Returns whether this device draws [emoji] as a single glyph.
     *
     * [Paint.hasGlyph] asks the platform's font fallback whether a string resolves to one glyph
     * (a ligature for a ZWJ/skin-tone sequence, or the base glyph for a plain emoji) rather than
     * falling apart into several tofu or component glyphs side by side — the same question iOS's
     * `CTLine` run probe answers by counting AppleColorEmoji glyphs.
     */
    fun isDrawable(emoji: String, probe: GlyphProbe = PaintGlyphProbe): Boolean = probe.isSingleGlyph(emoji)

    /** Seam so the merge logic that consumes drawability can be tested without a real typeface. */
    fun interface GlyphProbe {
        fun isSingleGlyph(text: String): Boolean
    }

    /** The real, device-backed probe: [android.graphics.Paint.hasGlyph]. */
    object PaintGlyphProbe : GlyphProbe {
        private val paint = Paint()

        override fun isSingleGlyph(text: String): Boolean = paint.hasGlyph(text)
    }
}
