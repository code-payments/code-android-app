package com.getcode.view.main.account.withdraw

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.getcode.App
import com.getcode.R
import com.getcode.solana.keys.PublicKey
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.WithdrawalArgs
import com.getcode.network.client.*
import com.getcode.utils.ErrorUtils
import com.getcode.vendor.Base58
import com.getcode.view.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

private const val TAG = "AccountWithdrawSummaryViewModel"

data class AccountWithdrawSummaryUiModel(
    val amountFiat: Double = 0.0,
    val amountKin: Kin? = null,
    val amountText: String = "",
    val currencyCode: String = "",
    val currencyResId: Int? = null,
    val currencyRate: Float? = null,
    val resolvedDestination: String = "",
    val isSuccess: Boolean? = null
)

@HiltViewModel
class AccountWithdrawSummaryViewModel @Inject constructor(
    private val client: Client
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(AccountWithdrawSummaryUiModel())

    fun setArguments(n: CodeNavigator, arguments: WithdrawalArgs) {
        val amountFiat = arguments.amountFiat ?: return goBack(n)
        val amountKin = arguments.amountKinQuarks ?: return goBack(n)
        val amountText = arguments.amountText ?: return goBack(n)
        val currencyCode = arguments.currencyCode ?: return goBack(n)
        val currencyResId = arguments.currencyResId ?: return goBack(n)
        val currencyRate = arguments.currencyRate?.toFloat() ?: return goBack(n)
        val resolvedDestination = arguments.resolvedDestination ?: return goBack(n)

        uiFlow.value =
            AccountWithdrawSummaryUiModel(
                amountFiat,
                Kin(amountKin),
                amountText,
                currencyCode,
                currencyResId,
                currencyRate,
                resolvedDestination
            )
    }

    fun onSubmit(
        navigator: CodeNavigator,
        arguments: WithdrawalArgs,
    ) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = App.getInstance().getString(R.string.prompt_title_confirmWithdrawal),
                subtitle = App.getInstance().getString(R.string.prompt_description_confirmWithdrawal),
                positiveText = App.getInstance().getString(R.string.action_withdrawKin),
                negativeText = App.getInstance().getString(R.string.action_cancel),
                onPositive = { startWithdraw(navigator) }
            ))
    }

    private fun goBack(navigator: CodeNavigator) {
        navigator.popAll()
    }
    
    @SuppressLint("CheckResult")
    private fun startWithdraw(navigator: CodeNavigator) {
        val uiModel = uiFlow.value
        val currencyRate = uiModel.currencyRate?.toDouble() ?: return
        val kin = uiModel.amountKin ?: return
        val currencyCode = CurrencyCode.tryValueOf(uiModel.currencyCode) ?: return

        val amount = KinAmount(
            kin = kin.toKinTruncating(),
            fiat = uiModel.amountFiat,
            rate = Rate(
                fx = currencyRate,
                currency = currencyCode
            )
        )

        val organizer = SessionManager.getOrganizer() ?: return
        val destination = PublicKey(Base58.decode(uiModel.resolvedDestination).toList())

        client.withdrawExternally(App.getInstance(), amount, organizer, destination)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        getString(R.string.success_title_withdrawalComplete),
                        getString(R.string.success_description_withdrawalComplete),
                        TopBarManager.TopBarMessageType.NOTIFICATION
                    )
                )

                navigator.replaceAll(HomeScreen(), inSheet = false)

                uiFlow.value = uiFlow.value.copy(isSuccess = true)
            }, {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        getString(R.string.error_title_failedWithdrawal),
                        getString(R.string.error_description_failedWithdrawal)
                    )
                )
                ErrorUtils.handleError(it)
            })
    }
}