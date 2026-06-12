package com.flipcash.app.payments.internal

import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.payments.PurchaseMethodSelection
import com.flipcash.app.payments.PurchaseMethodState
import com.flipcash.app.payments.PurchasePurpose
import com.flipcash.app.tokens.core.ReservesBalanceProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
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
    private val userManager: UserManager,
) : PurchaseMethodController {

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(PurchaseMethodState())
    override val state: StateFlow<PurchaseMethodState> = _state.asStateFlow()

    private val _selections = MutableSharedFlow<PurchaseMethodSelection>()
    override val selections: Flow<PurchaseMethodSelection> = _selections.asSharedFlow()

    private val _dismissals = MutableSharedFlow<Unit>()

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
            exchange.observePreferredRate(),
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

        var selected = false

        val title = when (metadata.purpose) {
            PurchasePurpose.Buy -> resources.getString(R.string.prompt_title_selectPurchaseMethod)
            PurchasePurpose.Deposit -> resources.getString(R.string.prompt_title_selectMethod)
        }

        BottomBarManager.showMessage(
            title = title,
            actions = purchaseOptions(_state.value, metadata, resources) { method ->
                selected = true
                scope.launch {
                    val selection = PurchaseMethodSelection(method, metadata)
                    delay(300)
                    _selections.emit(selection)
                }
            },
            showCancel = false,
            showScrim = true,
            onDismiss = {
                if (!selected) {
                    scope.launch { _dismissals.emit(Unit) }
                }
            },
        )
    }

    override suspend fun presentDepositOptions(popToRoot: Boolean): AppRoute? {
        delay(150)
        present(PurchaseMethodMetadata(
            mint = Mint.usdf,
            purpose = PurchasePurpose.Deposit,
            showReserves = false,
            canUseOtherWallets = true)
        )

        val result = merge(
            selections.map { it.method },
            _dismissals.map { null },
        ).first()

        return when (result) {
            PurchaseMethod.CoinbaseOnRamp -> {
                val profile = userManager.profile
                val needsPhone = profile?.verifiedPhoneNumber == null
                val needsEmail = profile?.verifiedEmailAddress == null
                val swapRoute = AppRoute.Token.Swap(
                    purpose = SwapPurpose.Buy(Mint.usdf, FundingSource.Coinbase),
                    popToRoot = popToRoot,
                )
                if (needsPhone || needsEmail) {
                    AppRoute.Verification(
                        origin = swapRoute,
                        includePhone = needsPhone,
                        includeEmail = needsEmail,
                        target = swapRoute,
                    )
                } else {
                    swapRoute
                }
            }
            PurchaseMethod.PhantomWallet -> AppRoute.Token.Swap(
                purpose = SwapPurpose.Buy(Mint.usdf, FundingSource.Phantom),
                popToRoot = popToRoot,
            )
            PurchaseMethod.OtherWallet -> AppRoute.Transfers.Deposit(showOtherOptions = true)
            is PurchaseMethod.CashReserves -> null
            null -> null
        }
    }
}