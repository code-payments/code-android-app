package com.getcode.libs.emojis.reactions

import java.awt.Font

actual fun deviceGlyphProbe(): EmojiDrawability.GlyphProbe = AwtGlyphProbe

/**
 * Desktop probe: asks the logical `Dialog` font, which falls back across the installed fonts,
 * whether it can display every code point. AWT has no equivalent of `Paint.hasGlyph`'s
 * single-glyph check, so a ZWJ sequence a font would draw as separate components still reads as
 * drawable here.
 */
object AwtGlyphProbe : EmojiDrawability.GlyphProbe {
    private val font by lazy { Font(Font.DIALOG, Font.PLAIN, 12) }

    override fun isSingleGlyph(text: String): Boolean = text.isNotEmpty() && font.canDisplayUpTo(text) == -1
}
