package com.flipcash.shared.chat.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.getcode.ui.utils.generateComplementaryColorPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The quote panel colours a citation by its sender, and iOS's ComplementaryPalette.swift is a
 * documented arithmetic-for-arithmetic port of this function. These are the hexes iOS's own tests
 * pin, so a change to either side's derivation surfaces here rather than as two apps quietly
 * colouring the same person differently.
 *
 * Compared as 8-bit hex, which is what iOS pins. The two platforms carry HSV out to different float
 * types, so equal colours are equal once quantized to a channel byte and not before.
 *
 * Only the first two stops are pinned. iOS deliberately omits the third stop's WCAG correction,
 * and a quote uses neither.
 */
class ComplementaryPaletteParityTest {

    @Test
    fun `the palette matches the values iOS pins`() {
        val first = assertNotNull(generateComplementaryColorPalette(IOS_IDENTIFIER_ONE))
        assertEquals("#D69336", first.first.hex())
        assertEquals("#D9CC3E", first.second.hex())

        val second = assertNotNull(generateComplementaryColorPalette(IOS_IDENTIFIER_TWO))
        assertEquals("#D936CB", second.first.hex())
        assertEquals("#D93E98", second.second.hex())
    }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    private companion object {
        // Byte-for-byte the identifiers iOS's own palette test uses: the UUIDs
        // 8B3D4E1A-0000-4000-8000-000000000007 and the all-zero UUID, in the order
        // `withUnsafeBytes(of: id.uuid)` hands them to SHA-512.
        val IOS_IDENTIFIER_ONE: List<Byte> = listOf<Byte>(
            0x8B.toByte(), 0x3D, 0x4E, 0x1A, 0x00, 0x00, 0x40, 0x00,
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07,
        )
        val IOS_IDENTIFIER_TWO: List<Byte> = List(16) { 0.toByte() }
    }
}
