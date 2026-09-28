package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.libs.emojis.reactions.EmojiCatalog
import com.getcode.libs.emojis.reactions.EmojiCatalogLoader
import com.getcode.libs.emojis.reactions.EmojiDrawability
import com.getcode.libs.emojis.reactions.EmojiPickerModel
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * The full emoji picker's search and sections. Scoped to the picker's own nav entry
 * (`ChatStep.ReactionPicker`), so it lives exactly as long as the sheet and starts empty each time
 * it opens. Choosing an emoji is a chat action and stays on `ChatViewModel.Event.ToggleReaction`.
 */
@HiltViewModel
class EmojiPickerViewModel internal constructor(
    private val dispatchers: DispatcherProvider,
    private val emojiCatalogLoader: EmojiCatalogLoader,
    private val recentReactionsStore: RecentReactionsStore,
    // A seam for tests: Robolectric's Paint reports no emoji glyphs, which would empty every section.
    private val glyphProbe: EmojiDrawability.GlyphProbe,
) : BaseViewModel<EmojiPickerViewModel.State, EmojiPickerViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    @Inject
    constructor(
        dispatchers: DispatcherProvider,
        emojiCatalogLoader: EmojiCatalogLoader,
        recentReactionsStore: RecentReactionsStore,
    ) : this(dispatchers, emojiCatalogLoader, recentReactionsStore, EmojiDrawability.PaintGlyphProbe)

    data class State(
        val searchFieldState: TextFieldState = TextFieldState(),
        /** The sections for the search text; stale for a moment while a new search's load is in flight. */
        val sections: List<EmojiPickerModel.Section> = emptyList(),
        /** False until the first load lands, which is what the sheet's spinner waits on. */
        val loaded: Boolean = false,
        /** What a long-press on an emoji offers; see [EmojiPickerModel.toneOptions]. */
        val tones: Map<String, List<String>> = emptyMap(),
    )

    sealed interface Event {
        /** Internal: the sections for the settled search text finished loading. */
        data class SectionsLoaded(
            val sections: List<EmojiPickerModel.Section>,
            val tones: Map<String, List<String>>,
        ) : Event
    }

    private class CatalogInputs(
        val catalog: EmojiCatalog,
        val undrawable: Set<String>,
        val tones: Map<String, List<String>>,
    )

    /**
     * The catalog with what this device can't draw and the tone choices, worked out once per sheet:
     * the glyph probe over all 3,944 entries is the costly part, and its answer can't change while
     * the sheet is open.
     */
    private val catalogInputs = viewModelScope.async(dispatchers.Default, start = CoroutineStart.LAZY) {
        val catalog = emojiCatalogLoader.load()
        val undrawable = catalog.entries
            .map { it.emoji }
            .filterNot { EmojiDrawability.isDrawable(it, glyphProbe) }
            .toSet()
        CatalogInputs(catalog, undrawable, EmojiPickerModel.toneOptions(catalog, undrawable))
    }

    init {
        // mapLatest drops a load the reader has already typed past.
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        snapshotFlow { stateFlow.value.searchFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(150)
            .mapLatest { query -> sections(query) }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    /**
     * The sections for [query] (recents row + one section per catalog category, or a single
     * search-results section — see [EmojiPickerModel.sections]), built on the default dispatcher
     * so a keystroke's filtering stays off the main thread.
     */
    private suspend fun sections(query: String): Event.SectionsLoaded = withContext(dispatchers.Default) {
        val inputs = catalogInputs.await()
        val recents = recentReactionsStore.rank(undrawable = inputs.undrawable)
        Event.SectionsLoaded(
            sections = EmojiPickerModel.sections(
                catalog = inputs.catalog,
                undrawable = inputs.undrawable,
                recents = recents,
                query = query,
            ),
            tones = inputs.tones,
        )
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.SectionsLoaded -> { state ->
                    state.copy(sections = event.sections, tones = event.tones, loaded = true)
                }
            }
        }
    }
}
