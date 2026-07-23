package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.funding.PurchaseMethodController

import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.Period
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class TokenInfoViewModel @Inject constructor(
    private val accountController: AccountController,
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val shareController: ShareSheetController,
    private val resources: ResourceHelper,
    private val purchaseMethodController: PurchaseMethodController,
    features: FeatureFlagController,
    dispatchers: DispatcherProvider,
) : BaseViewModel<TokenInfoViewModel.State, TokenInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val mint: Mint? = null,
        val token: Loadable<Token> = Loadable.Loading(),
        val marketCap: Fiat? = null,
        val marketCapChartEnabled: Boolean = false,
        val balance: LocalFiat = LocalFiat.Zero,
        val showAppreciation: Boolean = false,
        val showTransactionHistory: Boolean = false,
        val appreciation: LocalFiat? = null,
        val descriptionExpanded: Boolean = false,
        val historicalMarketCapData: Map<Period, Loadable<List<MarketCapPoint>>> = emptyMap(),
        val selectedPeriod: Period = Period.All,
        val canGiveUsdf: Boolean = false,
        val reservesBalance: LocalFiat = LocalFiat.Zero,
    ) {
        val canSell: Boolean
            get() = balance.underlyingTokenAmount.valueNonZero()

        val hasReserves: Boolean
            get() = reservesBalance.underlyingTokenAmount.valueNonZero()

        val isCashReserve: Boolean
            get() = token.dataOrNull?.address == Mint.usdf
    }

    sealed interface Event {
        data class CanGiveUsdf(val enabled: Boolean): Event
        data class MarketCapChartEnabled(val enabled: Boolean) : Event
        data class OnMintProvided(val mint: Mint, val shortFall: Fiat? = null) : Event
        data class OnTokenChanged(val token: Loadable<Token>, val shortFall: Fiat? = null) : Event
        data class OnMarketCapChanged(val mcap: Fiat?) : Event
        data class LoadHistoricalDataForPeriod(val period: Period, val evict: Boolean = false) : Event

        data class OnHistoricalMarketCapDataUpdated(
            val period: Period,
            val data: Loadable<List<MarketCapPoint>>
        ) : Event

        data class OnMarketCapPeriodSelected(val period: Period) : Event
        data class OnBalanceUpdated(val balance: LocalFiat) : Event
        data class OnReservesBalanceUpdated(val balance: LocalFiat): Event
        data class OnAppreciatedEnabled(val enabled: Boolean) : Event
        data class OnTransactionHistoryEnabled(val enabled: Boolean): Event
        data class OnAppreciationUpdated(val amount: LocalFiat?) : Event
        data class ExpandDescription(val expand: Boolean) : Event
        data object Share : Event
        data class OnBuy(val shortFall: Fiat? = null) : Event
        data object PresentDepositOptions: Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object Exit : Event
    }

    init {
        features.observe(FeatureFlag.GiveUsdf)
            .onEach {
                dispatchEvent(Event.CanGiveUsdf(it))
            }.launchIn(viewModelScope)

        features.observe(FeatureFlag.MarketCapChart)
            .onEach {
                dispatchEvent(Event.MarketCapChartEnabled(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMintProvided>()
            .onEach { dispatchEvent(Event.OnTokenChanged(Loadable.Loading())) }
            .onEach {
                tokenCoordinator.getTokenMetadata(it.mint)
                    .onSuccess { result ->
                        dispatchEvent(Event.OnTokenChanged(Loadable.Loaded(result.token), it.shortFall))
                    }.onFailure { cause ->
                        dispatchEvent(
                            Event.OnTokenChanged(
                                Loadable.Error(
                                    message = resources.getString(R.string.error_description_tokenNotFound),
                                    error = cause
                                )
                            )
                        )
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_tokenNotFound),
                            message = resources.getString(R.string.error_description_tokenNotFound),
                        ) {
                            dispatchEvent(Event.Exit)
                        }
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnTokenChanged>()
            .distinctUntilChanged()
            .filter { it.token.isLoaded() }
            .map { it.token.dataOrNull!! to it.shortFall }
            .flatMapLatest { (token, _) ->
                combine(
                    tokenCoordinator.balanceForToken(token.address),
                    tokenCoordinator.appreciationForToken(token.address),
                    exchange.observePreferredRate(),
                ) { balance, appreciation, rate ->
                    val localizedBalance = LocalFiat(
                        usdf = balance,
                        nativeAmount = balance.convertingTo(rate),
                        mint = token.address,
                    )

                    // USD reserves don't appreciate so we track that as MIN_VALUE internally to avoid confusion
                    // with true zero's.
                    val localizedAppreciation = if (appreciation != Fiat.MIN_VALUE) {
                        LocalFiat(
                            usdf = appreciation,
                            nativeAmount = appreciation.convertingTo(rate),
                            mint = token.address,
                        )
                    } else {
                        null
                    }

                    localizedBalance to localizedAppreciation
                }
            }.onEach { (balance, appreciation) ->
                dispatchEvent(Event.OnBalanceUpdated(balance))
                dispatchEvent(Event.OnAppreciationUpdated(appreciation))
            }
            .launchIn(viewModelScope)

        combine(
        tokenCoordinator.observeReservesBalance(),
            exchange.observePreferredRate(),
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
                mint = Mint.usdf,
            )
        }.onEach {
            dispatchEvent(Event.OnReservesBalanceUpdated(it))
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnTokenChanged>()
            .distinctUntilChanged()
            .map { it.shortFall }
            .filterNotNull()
            .onEach {
                 dispatchEvent(Event.OnBuy(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMarketCapPeriodSelected>()
            .map { it.period }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.LoadHistoricalDataForPeriod(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LoadHistoricalDataForPeriod>()
            .onEach { (period, evictCacheForMint) ->
                val window = when (period) {
                    Period.All -> WindowedRange.AllTime
                    Period.Day -> WindowedRange.LastDay
                    Period.Week -> WindowedRange.LastWeek
                    Period.Month -> WindowedRange.LastMonth
                    Period.Year -> WindowedRange.LastYear
                }
                val mint = stateFlow.value.mint ?: return@onEach
                val currency = stateFlow.value.balance.rate.currency

                dispatchEvent(
                    Event.OnHistoricalMarketCapDataUpdated(
                        period,
                        Loadable.Loading()
                    )
                )

                tokenCoordinator.getHistoricalMarketCapData(
                    mint = mint,
                    currencyCode = currency,
                    windowedRange = window,
                ).map {
                    it.map { point ->
                        MarketCapPoint(
                            point.snapshotAt.toEpochMilliseconds(),
                            point.marketCap
                        )
                    }
                }.onSuccess {
                    dispatchEvent(Event.OnHistoricalMarketCapDataUpdated(period, Loadable.Loaded(it)))
                }.onFailure {
                    dispatchEvent(Event.OnHistoricalMarketCapDataUpdated(period, Loadable.Error(message = "Failed to load data for range", error = it)))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBalanceUpdated>()
            .mapNotNull { stateFlow.value.mint }
            .distinctUntilChanged()
            .flatMapLatest { mint ->
                accountController.observeHasAccountFor(mint)
            }
            .onEach { hasAccount ->
                dispatchEvent(Event.OnAppreciatedEnabled(hasAccount))
                dispatchEvent(Event.OnTransactionHistoryEnabled(hasAccount))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBalanceUpdated>()
            .map { _ ->
                val token = stateFlow.value.token.dataOrNull ?: return@map null
                token.marketCap()
            }
            .flatMapLatest { mcap ->
                combine(
                    flowOf(mcap),
                    exchange.observePreferredRate(),
                ) { usdMcap, rate ->
                    usdMcap?.convertingTo(rate)
                }
            }.distinctUntilChanged().onEach {
                dispatchEvent(Event.OnMarketCapChanged(it))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMarketCapChanged>()
            .mapNotNull { it.mcap }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.LoadHistoricalDataForPeriod(stateFlow.value.selectedPeriod)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBuy>()
            .onEach { event ->
                val mint = stateFlow.value.mint ?: return@onEach
                // Buying requires cash reserves to fund the swap; if there are none,
                // send the user to deposit options first instead of the swap screen.
                // This check is only done if AddMoneyUx FF is enabled.
                val addMoney = features.get(FeatureFlag.AddMoneyUX)
                if (!stateFlow.value.hasReserves && addMoney) {
                    BottomBarManager.showInfo(
                        title = resources.getString(R.string.title_noBalanceYet),
                        message = resources.getString(R.string.description_noBalanceYetToBuy),
                        actions = listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_addMoney)
                            ) {
                                dispatchEvent(Event.PresentDepositOptions)
                            },
                        ),
                        showCancel = true,
                    )
                } else {
                    dispatchEvent(Event.OpenScreen(AppRoute.Token.Swap(
                        purpose = SwapPurpose.Buy(mint),
                        shortfall = event.shortFall,
                    )))
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull {
                val depositFirstUx = features.get(FeatureFlag.AddMoneyUX)
                if (!depositFirstUx) {
                    return@mapNotNull AppRoute.Transfers.Deposit(showOtherOptions = false)
                }

                // popToRoot = false so finishing the deposit returns to this token
                // info screen rather than dismissing the whole sheet.
                purchaseMethodController.presentDepositOptions(popToRoot = false) }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Share>()
            .mapNotNull { stateFlow.value.token.dataOrNull }
            .map { Shareable.TokenInfo(it) }
            .onEach { shareController.present(it) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.MarketCapChartEnabled -> { state -> state.copy(marketCapChartEnabled = event.enabled) }
                is Event.OnMintProvided -> { state -> state.copy(mint = event.mint) }
                is Event.OnTokenChanged -> { state -> state.copy(token = event.token) }
                is Event.OnMarketCapChanged -> { state -> state.copy(marketCap = event.mcap) }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
                is Event.OnReservesBalanceUpdated -> { state -> state.copy(reservesBalance = event.balance) }
                is Event.OnAppreciationUpdated -> { state -> state.copy(appreciation = event.amount) }
                is Event.ExpandDescription -> { state -> state.copy(descriptionExpanded = event.expand) }
                is Event.PresentDepositOptions -> { state -> state }
                is Event.OnHistoricalMarketCapDataUpdated -> { state ->
                    val historicalData = state.historicalMarketCapData.toMutableMap()
                    historicalData[event.period] = event.data
                    state.copy(historicalMarketCapData = historicalData.toMap())
                }

                is Event.OnAppreciatedEnabled -> { state -> state.copy(showAppreciation = event.enabled) }
                is Event.OnTransactionHistoryEnabled -> { state -> state.copy(showTransactionHistory = event.enabled) }

                is Event.OnMarketCapPeriodSelected -> { state -> state.copy(selectedPeriod = event.period) }
                is Event.OpenScreen -> { state -> state }
                is Event.OnBuy -> { state -> state }
                is Event.LoadHistoricalDataForPeriod -> { state -> state }
                is Event.Share -> { state -> state }
                is Event.Exit -> { state -> state }
                is Event.CanGiveUsdf -> { state -> state.copy(canGiveUsdf = event.enabled) }
            }
        }
    }
}