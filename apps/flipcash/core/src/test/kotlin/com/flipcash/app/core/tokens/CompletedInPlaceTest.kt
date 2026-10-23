package com.flipcash.app.core.tokens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletedInPlaceTest {

    // The Processing screen exits with the bare constructor, so it must not prompt; only
    // SwapCompletionEffects / the funding screen opt in.
    @Test
    fun `results do not count as in place unless the confirm screen says so`() {
        assertFalse(SwapResult.Success().completedInPlace)
        assertFalse(CurrencyCreatorResult.Success().completedInPlace)
        assertTrue(SwapResult.Success(completedInPlace = true).completedInPlace)
        assertTrue(CurrencyCreatorResult.Success(completedInPlace = true).completedInPlace)
    }
}
