package com.getcode.network

import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.client.TransactionReceiver
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.AccountRepository
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import com.getcode.utils.FormatUtils
import com.getcode.utils.trace
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

data class BalanceDisplay(
    val marketValue: Double = 0.0,
    val formattedValue: String = "",
    val currency: Currency? = null,
)

open class BalanceController @Inject constructor(
    exchange: Exchange,
    networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val transactionReceiver: TransactionReceiver,
    private val getCurrencyFromCode: (CurrencyCode?) -> Currency?,
    private val userManager: UserManager,
    val suffix: (Currency?) -> String,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    fun observeRawBalance(): Flow<Double> = balanceRepository.balanceFlow

    val rawBalance: Double
        get() = balanceRepository.balanceFlow.value

    private val _balanceDisplay = MutableStateFlow<BalanceDisplay?>(null)

    val formattedBalance: StateFlow<BalanceDisplay?>
        get() = _balanceDisplay
            .stateIn(scope, SharingStarted.Eagerly, BalanceDisplay())

    init {
        userManager.state
            .map { it.authState }
            .filterIsInstance<AuthState.LoggedIn>()
            .flatMapLatest { networkObserver.state }
            .map { it.connected }
            .onEach { connected ->
                if (connected) {
                    com.getcode.utils.network.retryable { this.getBalance() }
                }
            }
            .flatMapLatest {
                combine(
                    exchange.observeLocalRate()
                        .flowOn(Dispatchers.IO)
                        .onEach {
                            val display = _balanceDisplay.value ?: BalanceDisplay()
                            _balanceDisplay.value =
                                display.copy(currency = getCurrencyFromCode(it.currency))
                        }
                        .onEach { exchange.fetchRatesIfNeeded() },
                    balanceRepository.balanceFlow,
                ) { rate, balance ->
                    rate to balance.coerceAtLeast(0.0)
                }.map { (rate, balance) ->
                    refreshBalance(balance, rate)
                }
            }.distinctUntilChanged().onEach { (marketValue, amountText) ->
                val display = _balanceDisplay.value ?: BalanceDisplay()
                _balanceDisplay.value =
                    display.copy(marketValue = marketValue, formattedValue = amountText)
            }.launchIn(scope)
    }

    fun setTray(organizer: Organizer, tray: Tray) {
        organizer.set(tray)
        balanceRepository.setBalance(organizer.availableBalance.toKinTruncatingLong().toDouble())
    }

    fun getBalance(): Completable {
        trace("fetchBalance")
        val owner = userManager.keyPair
            ?: return Completable.error(IllegalStateException("Missing Owner"))

        fun getTokenAccountInfos(): Completable {
            return accountRepository.getTokenAccountInfos(owner)
                .flatMapCompletable { infos ->
                    val organizer = userManager.organizer ?: return@flatMapCompletable Completable.error(
                        IllegalStateException("Missing Organizer")
                    )
                    scope.launch {
                        organizer.setAccountInfo(infos)
                        userManager.set(organizer = organizer)
                    }

                    balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
                    transactionReceiver.receiveFromIncomingCompletable(organizer)
                }
                .timeout(15, TimeUnit.SECONDS)
        }

        return getTokenAccountInfos()
            .doOnSubscribe {
                Timber.i("Fetching Balance account info")
            }
            .onErrorResumeNext {
                Timber.i("Error: ${it.javaClass.simpleName} ${it.cause}")
                val organizer = userManager.organizer ?: return@onErrorResumeNext Completable.error(
                    IllegalStateException("Missing Organizer")
                )

                when (it) {
                    is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                        transactionRepository.createAccounts(
                            organizer = organizer
                        ).ignoreElement().concatWith(getTokenAccountInfos())
                    }

                    else -> {
                        Completable.error(it)
                    }
                }
            }
    }


    suspend fun fetchBalance(): Result<Unit> {
        Timber.d("fetching balance")
        val owner = userManager.keyPair
            ?: return Result.failure(IllegalStateException("Missing Owner"))

        try {
            val accountInfoResult = accountRepository.getTokenAccountInfosSuspend(owner)
            accountInfoResult.exceptionOrNull()?.let {
                throw it
            }

            val accountInfo = accountInfoResult.getOrNull().orEmpty()
            val organizer = userManager.organizer
                ?: return Result.failure(IllegalStateException("Missing Organizer"))


            organizer.setAccountInfo(accountInfo)
            userManager.set(organizer = organizer)
            balanceRepository.setBalance(organizer.availableBalance.toKinValueDouble())
            transactionReceiver.receiveFromIncoming(organizer)
            scope.launch {
                transactionRepository.swapIfNeeded(organizer)
            }

            return Result.success(Unit)
        } catch (ex: Exception) {
            Timber.i("Error: ${ex.javaClass.simpleName} ${ex.message}")
            val organizer = userManager.organizer
                ?: return Result.failure(IllegalStateException("Missing Organizer"))

            return suspendCancellableCoroutine { cont ->
                when (ex) {
                    is AccountRepository.FetchAccountInfosException.NotFoundException -> {
                        transactionRepository.createAccounts(
                            organizer = organizer
                        ).doOnError { cont.resume(Result.failure(it)) }
                            .doAfterSuccess { cont.resume(Result.success(Unit)) }
                            .subscribe()
                    }
                    else -> {
                        cont.resume(Result.failure(ex))
                    }
                }
            }
        }
    }

    private fun refreshBalance(balance: Double, rate: Rate): Pair<Double, String> {
        val preferredCurrency = getCurrencyFromCode(rate.currency)
        val fiatValue = FormatUtils.getFiatValue(balance, rate.fx)

        val prefix =
            formatPrefix(preferredCurrency).takeIf { it != preferredCurrency?.code }.orEmpty()

        val amountText = StringBuilder().apply {
            append(prefix)
            append(formatAmount(fiatValue, preferredCurrency))
            val suffix = suffix(preferredCurrency)
            if (suffix.isNotEmpty()) {
                append(" ")
                append(suffix)
            }
        }.toString()

        Timber.d("formatted balance is now $prefix $amountText in ${preferredCurrency?.code}")

        return fiatValue to amountText
    }

    private fun formatPrefix(selectedCurrency: Currency?): String {
        if (selectedCurrency == null) return ""
        return if (!isKin(selectedCurrency)) selectedCurrency.symbol else ""
    }

    private fun isKin(selectedCurrency: Currency): Boolean =
        selectedCurrency.code == com.getcode.model.CurrencyCode.KIN.name

    private fun formatAmount(amount: Double, currency: Currency?): String {
        return if (amount % 1 == 0.0 || currency?.code == com.getcode.model.CurrencyCode.KIN.name) {
            String.format(Locale.getDefault(), "%,.0f", amount)
        } else {
            String.format(Locale.getDefault(), "%,.2f", amount)
        }
    }
}
