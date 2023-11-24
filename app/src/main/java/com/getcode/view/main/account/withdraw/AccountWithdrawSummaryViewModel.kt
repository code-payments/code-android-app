package com.getcode.view.main.account.withdraw

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

    fun setArguments(n: NavController, arguments: Bundle?) {
        arguments ?: return goBack(n)
        val amountFiat =
            arguments.getString(ARG_WITHDRAW_AMOUNT_FIAT)?.toDoubleOrNull() ?: return goBack(n)
        val amountKin =
            arguments.getString(ARG_WITHDRAW_AMOUNT_KIN)?.toLongOrNull() ?: return goBack(n)
        val amountText = arguments.getString(ARG_WITHDRAW_AMOUNT_TEXT) ?: return goBack(n)
        val currencyCode =
            arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_CODE) ?: return goBack(n)
        val currencyResId =
            arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID)?.toIntOrNull()
                ?: return goBack(n)
        val currencyRate =
            arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_RATE)?.toFloatOrNull()
                ?: return goBack(n)
        val resolvedDestination =
            arguments.getString(ARG_WITHDRAW_RESOLVED_DESTINATION)
                ?: return goBack(n)

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
        navController: NavController,
        arguments: Bundle,
    ) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = App.getInstance().getString(R.string.prompt_title_confirmWithdrawal),
                subtitle = App.getInstance().getString(R.string.prompt_description_confirmWithdrawal),
                positiveText = App.getInstance().getString(R.string.action_withdrawKin),
                negativeText = App.getInstance().getString(R.string.action_cancel),
                onPositive = { startWithdraw(navController) }
            ))
    }

    private fun goBack(navController: NavController) {
        navController.popBackStack()
    }
    
    private fun startWithdraw(navController: NavController?) {
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

                navController?.navigate(
                    SheetSections.HOME.route,
                    NavOptions.Builder().setPopUpTo(
                        SheetSections.HOME.route,
                        inclusive = false,
                        saveState = false
                    ).build()
                )

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