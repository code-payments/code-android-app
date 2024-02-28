package com.getcode.network

import android.content.Context
import androidx.annotation.WorkerThread
import com.getcode.manager.SessionManager
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.FormatUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

data class BalanceDisplay(
    val marketValue: Double = 0.0,
    val formattedValue: String = "",
)

class BalanceController @Inject constructor(
    private val exchange: Exchange,
    private val networkObserver: NetworkConnectivityListener,
    private val getCurrency: suspend (rates: Map<CurrencyCode, Rate>) -> Currency,
    @ApplicationContext private val context: Context,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val privacyMigration: PrivacyMigration,
    private val transactionReceiver: TransactionReceiver,
    private val getDefaultCountry: () -> String,
    private val suffix: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO // Inject dispatcher for easier testing
) : CoroutineScope by CoroutineScope(ioDispatcher) {

    private val _balanceDisplay = MutableStateFlow<BalanceDisplay?>(null)
    val formattedBalance: StateFlow<BalanceDisplay?> = _balanceDisplay.asStateFlow()

    init {
        observeRatesAndBalance()
    }

    private fun observeRatesAndBalance() {
        combine(
            exchange.observeRates()
                .distinctUntilChanged()
                .map { getCurrency(it) }
                .flowOn(ioDispatcher)
                .mapNotNull { CurrencyCode.tryValueOf(it.code) }
                .mapNotNull { exchange.rateFor(it) },
            balanceRepository.balanceFlow,
            networkObserver.state
        ) { rate, balance, _ ->
            rate to balance
        }.map { (rate, balance) ->
            refreshBalance(balance, rate.fx)
        }.distinctUntilChanged().onEach { (marketValue, amountText) ->
            val display = BalanceDisplay(marketValue = marketValue, formattedValue = amountText)
            _balanceDisplay.value = display
        }.launchIn(this)
    }

    @WorkerThread
    suspend fun fetchBalance() = withContext(ioDispatcher) {
        if (!SessionManager.isAuthenticated()) {
            Timber.d("FetchBalance - Not authenticated")
            return@withContext
        }

        val owner = SessionManager.getKeyPair() ?: throw IllegalStateException("Missing Owner")
        try {
            val accountInfo = accountRepository.getTokenAccountInfos(owner).await()
            val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")

            organizer.setAccountInfo(accountInfo)
            balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
            transactionReceiver.receiveFromIncoming(organizer)
        } catch (ex: Exception) {
            handleFetchBalanceException(ex)
        }
    }

    private suspend fun handleFetchBalanceException(ex: Exception) {
        val organizer = SessionManager.getOrganizer() ?: throw IllegalStateException("Missing Organizer")
        when (ex) {
            is AccountRepository.FetchAccountInfosException.MigrationRequiredException -> {
                privacyMigration.migrateToPrivacy(
                    context = context,
                    amountToMigrate = ex.accountInfo.balance,
                    organizer = organizer
                )
            }
            is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                transactionRepository.createAccounts(organizer)
            }
            else -> throw ex
        }
    }

    private fun refreshBalance(balance: Double, rate: Double): Pair<Double, String> {
        val fiatValue = FormatUtils.getFiatValue(balance, rate)
        val locale = Locale.getDefault().let { Locale(it.language, getDefaultCountry()) }
        val fiatValueFormatted = FormatUtils.formatCurrency(fiatValue, locale)
        val amountText = "$fiatValueFormatted ${suffix()}"

        return fiatValue to amountText
    }
}
