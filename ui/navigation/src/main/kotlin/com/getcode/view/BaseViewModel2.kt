package com.getcode.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel2<ViewState : Any, Event : Any>(
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

    private fun setState(update: ViewState.() -> ViewState) {
        _stateFlow.value = _stateFlow.value.update()
    }
}