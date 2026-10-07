package com.flipcash.app.userprofile.internal.featuredgroups

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.features.userprofile.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.SetFeaturedGroupsError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** The most groups a profile can feature. The server rejects more, so the picker stops at it. */
internal const val FeaturedGroupsLimit = 10

/**
 * Picks the public groups a profile features, in the order they are picked.
 *
 * The list opens on what the session already holds, so the rows are there before anything loads,
 * and is then re-read: the server is the word on what is featured, and a group the user has left
 * stays on the list so it can still be removed. Joined groups follow, public ones only, from a
 * single read of the group feed.
 */
@HiltViewModel
internal class EditFeaturedGroupsViewModel @Inject constructor(
    private val userManager: UserManager,
    private val chatController: ChatController,
    private val store: FeaturedGroupsStore,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<EditFeaturedGroupsViewModel.State, EditFeaturedGroupsViewModel.Event>(
    initialState = State.seededWith(store.groups.value, resources.getString(R.string.label_linkCard_untitledGroup)),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    enum class LoadState { Loading, Loaded, Failed }

    data class State(
        val queryState: TextFieldState = TextFieldState(),
        val query: String = "",
        val candidates: List<ChatMetadata> = emptyList(),
        /** The picked groups, in the order they were picked. */
        val selection: List<ChatId> = emptyList(),
        /** The selection as the server last reported it, which is what a save has to differ from. */
        val saved: List<ChatId> = emptyList(),
        val loadState: LoadState = LoadState.Loading,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        /** A group's title as the search and the rows read it, which is never blank. */
        val untitled: String = "",
    ) {
        val atLimit: Boolean get() = selection.size >= FeaturedGroupsLimit

        /** At the limit only a picked group can be tapped, so it can still be dropped. */
        fun isEnabled(chatId: ChatId): Boolean = chatId in selection || !atLimit

        /** Not until the featured list has been read, or a save could drop groups never seen. */
        val canSave: Boolean
            get() = loadState == LoadState.Loaded && processingState.isIdle && selection != saved

        /** The candidates the query leaves, matched on title alone, ignoring case and outer spaces. */
        val visibleCandidates: List<ChatMetadata>
            get() {
                val needle = query.trim()
                if (needle.isEmpty()) return candidates
                return candidates.filter { titleOf(it).contains(needle, ignoreCase = true) }
            }

        private fun titleOf(chat: ChatMetadata) =
            chat.title?.trim().takeUnless { it.isNullOrEmpty() } ?: untitled

        companion object {
            fun seededWith(featured: List<ChatMetadata>, untitled: String) = State(
                candidates = featured,
                selection = featured.map { it.chatId },
                saved = featured.map { it.chatId },
                untitled = untitled,
            )
        }
    }

    sealed interface Event {
        data class Toggle(val chatId: ChatId) : Event
        data class OnQueryChanged(val query: String) : Event

        /** The featured list as the server has it: adopted by a selection nobody has touched. */
        data class OnFeaturedRead(val featured: List<ChatId>) : Event
        data class OnCandidatesLoaded(val candidates: List<ChatMetadata>) : Event
        data object OnLoadFailed : Event
        data object Save : Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object OnSaved : Event
    }

    init {
        snapshotFlow { stateFlow.value.queryState.text.toString() }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnQueryChanged(it)) }
            .launchIn(viewModelScope)

        viewModelScope.launch { loadCandidates() }

        eventFlow
            .filterIsInstance<Event.Save>()
            .onEach { save() }
            .launchIn(viewModelScope)
    }

    private suspend fun loadCandidates() {
        val username = userManager.state.value.userProfile?.username
        // No handle means nothing is featured and nothing to read.
        if (!username.isNullOrBlank() && !store.load(username)) {
            dispatchEvent(Event.OnLoadFailed)
            return
        }
        val featured = if (username.isNullOrBlank()) emptyList() else store.groups.value
        dispatchEvent(Event.OnFeaturedRead(featured.map { it.chatId }))

        // A failed read leaves only the featured groups on offer rather than an error.
        val joined = chatController.getGroupChatFeed().getOrNull()?.chats.orEmpty()
        val featuredIds = featured.map { it.chatId }.toSet()
        dispatchEvent(
            Event.OnCandidatesLoaded(
                featured + joined.filter {
                    it.type == ChatType.GROUP && !it.isPrivate && it.chatId !in featuredIds
                }
            )
        )
    }

    private fun save() {
        val state = stateFlow.value
        if (!state.canSave) return
        viewModelScope.launch {
            dispatchEvent(Event.UpdateProcessingState(loading = true))
            chatController.setFeaturedGroups(state.selection)
                .onSuccess { groups ->
                    store.replace(groups)
                    dispatchEvent(Event.UpdateProcessingState(success = true))
                    delay(500.milliseconds)
                    dispatchEvent(Event.OnSaved)
                    dispatchEvent(Event.UpdateProcessingState())
                }
                .onFailure { cause ->
                    dispatchEvent(Event.UpdateProcessingState())
                    // The selection stays so the user can fix it rather than rebuild it.
                    if (cause is SetFeaturedGroupsError.Denied) {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_featuredGroupsPrivate),
                            message = resources.getString(R.string.error_description_featuredGroupsPrivate),
                        )
                    } else {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_featuredGroupsSaveFailed),
                            message = resources.getString(R.string.error_description_featuredGroupsSaveFailed),
                        )
                    }
                }
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Toggle -> { state ->
                    when {
                        event.chatId in state.selection ->
                            state.copy(selection = state.selection - event.chatId)
                        state.atLimit -> state
                        else -> state.copy(selection = state.selection + event.chatId)
                    }
                }

                is Event.OnQueryChanged -> { state -> state.copy(query = event.query) }

                is Event.OnFeaturedRead -> { state ->
                    // A pick made while the read was out stands.
                    val untouched = state.selection == state.saved
                    state.copy(
                        saved = event.featured,
                        selection = if (untouched) event.featured else state.selection,
                    )
                }

                is Event.OnCandidatesLoaded -> { state ->
                    state.copy(candidates = event.candidates, loadState = LoadState.Loaded)
                }

                Event.OnLoadFailed -> { state -> state.copy(loadState = LoadState.Failed) }

                is Event.UpdateProcessingState -> { state ->
                    state.copy(
                        processingState = state.processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                Event.Save,
                Event.OnSaved -> { state -> state }
            }
        }
    }
}
