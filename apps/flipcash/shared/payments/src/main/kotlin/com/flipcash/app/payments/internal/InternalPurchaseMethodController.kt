package com.flipcash.app.payments.internal

import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.payments.PurchaseMethodSelection
import com.flipcash.app.payments.PurchaseMethodState
import com.flipcash.app.tokens.core.ReservesBalanceProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalPurchaseMethodController @Inject constructor(
    features: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
    reservesBalanceProvider: ReservesBalanceProvider,
    exchange: Exchange,
    private val resources: ResourceHelper,
) : PurchaseMethodController {

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(PurchaseMethodState())
    override val state: StateFlow<PurchaseMethodState> = _state.asStateFlow()

    private val _selections = MutableSharedFlow<PurchaseMethodSelection>()
    override val selections: Flow<PurchaseMethodSelection> = _selections.asSharedFlow()

    init {
        combine(
            features.observe(FeatureFlag.CoinbaseOnRamp),
            userFlags.resolvedFlags
                .map { it.supportedOnRampProviders.effectiveValue }
                .map { it.contains(OnRampProvider.Coinbase(OnRampType.Virtual)) }
        ) { enabled, available ->
            enabled && available
        }.onEach { coinbaseAvailable ->
            _state.update { it.copy(coinbaseOnRampAvailable = coinbaseAvailable) }
        }.launchIn(scope)

        combine(
            reservesBalanceProvider.observeReservesBalance(),
            exchange.observeBalanceRate(),
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
            )
        }.onEach { reservesBalance ->
            _state.update { it.copy(reservesBalance = reservesBalance) }
        }.launchIn(scope)

        userFlags.resolvedFlags
            .mapNotNull { it.preferredOnRampProvider.effectiveValue }
            .filterIsInstance<OnRampProvider.Defined>()
            .onEach { provider ->
                _state.update { it.copy(preferredProvider = provider) }
            }.launchIn(scope)
    }

    override fun select(method: PurchaseMethod, metadata: PurchaseMethodMetadata) {
        scope.launch {
            _selections.emit(PurchaseMethodSelection(method, metadata))
        }
    }

    override fun present(metadata: PurchaseMethodMetadata) {
        _state.update { it.copy(canUseOtherWallets = metadata.canUseOtherWallets) }
        BottomBarManager.showMessage(
            title = resources.getString(R.string.prompt_title_selectPurchaseMethod),
            actions = purchaseOptions(_state.value, metadata, resources) { method ->
                scope.launch {
                    val selection = PurchaseMethodSelection(method, metadata)
                    delay(300)
                    _selections.emit(selection)
                }
            },
            showCancel = false,
            showScrim = true,
        )
    }
}