package com.flipcash.app.messenger.internal.screens

import com.getcode.libs.emojis.reactions.EmojiPickerModel
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiPickerReducerTest {

    private fun reduce(
        state: EmojiPickerViewModel.State,
        event: EmojiPickerViewModel.Event,
    ): EmojiPickerViewModel.State = EmojiPickerViewModel.updateStateForEvent(event)(state)

    private fun sections(id: String) = listOf(EmojiPickerModel.Section(id = id, title = id, entries = emptyList()))

    @Test
    fun `the picker is loading until its first sections land`() {
        val state = EmojiPickerViewModel.State()

        assertEquals(false, state.loaded)
        assertEquals(emptyList(), state.sections)
    }

    @Test
    fun `loaded sections replace the ones showing and keep the search field`() {
        val first = reduce(EmojiPickerViewModel.State(), EmojiPickerViewModel.Event.SectionsLoaded(sections("all"), tones = emptyMap()))

        val second = reduce(first, EmojiPickerViewModel.Event.SectionsLoaded(sections("search"), tones = emptyMap()))

        assertEquals(sections("search"), second.sections)
        assertEquals(true, second.loaded)
        assertEquals(first.searchFieldState, second.searchFieldState)
    }
}
