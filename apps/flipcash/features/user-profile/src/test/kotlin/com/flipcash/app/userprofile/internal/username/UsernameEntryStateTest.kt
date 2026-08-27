package com.flipcash.app.userprofile.internal.username

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Same baseline rule as the name step, over the two cases this screen serves: a first claim, where
 * the stored handle is empty, and a change, where it is not.
 */
class UsernameEntryStateTest {

    private val reduce = UsernameEntryViewModel.updateStateForEvent

    private fun stateWith(saved: String, typed: String = saved): UsernameEntryViewModel.State {
        val state = reduce(UsernameEntryViewModel.Event.OnSavedUsernameLoaded(saved))(
            UsernameEntryViewModel.State()
        )
        state.usernameFieldState.setTextAndPlaceCursorAtEnd(typed)
        return state
    }

    @Test
    fun `an unclaimed handle starts unchanged`() {
        val state = UsernameEntryViewModel.State()

        assertEquals("", state.savedUsername)
        assertFalse(state.isChanged)
        assertFalse(state.hasUsername)
    }

    @Test
    fun `a first claim counts as a change`() {
        val state = stateWith(saved = "", typed = "ada")

        assertTrue(state.isChanged)
        assertTrue(state.hasUsername)
    }

    @Test
    fun `the claimed handle arrives unchanged`() {
        val state = stateWith(saved = "ada")

        assertTrue(state.hasUsername)
        assertFalse(state.isChanged)
    }

    @Test
    fun `one character is enough to count as a change`() {
        val state = stateWith(saved = "ada", typed = "adam")

        assertTrue(state.isChanged)
    }

    @Test
    fun `typing the claimed handle back in is not a change`() {
        val state = stateWith(saved = "ada", typed = "adam")
        state.usernameFieldState.setTextAndPlaceCursorAtEnd("ada")

        assertFalse(state.isChanged)
    }

    @Test
    fun `claiming a new handle moves the baseline`() {
        val state = stateWith(saved = "ada", typed = "adam")
        val saved = reduce(UsernameEntryViewModel.Event.OnSavedUsernameLoaded("adam"))(state)

        assertEquals("adam", saved.savedUsername)
        assertFalse(saved.isChanged)
    }

    @Test
    fun `discarding leaves the state alone - the field reset is the view model's own`() {
        val state = stateWith(saved = "ada", typed = "adam")

        assertEquals(state, reduce(UsernameEntryViewModel.Event.DiscardChanges)(state))
    }

    @Test
    fun `a refresh that moves the baseline leaves the edit in the field`() {
        val editing = stateWith(saved = "mcansh", typed = "mcanshzz")

        // The 60s profile poll can publish a different handle — reducing it must not touch
        // the field, only the baseline the field is measured against.
        val refreshed = reduce(
            UsernameEntryViewModel.Event.OnSavedUsernameLoaded("")
        )(editing)

        assertEquals("mcanshzz", refreshed.usernameFieldState.text.toString())
        assertTrue(refreshed.isChanged)
    }
}
