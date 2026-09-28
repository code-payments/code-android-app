package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.libs.emojis.reactions.EmojiDrawability
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

/**
 * The full emoji picker's search and sections. Scoped to the picker's own nav entry
 * (`ChatStep.ReactionPicker`), so it lives exactly as long as the sheet and starts empty each time
 * it opens. Choosing an emoji is a chat action and stays on `ChatViewModel.Event.ToggleReaction`.
 */
@HiltViewModel
class EmojiPickerViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    private val emojiCatalogLoader: EmojiCatalogLoader,
    private val recentReactionsStore: RecentReactionsStore,
) : BaseViewModel<EmojiPickerViewModel.State, EmojiPickerViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val searchFieldState: TextFieldState = TextFieldState(),
        /** The sections for the search text; stale for a moment while a new search's load is in flight. */
        val sections: List<EmojiPickerModel.Section> = emptyList(),
        /** False until the first load lands, which is what the sheet's spinner waits on. */
        val loaded: Boolean = false,
    )

    sealed interface Event {
        /** Internal: the sections for the settled search text finished loading. */
        data class SectionsLoaded(val sections: List<EmojiPickerModel.Section>) : Event
    }

    init {
        // mapLatest drops a load the reader has already typed past.
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        snapshotFlow { stateFlow.value.searchFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(150)
            .mapLatest { query -> Event.SectionsLoaded(sections(query)) }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    /**
     * The sections for [query] (recents row + one section per catalog category, or a single
     * search-results section — see [EmojiPickerModel.sections]). The loader caches the parsed
     * catalog, so reloading it per query is cheap after the first.
     */
    private suspend fun sections(query: String): List<EmojiPickerModel.Section> {
        val catalog = emojiCatalogLoader.load()
        val undrawable = catalog.entries
            .map { it.emoji }
            .filterNot { EmojiDrawability.isDrawable(it) }
            .toSet()
        val recents = recentReactionsStore.rank(undrawable = undrawable)
        return EmojiPickerModel.sections(catalog = catalog, undrawable = undrawable, recents = recents, query = query)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.SectionsLoaded -> { state -> state.copy(sections = event.sections, loaded = true) }
            }
        }
    }
}
