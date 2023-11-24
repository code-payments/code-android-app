package com.getcode.view.main.connectivity

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.network.client.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NetworkConnectionViewModel @Inject constructor(
    connectionRepository: ConnectionRepository,
    client: Client
) : ViewModel(), DefaultLifecycleObserver {

    val connectionStatus = MutableStateFlow(ConnectionState(ConnectionStatus.CONNECTED))

    init {
        viewModelScope.launch(Dispatchers.Default) {
            connectionRepository.connectionFlow.collect { connection ->
                connection?.let {
                    if (it) {
                        connectionStatus.value = ConnectionState(ConnectionStatus.CONNECTED)
                        client.pollOnce()
                    } else {
                        connectionStatus.value = ConnectionState(ConnectionStatus.DISCONNECTED)
                    }
                }
            }
        }
    }
}
