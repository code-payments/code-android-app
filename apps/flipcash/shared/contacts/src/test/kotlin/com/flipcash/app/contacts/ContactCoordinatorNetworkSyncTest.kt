@file:OptIn(ExperimentalCoroutinesApi::class)

package com.flipcash.app.contacts

import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.SignalStrength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class ContactCoordinatorNetworkSyncTest {

    private class FakeNetworkObserver : NetworkConnectivityListener {
        val _state = MutableStateFlow(NetworkState.Default)
        override val state: StateFlow<NetworkState> = _state.asStateFlow()
        override val isConnected: Boolean get() = _state.value.connected
        override val type: ConnectionType get() = _state.value.type
    }

    @Test
    fun `signal strength changes while connected do not re-trigger sync`() = runTest {
        val networkObserver = FakeNetworkObserver()
        var syncCount = 0

        val cluster = MutableStateFlow<Unit?>(null)

        // Mirrors the FIXED subscription from ContactCoordinator.init
        val job = cluster
            .filterNotNull()
            .flatMapLatest {
                networkObserver.state
                    .map { it.connected }
                    .distinctUntilChanged()
            }
            .filter { it }
            .onEach { syncCount++ }
            .launchIn(this)

        cluster.value = Unit
        advanceUntilIdle()

        // Initial connected
        networkObserver._state.value = NetworkState(true, SignalStrength.Good, ConnectionType.Wifi)
        advanceUntilIdle()
        assertEquals(1, syncCount, "First connected event should trigger sync")

        // Rapid signal/type changes — all still connected
        networkObserver._state.value = NetworkState(true, SignalStrength.Great, ConnectionType.Wifi)
        networkObserver._state.value = NetworkState(true, SignalStrength.Strong, ConnectionType.Wifi)
        networkObserver._state.value = NetworkState(true, SignalStrength.Good, ConnectionType.Cellular)
        networkObserver._state.value = NetworkState(true, SignalStrength.Poor, ConnectionType.Cellular)
        networkObserver._state.value = NetworkState(true, SignalStrength.Great, ConnectionType.Wifi)
        advanceUntilIdle()

        assertEquals(1, syncCount, "Signal strength / type changes should NOT re-trigger sync")

        // Disconnect → reconnect
        networkObserver._state.value = NetworkState(false, SignalStrength.Unknown, ConnectionType.Unknown)
        advanceUntilIdle()
        networkObserver._state.value = NetworkState(true, SignalStrength.Good, ConnectionType.Wifi)
        advanceUntilIdle()

        assertEquals(2, syncCount, "Reconnect after disconnect should trigger sync")

        job.cancel()
    }
}
