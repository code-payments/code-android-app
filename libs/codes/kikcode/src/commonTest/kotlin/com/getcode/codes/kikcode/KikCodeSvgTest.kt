package com.getcode.codes.kikcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KikCodeSvgTest {

    private val payload = ByteArray(20) { (it * 37 + 11).toByte() }

    @Test
    fun omits_the_background_rect_when_no_background_is_given() {
        val svg = KikCodeSvg.render(payload, background = null)
        assertFalse(svg.contains("<rect"), "transparent export should not paint a background")
    }

    @Test
    fun paints_the_background_when_one_is_given() {
        val svg = KikCodeSvg.render(payload, background = "#0A0A0F")
        assertTrue(svg.contains("<rect width=\"1024\" height=\"1024\" fill=\"#0A0A0F\"/>"))
    }

    @Test
    fun omits_the_badge_when_it_is_switched_off() {
        assertFalse(KikCodeSvg.render(payload, includeBadge = false).contains("evenodd"))
        assertTrue(KikCodeSvg.render(payload, includeBadge = true).contains("evenodd"))
    }

    @Test
    fun the_document_is_self_contained() {
        val svg = KikCodeSvg.render(payload, background = "#000000")
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(svg.trimEnd().endsWith("</svg>"))
        // No fonts, no text, no external references -- the export must not depend on the host.
        assertFalse(svg.contains("<text"))
        assertFalse(svg.contains("<image"))
        assertFalse(svg.contains("xlink:href"))
        assertFalse(svg.contains("http", ignoreCase = true) && svg.contains("<image"))
    }

    @Test
    fun arcs_are_stroked_with_round_caps_so_runs_read_as_joined_dots() {
        val svg = KikCodeSvg.render(payload)
        assertTrue(svg.contains("stroke-linecap=\"round\""))
        assertTrue(svg.contains("<g fill=\"none\" stroke=\"#FFFFFF\""))
    }

    @Test
    fun numbers_are_formatted_deterministically() {
        assertEquals("0", KikCodeSvg.num(0.0))
        assertEquals("0", KikCodeSvg.num(-0.0))
        assertEquals("1", KikCodeSvg.num(1.0))
        assertEquals("1.5", KikCodeSvg.num(1.5))
        assertEquals("-1.5", KikCodeSvg.num(-1.5))
        // Trailing zeros are trimmed, so the same value never has two spellings.
        assertEquals("2.1", KikCodeSvg.num(2.1000004))
        assertEquals("3", KikCodeSvg.num(2.9999996))
        // Three decimals kept, the fourth rounded away.
        assertEquals("0.123", KikCodeSvg.num(0.1234))
        assertEquals("0.124", KikCodeSvg.num(0.1236))
        // Ties go toward positive infinity on both platforms (`roundToLong`).
        assertEquals("0.002", KikCodeSvg.num(0.0015))
        assertEquals("-0.001", KikCodeSvg.num(-0.0015))
    }
}
