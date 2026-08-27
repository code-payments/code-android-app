package com.flipcash.app.userprofile.internal.name

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The confirm button reads [NameEntryViewModel.State.isChanged], so these cover what arms it:
 * a name that differs from the stored one, and nothing else.
 */
class NameEntryStateTest {

    private val reduce = NameEntryViewModel.updateStateForEvent

    private fun stateWith(saved: String, typed: String = saved): NameEntryViewModel.State {
        val state = reduce(NameEntryViewModel.Event.OnSavedNameLoaded(saved))(
            NameEntryViewModel.State()
        )
        state.nameFieldState.setTextAndPlaceCursorAtEnd(typed)
        return state
    }

    @Test
    fun `an empty field on a fresh account is unchanged`() {
        val state = NameEntryViewModel.State()

        assertEquals("", state.savedName)
        assertFalse(state.isChanged)
        assertFalse(state.hasName)
    }

    @Test
    fun `the seeded name arrives unchanged`() {
        val state = stateWith(saved = "Ada")

        assertTrue(state.hasName)
        assertFalse(state.isChanged)
    }

    @Test
    fun `one character is enough to count as a change`() {
        val state = stateWith(saved = "Ada", typed = "Adam")

        assertTrue(state.isChanged)
    }

    @Test
    fun `typing the stored name back in is not a change`() {
        val state = stateWith(saved = "Ada", typed = "Adam")
        state.nameFieldState.setTextAndPlaceCursorAtEnd("Ada")

        assertFalse(state.isChanged)
    }

    @Test
    fun `a first name on an account without one counts as a change`() {
        val state = stateWith(saved = "", typed = "Ada")

        assertTrue(state.isChanged)
        assertTrue(state.hasName)
    }

    @Test
    fun `clearing the field is a change the confirm button still refuses`() {
        val state = stateWith(saved = "Ada", typed = "")

        assertTrue(state.isChanged)
        assertFalse(state.hasName)
    }

    @Test
    fun `saving a new name moves the baseline`() {
        val state = stateWith(saved = "Ada", typed = "Adam")
        val saved = reduce(NameEntryViewModel.Event.OnSavedNameLoaded("Adam"))(state)

        assertEquals("Adam", saved.savedName)
        assertFalse(saved.isChanged)
    }

    @Test
    fun `discarding leaves the state alone - the field reset is the view model's own`() {
        val state = stateWith(saved = "Ada", typed = "Adam")

        assertEquals(state, reduce(NameEntryViewModel.Event.DiscardChanges)(state))
    }
}
