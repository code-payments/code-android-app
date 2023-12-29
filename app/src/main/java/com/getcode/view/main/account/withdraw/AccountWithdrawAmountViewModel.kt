package com.getcode.view.main.account.withdraw

import androidx.lifecycle.viewModelScope
import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.WithdrawalAddressScreen
import com.getcode.network.client.Client
import com.getcode.network.client.receiveIfNeeded
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.CurrencyRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.view.main.giveKin.*
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
    val currencySelectorVisible: Boolean = false
)

@HiltViewModel
class AccountWithdrawAmountViewModel @Inject constructor(
    client: Client,
    currencyRepository: CurrencyRepository,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository
) : BaseAmountCurrencyViewModel(client, prefsRepository, currencyRepository, balanceRepository) {
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
                it.copy(
                    currencySelectorVisible = false,
                    currencyModel = it.currencyModel.copy(currencySearchText = ""),
                    continueEnabled = false
                )
            }
        }
    }

    fun onSubmit(navigator: CodeNavigator) {
        val uiModel = uiFlow.value
        if (uiModel.amountModel.amountKin.toKinValueDouble() > uiModel.amountModel.balanceKin) {
            TopBarManager.showMessage(
                App.getInstance().getString(R.string.error_title_insuffiecientKin),
                App.getInstance().getString(R.string.error_description_insuffiecientKin)
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
                uiModel.currencyModel.currenciesMap[currencyCode]?.rate
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

    override fun setCurrencySelectorVisible(isVisible: Boolean) {
        uiFlow.update { it.copy(currencySelectorVisible = isVisible) }
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
