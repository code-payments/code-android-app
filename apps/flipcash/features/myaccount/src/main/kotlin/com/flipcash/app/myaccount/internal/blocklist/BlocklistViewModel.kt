package com.flipcash.app.myaccount.internal.blocklist

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.core.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class BlocklistViewModel @Inject constructor(
    private val coordinator: BlocklistCoordinator,
    private val resources: ResourceHelper,
) : BaseViewModel<BlocklistViewModel.State, BlocklistViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    /** The paged, offline-first blocklist, cached across recompositions/config changes. */
    val blocked: Flow<PagingData<BlockedUserProfile>> =
        coordinator.blocklist.cachedIn(viewModelScope)

    /**
     * @param unblocking per-user unblock progress, keyed by user id hex, so an in-flight row can
     * swap its chevron for a spinner.
     */
    data class State(val unblocking: Map<String, LoadingSuccessState> = emptyMap())

    sealed interface Event {
        /** User tapped a row — prompt for confirmation before unblocking. */
        data class UnblockRequested(val user: BlockedUserProfile) : Event

        /** User confirmed the unblock prompt. */
        data class UnblockConfirmed(val user: BlockedUserProfile) : Event

        /** Progress of the unblock request for [userIdHex]. */
        data class UnblockProcessing(
            val userIdHex: String,
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.UnblockRequested>()
            .onEach { event ->
                BottomBarManager.showAlert(
                    title = resources.getString(
                        R.string.prompt_title_unblockUser,
                        event.user.displayName,
                    ),
                    message = resources.getString(R.string.prompt_description_unblockUser),
                    actions = listOf(
                        BottomBarAction(text = resources.getString(R.string.action_unblock)) {
                            dispatchEvent(Event.UnblockConfirmed(event.user))
                        }
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnblockConfirmed>()
            .onEach { event ->
                val key = event.user.userId.hexEncodedString()
                dispatchEvent(Event.UnblockProcessing(key, loading = true))
                coordinator.unblock(event.user.userId)
                    // On success the row leaves the paged list; on failure the spinner reverts.
                    .onSuccess { dispatchEvent(Event.UnblockProcessing(key, success = true)) }
                    .onFailure { dispatchEvent(Event.UnblockProcessing(key, error = true)) }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UnblockProcessing -> { state ->
                    state.copy(
                        unblocking = state.unblocking + (
                                event.userIdHex to LoadingSuccessState(
                                    loading = event.loading,
                                    success = event.success,
                                    error = event.error,
                                ))
                    )
                }

                is Event.UnblockRequested,
                is Event.UnblockConfirmed -> { state -> state }
            }
        }
    }
}
