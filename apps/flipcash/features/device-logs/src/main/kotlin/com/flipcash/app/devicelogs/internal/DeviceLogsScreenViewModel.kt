package com.flipcash.app.devicelogs.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.utils.TraceManager
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class DeviceLogsScreenViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
) : BaseViewModel<DeviceLogsScreenViewModel.State, DeviceLogsScreenViewModel.Event>(
    // Subscribing to the SharedFlow replay cache below seeds history naturally —
    // new collectors receive the last N lines as emissions.
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val lines: List<String> = emptyList(),
        val filter: String = "",
        val isPaused: Boolean = false,
        val pausedBacklog: Int = 0,
    ) {
        val visibleLines: List<String>
            get() = if (filter.isBlank()) {
                lines
            } else {
                lines.filter { it.contains(filter, ignoreCase = true) }
            }
    }

    sealed interface Event {
        data class OnLineEmitted(val line: String) : Event
        data class OnFilterChanged(val filter: String) : Event
        data object TogglePause : Event
        data object ClearLogs : Event
        data object ShareLogs : Event
    }

    init {
        TraceManager.logStream
            .onEach { dispatchEvent(Event.OnLineEmitted(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ClearLogs>()
            .onEach { TraceManager.clearLogs() }
            .launchIn(viewModelScope)
    }

    internal companion object {
        const val MAX_BUFFER = 1000

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnLineEmitted -> { state ->
                    val next = if (state.lines.size >= MAX_BUFFER) {
                        state.lines.drop(state.lines.size - MAX_BUFFER + 1) + event.line
                    } else {
                        state.lines + event.line
                    }
                    state.copy(
                        lines = next,
                        pausedBacklog = if (state.isPaused) state.pausedBacklog + 1 else 0,
                    )
                }

                is Event.OnFilterChanged -> { state ->
                    state.copy(filter = event.filter)
                }

                is Event.TogglePause -> { state ->
                    val pausing = !state.isPaused
                    state.copy(
                        isPaused = pausing,
                        pausedBacklog = if (pausing) state.pausedBacklog else 0,
                    )
                }

                is Event.ClearLogs -> { state ->
                    state.copy(
                        lines = emptyList(),
                        pausedBacklog = 0,
                    )
                }

                is Event.ShareLogs -> { state -> state }
            }
        }
    }
}
