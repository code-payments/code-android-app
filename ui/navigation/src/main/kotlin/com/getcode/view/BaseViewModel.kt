package com.getcode.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class BaseViewModel<ViewState : Any, Event : Any>(
    initialState: ViewState,
    private val updateStateForEvent: (Event) -> (ViewState.() -> ViewState),
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    private val _stateFlow: MutableStateFlow<ViewState> = MutableStateFlow(initialState)
    val stateFlow: StateFlow<ViewState> = _stateFlow.asStateFlow()

    fun dispatchEvent(event: Event) {
        setState(updateStateForEvent(event))
        viewModelScope.launch(defaultDispatcher) {
            _eventFlow.emit(event)
        }
    }

    suspend fun dispatchEvent(context: CoroutineContext, event: Event) {
        withContext(context) {
            dispatchEvent(event)
        }
    }

    /**
     * Show a confirm button's success state, hold it long enough to be read, then move on.
     *
     * The hold starts when [success] is dispatched, not when the work began: by the time a screen
     * has a result to act on, the checkmark has not been drawn yet, and navigating on the same
     * frame swaps it away before anyone sees it. That is what separates this from
     * `Result.onSuccessWithDelay`, which enforces a minimum duration for the *operation* and so
     * holds for nothing once the operation itself is slow.
     *
     * [then] runs on [viewModelScope], so it is cancelled with the ViewModel rather than pushing a
     * route the user has already left.
     */
    protected fun dispatchSuccessThen(
        success: Event,
        hold: Duration = SuccessHoldDuration,
        then: suspend () -> Unit,
    ): Job = viewModelScope.launch {
        dispatchEvent(success)
        delay(hold)
        then()
    }

    // Events are dispatched from multiple threads — the UI thread and background flows on
    // defaultDispatcher — so this must be an atomic compare-and-set, not a plain
    // read-modify-write. A non-atomic assignment lets concurrent reducers derive from the same
    // snapshot and clobber each other, silently dropping one event's state change.
    private fun setState(update: ViewState.() -> ViewState) {
        _stateFlow.update(update)
    }
}

/**
 * How long a confirm button holds its checkmark before the screen it sits on moves on.
 *
 * Matches what the username-claim, name-entry and photo-selection screens already wait; the
 * longer holds elsewhere (verification's second, the deposit screen's two) are their own beat, not
 * this one.
 */
val SuccessHoldDuration = 500.milliseconds

data class LoadingSuccessState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val error: Boolean = false,
) {
    sealed interface State {
        data object Idle: State
        data object Loading : State
        data object Success : State
        data object Error : State
    }

    val state: State = when {
        loading -> State.Loading
        success -> State.Success
        error -> State.Error
        else -> State.Idle
    }

    val isIdle: Boolean = state is State.Idle
}
