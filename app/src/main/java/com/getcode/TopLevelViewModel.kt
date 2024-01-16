package com.getcode

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.data.transactions.toUi
import com.getcode.manager.AuthManager
import com.getcode.manager.SessionManager
import com.getcode.network.client.Client
import com.getcode.network.client.historicalTransactions
import com.getcode.network.client.observeTransactions
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel
import com.getcode.view.main.balance.BalanceSheetViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val client: Client,
    networkObserver: NetworkConnectivityListener,
    resources: ResourceHelper,
) : BaseViewModel(resources) {
    init {
        SessionManager.authState
            .map { it.keyPair }
            .filterNotNull()
            .flatMapLatest { networkObserver.state }
            .filter { it.connected }
            .flatMapLatest {
                client.observeTransactions(owner = SessionManager.getKeyPair()!!)
                    .flowOn(Dispatchers.IO)
            }.launchIn(viewModelScope)
    }
    fun logout(activity: Activity, onComplete: () -> Unit = {}) {
        authManager.logout(activity, onComplete)
    }
}
