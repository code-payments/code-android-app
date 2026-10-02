package com.flipcash.app.tokens.internal.explainer

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplainerNumberTextTest {

    @Test
    fun `slots are keyed by role with integer digits counted from the point`() {
        assertEquals(
            mapOf("P0" to '$', "L2" to '1', "L1" to '7', "L0" to '0', "S0" to 'K'),
            numberSlots("$170K"),
        )
    }

    @Test
    fun `fraction digits and the point get their own slots`() {
        assertEquals(
            mapOf("P0" to '$', "L1" to '2', "L0" to '7', "dot" to '.', "R0" to '0', "S0" to 'K'),
            numberSlots("$27.0K"),
        )
    }

    @Test
    fun `a digit keeps its slot when the string gets shorter`() {
        val before = numberSlots("$170K")
        val after = numberSlots("$27.0K")
        // The units digit stays in L0 and the leading digit drops out of L2.
        assertEquals('0', before["L0"])
        assertEquals('7', after["L0"])
        assertTrue("L2" in before && "L2" !in after)
    }

    @Test
    fun `a suffix change reuses the suffix slot`() {
        assertEquals('K', numberSlots("$999K")["S0"])
        assertEquals('M', numberSlots("$1.00M")["S0"])
    }

    @Test
    fun `text without digits becomes prefix slots`() {
        assertEquals(mapOf("P0" to '-', "P1" to '-'), numberSlots("--"))
        assertEquals(emptyMap(), numberSlots(""))
    }

    @Test
    fun `numericValue applies the K M B T suffix scale`() {
        assertEquals(22_700.0, numericValue("$22.7K"), 1e-6)
        assertEquals(1_000_000.0, numericValue("$1M"), 1e-6)
        assertEquals(2_500_000_000.0, numericValue("$2.5B"), 1e-3)
        assertEquals(3e12, numericValue("$3T"), 1.0)
        assertEquals(5.0, numericValue("$5"), 1e-9)
        assertEquals(0.0, numericValue("--"), 0.0)
    }

    @Test
    fun `direction is rising when the readout grows across a suffix`() {
        assertTrue(numericValue("$1.2M") >= numericValue("$999K"))
    }

    @Test
    fun `direction is falling when the readout shrinks across a suffix`() {
        assertTrue(numericValue("$999K") < numericValue("$1.2M"))
        assertTrue(numericValue("$27.0K") < numericValue("$170K"))
    }

    @Test
    fun `a settled glyph is sharp and opaque`() {
        val m = glyphMotion(d = 0f, fade = incomingFade(1f), p = 1f, presence = 1f)
        assertEquals(0f, m.travel, 1e-6f)
        assertEquals(1f, m.alpha, 1e-6f)
        assertEquals(0f, m.blur, 1e-6f)
    }

    @Test
    fun `travel is capped at 0_3 of the glyph height`() {
        assertEquals(MaxTravel, glyphMotion(1f, 1f, 0.5f, 1f).travel, 1e-6f)
        assertEquals(-MaxTravel, glyphMotion(-1f, 1f, 0.5f, 1f).travel, 1e-6f)
        assertEquals(0.3f, MaxTravel, 0f)
    }

    @Test
    fun `outgoing glyph is gone by p 0_6 and incoming starts at p 0_4`() {
        assertEquals(1f, outgoingFade(0f), 1e-6f)
        assertEquals(0f, outgoingFade(0.6f), 1e-6f)
        assertEquals(0f, outgoingFade(1f), 1e-6f)
        assertEquals(0f, incomingFade(0f), 1e-6f)
        assertEquals(0f, incomingFade(0.4f), 1e-6f)
        assertEquals(1f, incomingFade(1f), 1e-6f)
    }

    @Test
    fun `presence scales alpha`() {
        assertEquals(0.5f, glyphMotion(0f, 1f, 1f, 0.5f).alpha, 1e-6f)
    }

    @Test
    fun `a retargeted glyph that is still displaced is blurred even at p 0`() {
        val m = glyphMotion(d = 0.8f, fade = 1f, p = 0f, presence = 1f)
        assertEquals(0.8f, m.blur, 1e-6f)
    }

    @Test
    fun `blur peaks mid transition`() {
        assertEquals(1f, glyphMotion(0f, 1f, 0.5f, 1f).blur, 1e-6f)
    }

    @Test
    fun `appreciation tone follows the sign`() {
        assertEquals(Tone.Positive, appreciationTone(Fiat(12.5, CurrencyCode.USD)))
        assertEquals(Tone.Negative, appreciationTone(Fiat(-3.0, CurrencyCode.USD)))
        assertEquals(Tone.Neutral, appreciationTone(Fiat(0.0, CurrencyCode.USD)))
        assertEquals(Tone.Neutral, appreciationTone(null))
    }
}
