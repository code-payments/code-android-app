package com.getcode.view.main.giveKin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.model.BetaFlags
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.model.PrefsBool
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.replaceParam
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.network.NetworkConnectivityListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GiveKinSheetUiModel(
    val giveRequestsEnabled: Boolean = false,
    val currencyModel: CurrencyUiModel = CurrencyUiModel(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val amountModel: AmountUiModel = AmountUiModel(),
    val continueEnabled: Boolean = false,
)

@HiltViewModel
class GiveKinSheetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    client: Client,
    private val exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    localeHelper: LocaleHelper,
    currencyUtils: CurrencyUtils,
    networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
) : BaseAmountCurrencyViewModel(
    client,
    prefsRepository,
    exchange,
    balanceRepository,
    localeHelper,
    currencyUtils,
    resources,
    networkObserver
) {

    val uiFlow = MutableStateFlow(GiveKinSheetUiModel())

    init {
        init()
        viewModelScope.launch(Dispatchers.IO) {
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }

        prefsRepository.observeOrDefault(PrefsBool.GIVE_REQUESTS_ENABLED, false)
            .filter { BetaFlags.isAvailable(PrefsBool.GIVE_REQUESTS_ENABLED) }
            .map { it }
            .onEach { enabled ->
                uiFlow.update {
                    it.copy(giveRequestsEnabled = enabled)
                }
            }.launchIn(viewModelScope)
    }

    fun reset() {
        numberInputHelper.reset()
        onAmountChanged(true)
        viewModelScope.launch {
            uiFlow.update {
                it.copy(
                    continueEnabled = false
                )
            }
        }
    }

    suspend fun onSubmit(): KinAmount? {
        val uiModel = uiFlow.value
        val checkBalanceLimit: () -> Boolean = {
            val isOverBalance =
                uiModel.amountModel.amountKin.toKinValueDouble() > uiModel.amountModel.balanceKin
            if (isOverBalance) {
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_insuffiecientKin),
                    resources.getString(R.string.error_description_insuffiecientKin)
                )
            }
            isOverBalance
        }
        val checkSendLimit: () -> Boolean = {
            val isOverLimit = uiModel.amountModel.amountDouble > uiModel.amountModel.sendLimit
            if (isOverLimit) {
                val currencySymbol = uiModel.currencyModel
                    .currencies.firstOrNull { uiModel.currencyModel.selectedCurrencyCode == it.code }
                    ?.symbol
                    .orEmpty()
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_giveLimitReached),
                    resources.getString(R.string.error_description_giveLimitReached)
                        .replaceParam(
                            "$currencySymbol${uiModel.amountModel.sendLimit.toInt()}"
                        )
                )
            }
            isOverLimit
        }

        if (checkBalanceLimit() || checkSendLimit()) return null

        val amountFiat = uiModel.amountModel.amountDouble
        val amountKin = uiModel.amountModel.amountKin

        val currencyCode =
            CurrencyCode.tryValueOf(uiModel.currencyModel.selectedCurrencyCode.orEmpty())
                ?: return null

        exchange.fetchRatesIfNeeded()
        val rate = exchange.rateFor(currencyCode) ?: return null

        return KinAmount.fromFiatAmount(amountKin, amountFiat, rate.fx, currencyCode)
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        uiFlow.update {
            val minValue =
                if (it.currencyModel.selectedCurrencyCode == CurrencyCode.KIN.name) 1.0 else 0.01
            it.copy(
                continueEnabled = numberInputHelper.amount >= minValue && !it.amountModel.isInsufficient
            )
        }
    }

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
        uiFlow.update { it.copy(currencyModel = currencyUiModel) }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        uiFlow.update {
            it.copy(amountModel = amountUiModel)
        }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        uiFlow.update { it.copy(amountAnimatedModel = amountAnimatedInputUiModel) }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        return uiFlow.value.currencyModel.copy()
    }

    override fun getAmountUiModel(): AmountUiModel {
        return uiFlow.value.amountModel.copy()
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return uiFlow.value.amountAnimatedModel.copy()
    }
}