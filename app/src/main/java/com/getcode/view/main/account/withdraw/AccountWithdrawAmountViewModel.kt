package com.getcode.view.main.account.withdraw

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.WithdrawalAddressScreen
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.view.main.giveKin.AmountAnimatedInputUiModel
import com.getcode.view.main.giveKin.AmountUiModel
import com.getcode.view.main.giveKin.BaseAmountCurrencyViewModel
import com.getcode.view.main.giveKin.CurrencyUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountWithdrawAmountUiModel(
    val currencyModel: CurrencyUiModel = CurrencyUiModel(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val amountModel: AmountUiModel = AmountUiModel(),
    val continueEnabled: Boolean = false,
)

@HiltViewModel
class AccountWithdrawAmountViewModel @Inject constructor(
    client: Client,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    localeHelper: LocaleHelper,
    currencyUtils: CurrencyUtils,
    private val resources: ResourceHelper,
) : BaseAmountCurrencyViewModel(
    client,
    prefsRepository,
    exchange,
    balanceRepository,
    localeHelper,
    currencyUtils,
    resources
) {
    val uiFlow = MutableStateFlow(AccountWithdrawAmountUiModel())

    init {
        init()
        viewModelScope.launch(Dispatchers.Default) {
            client.receiveIfNeeded().subscribe({}, ErrorUtils::handleError)
        }
    }

    fun reset() {
        numberInputHelper.reset()
        onAmountChanged(true)
        viewModelScope.launch {
            uiFlow.update {
                it.copy(continueEnabled = false)
            }
        }
    }

    fun onSubmit(navigator: CodeNavigator) {
        val uiModel = uiFlow.value
        if (uiModel.amountModel.amountKin.toKinValueDouble() > uiModel.amountModel.balanceKin) {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_insuffiecientKin),
                resources.getString(R.string.error_description_insuffiecientKin)
            )
            return
        }
        if (uiModel.amountModel.amountKin.toKinTruncatingLong() == 0L) {
            return
        }

        val currencyCode = uiModel.currencyModel.selectedCurrencyCode ?: return
        val currencyResId = uiModel.currencyModel.selectedCurrencyResId ?: return

        navigator.push(
            WithdrawalAddressScreen(
                uiModel.amountModel.amountDouble,
                uiModel.amountModel.amountKin.quarks,
                uiModel.amountModel.amountText,
                currencyCode,
                currencyResId,
                uiModel.currencyModel.currencies.firstOrNull { it.code == currencyCode }?.rate
            )
        )
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        uiFlow.update {
            // only enable if sufficient balance and non-zero
            it.copy(continueEnabled = !it.amountModel.isInsufficient && numberInputHelper.amount != 0.0)
        }
    }

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
        uiFlow.update { it.copy(currencyModel = currencyUiModel) }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        uiFlow.update { it.copy(amountModel = amountUiModel) }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        uiFlow.update { it.copy(amountAnimatedModel = amountAnimatedInputUiModel) }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        return uiFlow.value.currencyModel
    }

    override fun getAmountUiModel(): AmountUiModel {
        return uiFlow.value.amountModel
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return uiFlow.value.amountAnimatedModel
    }
}
