package com.flipcash.app.payments.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.money.formatted
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
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    override fun present(metadata: PurchaseMethodMetadata) {
        val current = _state.value
        fun select(method: PurchaseMethod) {
            scope.launch { _selections.emit(PurchaseMethodSelection(method, metadata)) }
        }
        BottomBarManager.showMessage(
            title = resources.getString(R.string.prompt_title_selectPurchaseMethod),
            actions = buildList {
                if (current.coinbaseOnRampAvailable) {
                    add(
                        buildButtonAction(
                            prefix = null,
                            suffix = null,
                            iconRes = R.drawable.ic_buy_with_google_pay,
                            width = 150.sp,
                            height = 24.sp,
                            tintIcon = false,
                            onClick = { select(PurchaseMethod.CoinbaseOnRamp) }
                        )
                    )
                }
                if (current.hasReserves) {
                    add(
                        BottomBarAction(
                            text = resources.getString(
                                R.string.action_useCashReservesWithBalance,
                                current.reservesBalance.formatted()
                            ),
                            onClick = { select(PurchaseMethod.CashReserves(current.reservesBalance)) }
                        )
                    )
                }

                add(
                    buildButtonAction(
                        prefix = resources.getString(R.string.label_solanaUsdc),
                        suffix = resources.getString(R.string.label_phantom),
                        iconRes = R.drawable.ic_phantom_wallet,
                        onClick = { select(PurchaseMethod.PhantomWallet) }
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
    }

    private fun buildButtonAction(
        prefix: String?,
        suffix: String?,
        iconRes: Int,
        width: TextUnit = 25.sp,
        height: TextUnit = 14.sp,
        iconPadding: @Composable () -> PaddingValues = {
            PaddingValues(
                start = CodeTheme.dimens.grid.x1 + 2.dp,
                end = CodeTheme.dimens.grid.x1
            )
        },
        tintIcon: Boolean = true,
        onClick: () -> Unit
    ): BottomBarAction {
        return BottomBarAction(
            text = buildAnnotatedString {
                if (prefix != null) {
                    append(prefix)
                }
                appendInlineContent("[icon]", alternateText = " ")
                if (suffix != null) {
                    append(suffix)
                }
            },
            inlineContentMap = mapOf(
                "[icon]" to InlineTextContent(
                    placeholder = Placeholder(
                        width = width,
                        height = height,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    ),
                    children = {
                        val buttonColors = ButtonState.Filled.colors()
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                modifier = Modifier.padding(iconPadding()),
                                painter = painterResource(iconRes),
                                colorFilter = if (tintIcon) {
                                    ColorFilter.tint(buttonColors.contentColor(true).value)
                                } else {
                                    null
                                },
                                contentDescription = null
                            )
                        }
                    }
                )
            ),
            onClick = onClick
        )
    }
}