package com.getcode.libs.emojis.reactions

import android.graphics.Paint

actual fun deviceGlyphProbe(): EmojiDrawability.GlyphProbe = PaintGlyphProbe

/** The real, device-backed probe: [android.graphics.Paint.hasGlyph]. */
object PaintGlyphProbe : EmojiDrawability.GlyphProbe {
    private val paint = Paint()

    override fun isSingleGlyph(text: String): Boolean = paint.hasGlyph(text)
}
