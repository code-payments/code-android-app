package com.getcode.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.model.ScreenModel
import com.getcode.App
import io.reactivex.rxjava3.disposables.CompositeDisposable
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

@Deprecated(
    message = "Replaced With BaseViewModel2",
    replaceWith = ReplaceWith("Use BaseViewModel2", "com.getcode.view.BaseViewModel2"))
abstract class BaseViewModel : ViewModel() {
    private val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }

    open fun setIsLoading(isLoading: Boolean) {}

    fun getString(resId: Int): String = App.getInstance().getString(resId)
}

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