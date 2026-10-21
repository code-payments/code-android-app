package com.flipcash.app.currencycreator.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrencyCreatorProcessingStateTest {

    private fun reduce(event: CurrencyCreatorViewModel.Event) =
        CurrencyCreatorViewModel.updateStateForEvent(event)(CurrencyCreatorViewModel.State())

    @Test
    fun `UpdateProcessingState sets error`() {
        val state = reduce(CurrencyCreatorViewModel.Event.UpdateProcessingState(error = true))
        assertTrue(state.processingState.error)
        assertFalse(state.processingState.loading)
        assertFalse(state.processingState.success)
    }

    @Test
    fun `UpdateProcessingState sets loading and success`() {
        val loading = reduce(CurrencyCreatorViewModel.Event.UpdateProcessingState(loading = true))
        assertTrue(loading.processingState.loading)
        val success = reduce(CurrencyCreatorViewModel.Event.UpdateProcessingState(success = true))
        assertTrue(success.processingState.success)
        assertEquals(false, success.processingState.error)
    }
}
