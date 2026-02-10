package com.flipcash.app.tokens.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.Period
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.collections.map

@HiltViewModel
class TokenInfoViewModel @Inject constructor(
    private val accountController: AccountController,
    private val tokenController: TokenController,
    private val exchange: Exchange,
    private val shareController: ShareSheetController,
    private val resources: ResourceHelper,
    features: FeatureFlagController,
) : BaseViewModel2<TokenInfoViewModel.State, TokenInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val mint: Mint? = null,
        val token: Token? = null,
        val marketCap: Fiat? = null,
        val cashReservesEnabled: Boolean = false,
        val marketCapChartEnabled: Boolean = false,
        val balance: LocalFiat = LocalFiat.Zero,
        val showAppreciation: Boolean = false,
        val showTransactionHistory: Boolean = false,
        val appreciation: LocalFiat? = null,
        val descriptionExpanded: Boolean = false,
        val reservesBalance: LocalFiat = LocalFiat.Zero,
        val historicalMarketCapData: Map<Period, Loadable<List<MarketCapPoint>>> = emptyMap(),
        val selectedPeriod: Period = Period.All,
    ) {
        val canSell: Boolean
            get() = balance.underlyingTokenAmount.valueNonZero()

        val hasReserves: Boolean
            get() = cashReservesEnabled && reservesBalance.underlyingTokenAmount.valueNonZero()

        val isCashReserve: Boolean
            get() = token?.address == Mint.usdf
    }

    sealed interface Event {
        data class CashReservesEnabled(val enabled: Boolean) : Event
        data class MarketCapChartEnabled(val enabled: Boolean) : Event
        data class OnMintProvided(val mint: Mint, val forNeededFunds: Boolean = false) : Event
        data class OnTokenChanged(val token: Token, val forNeededFunds: Boolean = false) : Event
        data class OnMarketCapChanged(val mcap: Fiat?) : Event
        data class LoadHistoricalDataForPeriod(val period: Period, val evict: Boolean = false) : Event

        data class OnHistoricalMarketCapDataUpdated(
            val period: Period,
            val data: Loadable<List<MarketCapPoint>>
        ) : Event

        data class OnMarketCapPeriodSelected(val period: Period) : Event
        data class OnBalanceUpdated(val balance: LocalFiat) : Event
        data class OnReservesUpdated(val balance: LocalFiat) : Event
        data class OnAppreciatedEnabled(val enabled: Boolean) : Event
        data class OnTransactionHistoryEnabled(val enabled: Boolean): Event
        data class OnAppreciationUpdated(val amount: LocalFiat?) : Event
        data class ExpandDescription(val expand: Boolean) : Event
        data object Share : Event
        data class OpenPurchaseMethods(val forNeededFunds: Boolean = false) : Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object ConnectPhantomWallet : Event
        data object Exit : Event
    }

    init {
        features.observe(FeatureFlag.CashReservesEnabled)
            .onEach {
                dispatchEvent(Event.CashReservesEnabled(it))
            }.launchIn(viewModelScope)

        features.observe(FeatureFlag.MarketCapChart)
            .onEach {
                dispatchEvent(Event.MarketCapChartEnabled(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMintProvided>()
            .onEach {
                tokenController.getTokenMetadata(it.mint)
                    .onSuccess { result ->
                        dispatchEvent(Event.OnTokenChanged(result.token, it.forNeededFunds))
                    }.onFailure {
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
            .flatMapLatest { (token, _) ->
                combine(
                    tokenController.balanceForToken(token.address),
                    tokenController.appreciationForToken(token.address),
                    exchange.observeBalanceRate(),
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

        eventFlow
            .filterIsInstance<Event.OnTokenChanged>()
            .distinctUntilChanged()
            .filter { it.forNeededFunds }
            .onEach {
                 dispatchEvent(Event.OpenPurchaseMethods(true))
            }.launchIn(viewModelScope)

        combine(
            tokenController.observeReservesBalance(),
            exchange.observeBalanceRate(),
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
            )
        }.onEach {
            dispatchEvent(Event.OnReservesUpdated(it))
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

                tokenController.getHistoricalMarketCapData(
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
            .onEach {
                val hasAccount = accountController.hasAccountFor(it)
                dispatchEvent(Event.OnAppreciatedEnabled(hasAccount))
                dispatchEvent(Event.OnTransactionHistoryEnabled(hasAccount))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBalanceUpdated>()
            .map { _ ->
                val token = stateFlow.value.token ?: return@map null
                token.marketCap()
            }
            .flatMapLatest { mcap ->
                combine(
                    flowOf(mcap),
                    exchange.observeBalanceRate(),
                ) { usdMcap, rate ->
                    usdMcap?.convertingTo(rate)
                }
            }.distinctUntilChanged().onEach {
                dispatchEvent(Event.OnMarketCapChanged(it))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMarketCapChanged>()
            .map { it.mcap }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.LoadHistoricalDataForPeriod(stateFlow.value.selectedPeriod)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OpenPurchaseMethods>()
            .onEach {
                BottomBarManager.showMessage(
                    bottomBarMessage = BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_selectPurchaseMethod),
                        type = BottomBarManager.BottomBarMessageType.DEFAULT,
                        actions = buildList {
                            if (stateFlow.value.hasReserves) {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(
                                            R.string.action_useCashReservesWithBalance,
                                            stateFlow.value.reservesBalance.formatted()
                                        ),
                                        onClick = {
                                            dispatchEvent(
                                                Event.OpenScreen(
                                                    AppRoute.Token.SwapTransact(
                                                        purpose = TokenSwapPurpose.Buy(stateFlow.value.token!!.address),
                                                        forNeededFunds = it.forNeededFunds
                                                    )
                                                )
                                            )
                                        }
                                    )
                                )
                            }

                            add(
                                BottomBarAction(
                                    text = buildAnnotatedString {
                                        append(resources.getString(R.string.label_solanaUsdc))
                                        appendInlineContent("[icon]", alternateText = " ")
                                        append(resources.getString(R.string.label_phantom))
                                    },
                                    inlineContentMap = mapOf(
                                        "[icon]" to InlineTextContent(
                                            placeholder = Placeholder(
                                                width = 25.sp,
                                                height = 14.sp,
                                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                            ),
                                            children = {
                                                val buttonColors = ButtonState.Filled.colors()
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Image(
                                                        modifier = Modifier.padding(
                                                            start = CodeTheme.dimens.staticGrid.x1 + 2.dp,
                                                            end = CodeTheme.dimens.staticGrid.x1
                                                        ),
                                                        painter = painterResource(R.drawable.ic_phantom_wallet),
                                                        colorFilter = ColorFilter.tint(
                                                            buttonColors.contentColor(
                                                                true
                                                            ).value
                                                        ),
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        )
                                    ),
                                    onClick = {
                                        // start the onramp flow here since we skip the provider list
                                        OnRampFlowTracker.start(
                                            AppRoute.Token.Info(stateFlow.value.token!!.address)
                                        )

                                        dispatchEvent(Event.ConnectPhantomWallet)
                                    }
                                )
                            )

                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_dismiss),
                                    style = BottomBarManager.BottomBarButtonStyle.Text,
                                )
                            )
                        },
                        showCancel = false,
                        showScrim = true,
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Share>()
            .mapNotNull { stateFlow.value.token }
            .map { Shareable.TokenInfo(it) }
            .onEach { shareController.present(it) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.CashReservesEnabled -> { state -> state.copy(cashReservesEnabled = event.enabled) }
                is Event.MarketCapChartEnabled -> { state -> state.copy(marketCapChartEnabled = event.enabled) }
                is Event.OnMintProvided -> { state -> state.copy(mint = event.mint) }
                is Event.OnTokenChanged -> { state -> state.copy(token = event.token) }
                is Event.OnMarketCapChanged -> { state -> state.copy(marketCap = event.mcap) }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
                is Event.OnReservesUpdated -> { state -> state.copy(reservesBalance = event.balance) }
                is Event.OnAppreciationUpdated -> { state -> state.copy(appreciation = event.amount) }
                is Event.ExpandDescription -> { state -> state.copy(descriptionExpanded = event.expand) }
                is Event.OnHistoricalMarketCapDataUpdated -> { state ->
                    val historicalData = state.historicalMarketCapData.toMutableMap()
                    historicalData[event.period] = event.data
                    state.copy(historicalMarketCapData = historicalData.toMap())
                }

                is Event.OnAppreciatedEnabled -> { state -> state.copy(showAppreciation = event.enabled) }
                is Event.OnTransactionHistoryEnabled -> { state -> state.copy(showTransactionHistory = event.enabled) }

                is Event.OnMarketCapPeriodSelected -> { state -> state.copy(selectedPeriod = event.period) }
                is Event.OpenScreen -> { state -> state }
                is Event.ConnectPhantomWallet -> { state -> state }
                is Event.OpenPurchaseMethods -> { state -> state }
                is Event.LoadHistoricalDataForPeriod -> { state -> state }
                is Event.Share -> { state -> state }
                is Event.Exit -> { state -> state }
            }
        }
    }
}