package com.flipcash.app.tokens.internal.explainer

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals

class ExplainerToneTest {
    @Test
    fun `appreciation tone follows the sign`() {
        assertEquals(Tone.Positive, appreciationTone(Fiat(12.5, CurrencyCode.USD)))
        assertEquals(Tone.Negative, appreciationTone(Fiat(-3.0, CurrencyCode.USD)))
        assertEquals(Tone.Neutral, appreciationTone(Fiat(0.0, CurrencyCode.USD)))
        assertEquals(Tone.Neutral, appreciationTone(null))
    }
}
