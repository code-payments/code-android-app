package com.getcode.network.client

import android.content.Context
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.SessionManager
import com.getcode.network.BalanceController
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

internal const val TAG = "Client"

@Singleton
class Client @Inject constructor(
    @ApplicationContext
    internal val context: Context,
    internal val transactionRepository: TransactionRepository,
    internal val balanceController: BalanceController,
    internal val accountRepository: AccountRepository,
    internal val analyticsManager: AnalyticsManager,
    internal val prefRepository: PrefRepository,
    internal val exchange: Exchange,
    internal val transactionReceiver: TransactionReceiver,
) {

    private val TAG = "PollTimer"
    private val scope = CoroutineScope(Dispatchers.IO)

    private var pollTimer: Timer? = null
    private var lastPoll: Long = 0L

    private fun startPollTimerWhenAuthenticated() {
        Timber.tag(TAG).i("Creating poll timer")
        scope.launch {
            SessionManager.authState.collect {
                if (it.isAuthenticated == true) {
                    Timber.tag(TAG).i("User Authenticated - starting timer")
                    startPollTimer()
                    this.cancel()
                }
            }
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
        balanceController.fetchBalanceSuspend()
        exchange.fetchRatesIfNeeded()
        fetchLimits()
        fetchPrivacyUpgrades()
    }

    fun startTimer() {
        startPollTimerWhenAuthenticated()
    }

    fun pollOnce() {
        scope.launch {
            delay(2000)
            Timber.tag(TAG).i("Poll Once")
            poll()
        }
    }

    fun stopTimer() {
        Timber.tag(TAG).i("Cancelling Poller")
        pollTimer?.cancel()
    }
}