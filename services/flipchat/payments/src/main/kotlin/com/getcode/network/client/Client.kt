package com.getcode.network.client

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.getcode.manager.SessionManager
import com.getcode.network.BalanceController
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.MessagingRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.service.AccountService
import com.getcode.network.service.DeviceService
import com.getcode.services.analytics.AnalyticsService
import com.getcode.services.manager.MnemonicManager
import com.getcode.utils.ErrorUtils
import com.getcode.utils.network.NetworkConnectivityListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

internal const val TAG = "Client"

@Singleton
class Client @Inject constructor(
    internal val transactionRepository: TransactionRepository,
    internal val messagingRepository: MessagingRepository,
    internal val balanceController: BalanceController,
    internal val accountRepository: AccountRepository,
    internal val accountService: AccountService,
    internal val analyticsManager: AnalyticsService,
    internal val exchange: Exchange,
    internal val transactionReceiver: TransactionReceiver,
    internal val networkObserver: NetworkConnectivityListener,
    internal val deviceService: DeviceService,
    internal val mnemonicManager: MnemonicManager,
) : LifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var pollTimer: Timer? = null
    private var lastPoll: Long = 0L

    private fun startPollTimerWhenAuthenticated() {
        Timber.tag(TAG).i("Creating poll timer")
        scope.launch {
            SessionManager.authState
                .map { it.isAuthenticated }
                .filterNotNull()
                .filter { it }
                .onEach {
                    Timber.tag(TAG).i("User Authenticated - starting timer")
                    startPollTimer()
                    this.cancel()
                }.launchIn(this)
        }
    }

    private fun startPollTimer() {
        pollTimer?.cancel()
        pollTimer = fixedRateTimer("pollTimer", false, 0, 1000 * 60) {
            scope.launch {
                Timber.tag(TAG).i("Timer Polling")

                val time = System.currentTimeMillis()
                val isPastThrottle = time - lastPoll > 1000 * 30 || lastPoll == 0L

                if (SessionManager.isAuthenticated() == true && isPastThrottle) {
                    poll()
                    lastPoll = time
                }
            }
        }
    }

    private suspend fun poll() {
        if (networkObserver.isConnected) {
            try {
                balanceController.getBalance()
                exchange.fetchRatesIfNeeded()
            } catch (e: Exception) {
                ErrorUtils.handleError(e)
            }
            fetchLimits().andThen(fetchPrivacyUpgrades()).blockingSubscribe()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun startTimer() {
        startPollTimerWhenAuthenticated()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stopTimer() {
        Timber.tag(TAG).i("Cancelling Poller")
        pollTimer?.cancel()
    }
}