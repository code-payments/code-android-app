package com.getcode.view.main.connectivity

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.animation.core.updateTransition
import androidx.core.content.getSystemService
import com.getcode.view.main.connectivity.legacy.LegacyNetworkReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Singleton


@Singleton
class ConnectionRepository(
       @ApplicationContext
       val context: Context
) {

    val scope = CoroutineScope(Dispatchers.Default)
    val legacyScope = CoroutineScope(Dispatchers.Default)
    val connectionFlow = MutableStateFlow<Boolean?>(null)

    private var receiver: LegacyNetworkReceiver? = null

    init {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService<ConnectivityManager>()?.registerNetworkCallback(
                    NetworkRequest.Builder().build(),
                    wifiCallback)
                updateStateBeforeCallback()
            } else {
                //TODO update this for older devices
                //registerLegacyReceiver()
            }
        }
    }

    //The callback only fires when there is a change, so when we start listening
    //we should update the state to what is accurate.
    fun updateStateBeforeCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var connection: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connection = cm.activeNetwork != null && cm.getNetworkCapabilities(cm.activeNetwork) != null
        } else {
            connection = cm.activeNetworkInfo != null && cm.activeNetworkInfo!!.isConnectedOrConnecting
        }
        connectionFlow.value = connection
    }

    fun registerLegacyReceiver() {
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        receiver = LegacyNetworkReceiver()
        context.applicationContext.registerReceiver(receiver, intentFilter)

        legacyScope.launch {
            receiver?.legacyFlow?.collect {
                connectionFlow.value = it
            }
        }
    }

    fun unregisterLegacyReceiver() {
        context.applicationContext.unregisterReceiver(receiver)
        legacyScope.cancel()
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Timber.d("Connection 1")
            connectionFlow.value = true
        }

        override fun onUnavailable() {
            super.onUnavailable()
            Timber.d("Connection 2")
            connectionFlow.value = false
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Timber.d("Connection 3")
            connectionFlow.value = false
        }
    }

}