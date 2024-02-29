package com.getcode.view.main.getKin

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.KinAmount
import com.getcode.model.Limit
import com.getcode.model.Rate
import com.getcode.network.client.Client
import com.getcode.network.client.linkAdditionalAccount
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.AccountType
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import com.getcode.utils.makeE164
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.main.giveKin.AmountAnimatedInputUiModel
import com.getcode.view.main.giveKin.AmountUiModel
import com.getcode.view.main.giveKin.BaseAmountCurrencyViewModel
import com.getcode.view.main.giveKin.CurrencyUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BuyKinViewModel @Inject constructor(
    client: Client,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    localeHelper: LocaleHelper,
    currencyUtils: CurrencyUtils,
    networkObserver: NetworkConnectivityListener,
    resources: ResourceHelper,
    private val transactionRepository: TransactionRepository,
    private val phoneRepository: PhoneRepository,
): BaseAmountCurrencyViewModel(
    client,
    prefsRepository,
    exchange,
    balanceRepository,
    localeHelper,
    currencyUtils,
    resources,
    networkObserver
) {
    data class State(
        val currencyModel: CurrencyUiModel = CurrencyUiModel(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val amountModel: AmountUiModel = AmountUiModel(),
        val continueEnabled: Boolean = false,
        val relationshipEstablished: Boolean = false,
    )

    val state = MutableStateFlow(State())

    init {
        init()

        viewModelScope.launch {
            establishSwapRelationship()
        }
    }

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
       state.update {
           it.copy(currencyModel = currencyUiModel)
       }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        state.update {
            it.copy(amountModel = amountUiModel)
        }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        state.update {
            it.copy(amountAnimatedModel = amountAnimatedInputUiModel)
        }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        return state.value.currencyModel.copy()
    }

    override fun getAmountUiModel(): AmountUiModel {
        return state.value.amountModel.copy()
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return state.value.amountAnimatedModel.copy()
    }

    override fun reset() {
        numberInputHelper.reset()
        onAmountChanged(true)
        state.update {
            it.copy(continueEnabled = false)
        }
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        state.update {
            it.copy(continueEnabled = numberInputHelper.amount > 0.0 && BuildConfig.KADO_API_KEY.isNotEmpty())
        }
    }

    private suspend fun establishSwapRelationship() {
        val organizer = SessionManager.getOrganizer() ?: return
        if (organizer.info(AccountType.Swap) != null) {
            Timber.d("USDC deposit account established already.")
            state.update {
                it.copy(relationshipEstablished = true)
            }
            return
        }

        client.linkAdditionalAccount(
            owner = organizer.ownerKeyPair,
            linkedAccount = organizer.swapKeyPair
        ).onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_buy_kin_too_large),
                resources.getString(R.string.error_description_usdc_deposit_failure)
            )
        }.onSuccess {
            state.update { it.copy(relationshipEstablished = true) }
        }
    }

    private val supportedCurrencies = listOf(
        CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.CAD, CurrencyCode.GBP, CurrencyCode.MXN,
        CurrencyCode.COP, CurrencyCode.INR, CurrencyCode.CHF, CurrencyCode.AUD, CurrencyCode.ARS,
        CurrencyCode.BRL, CurrencyCode.CLP, CurrencyCode.JPY, CurrencyCode.KRW, CurrencyCode.PEN,
        CurrencyCode.PHP, CurrencyCode.SGD, CurrencyCode.TRY, CurrencyCode.UYU, CurrencyCode.TWD,
        CurrencyCode.VND, CurrencyCode.CRC, CurrencyCode.SEK, CurrencyCode.PLN, CurrencyCode.DKK,
        CurrencyCode.NOK, CurrencyCode.NZD
    )

    private fun buildKadoUrl(amount: KinAmount, rate: Rate): Uri? {
        val apiKey = BuildConfig.KADO_API_KEY
        if (apiKey.isEmpty()) {
            return null
        }

        return Uri.Builder()
            .scheme("https")
            .authority("app.kado.money")
            .appendQueryParameter("apiKey", apiKey)
            .appendQueryParameter("onPayAmount", amount.fiat.toString())
            .appendQueryParameter("onPayCurrency", rate.currency.name.uppercase())
            .appendQueryParameter("onRevCurrency", "USDC")
            .appendQueryParameter("mode", "minimal")
            .appendQueryParameter("network", "SOLANA")
            .appendQueryParameter("fiatMethodList", "debit_only")
            .appendQueryParameter("phone", phoneRepository.phoneNumber.makeE164())
            .appendQueryParameter("onToAddress", SessionManager.getOrganizer()?.swapDepositAddress)
            .build()
    }

    private val checkMinimumMet: (amount: KinAmount, rate: Rate) -> Boolean = { amount, rate ->
        val threshold = transactionRepository.buyLimitFor(rate.currency) ?: Limit.Zero
        val isUnderMinimum = amount.fiat < threshold.min
        if (isUnderMinimum) {
            val formatted = FormatUtils.formatCurrency(threshold.min, rate.currency)

            TopBarManager.showMessage(
                resources.getString(R.string.error_title_buy_kin_too_small),
                resources.getString(R.string.error_description_buy_kin_too_small, formatted)
            )
        }
        !isUnderMinimum
    }

    private val checkUnderMax: (amount: KinAmount, rate: Rate) -> Boolean = { amount, rate ->
        val threshold = transactionRepository.buyLimitFor(rate.currency) ?: Limit.Zero
        val isOverLimit = amount.fiat > threshold.max
        if (isOverLimit) {
            val formatted = FormatUtils.formatCurrency(threshold.max, rate.currency)
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_buy_kin_too_large),
                resources.getString(R.string.error_description_buy_kin_too_large, formatted)
            )
        }

        !isOverLimit
    }

    fun initiatePurchase(): String? {
        val uiState = state.value
        val currencySymbol = uiState.currencyModel
            .currencies.firstOrNull { uiState.currencyModel.selectedCurrencyCode == it.code }
            ?.let { CurrencyCode.tryValueOf(it.code) }
            ?.takeIf { supportedCurrencies.contains(it) }
            ?: CurrencyCode.USD

        val rate = exchange.rateFor(currencySymbol) ?: exchange.rateForUsd()!!

        val kadoAmount = Fiat.fromString(
            currencySymbol,
            uiState.amountAnimatedModel.amountData.amount
        )?.let { KinAmount.fromFiatAmount(fiat = it, rate = rate) } ?: return null

        if (!checkMinimumMet(kadoAmount, rate)) {
            return null
        }

        if (!checkUnderMax(kadoAmount, rate)) {
            return null
        }

        val kadoUrl = buildKadoUrl(kadoAmount, rate)
        Timber.d("resulting url=$kadoUrl")
        return kadoUrl?.toString()
    }
}