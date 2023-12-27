package com.getcode.view.main.getKin

import com.getcode.App
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.PrefsBool
import com.getcode.models.Bill
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.requestFirstKinAirdrop
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.NetworkUtils
import com.getcode.view.BaseViewModel
import com.getcode.view.main.home.HomeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class GetKinSheetUiModel(
    val isEligibleGetFirstKinAirdrop: Boolean = false,
    val isEligibleGiveFirstKinAirdrop: Boolean = false,
    val isGetFirstKinAirdropLoading: Boolean = false
)

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    private val prefsRepository: PrefRepository,
    private val balanceController: BalanceController,
    private val client: Client
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(GetKinSheetUiModel())

    fun reset() {
        prefsRepository.getFirstOrDefault(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
            .subscribe { value ->
                uiFlow.update {
                    it.copy(isEligibleGetFirstKinAirdrop = value)
                }
            }
        prefsRepository.getFirstOrDefault(PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP, false)
            .subscribe { value ->
                uiFlow.update {
                    it.copy(isEligibleGiveFirstKinAirdrop = value)
                }
            }
    }

    internal fun requestFirstKinAirdrop(upPress: () -> Unit = {}, homeViewModel: HomeViewModel) {
        if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
            return ErrorUtils.showNetworkError()
        }

        val owner = SessionManager.getKeyPair() ?: return

        uiFlow.update { it.copy(isGetFirstKinAirdropLoading = true) }

        client.requestFirstKinAirdrop(owner)
            .subscribeOn(Schedulers.computation())
            .delay(1, TimeUnit.SECONDS)
            .doOnSuccess {
                upPress()
            }
            .delay(300, TimeUnit.MILLISECONDS)
            .doOnSuccess {
                homeViewModel.showBill(
                    Bill.Cash(
                        kind = Bill.Kind.firstKin,
                        amount = it,
                        didReceive = true
                    ),
                    vibrate = true,
                )

                uiFlow.update { model -> model.copy(
                    isGetFirstKinAirdropLoading = false,
                    isEligibleGetFirstKinAirdrop = false,
                ) }

                prefsRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false) }
            .flatMapCompletable {
                balanceController.fetchBalance()
            }
            .subscribe({}, {
                uiFlow.update {model -> model.copy(isGetFirstKinAirdropLoading = false) }

                if (it is TransactionRepository.AirdropException.AlreadyClaimedException) {
                    prefsRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
                    uiFlow.update {model -> model.copy(isEligibleGetFirstKinAirdrop = false) }
                } else {
                    TopBarManager.showMessage(
                        getString(R.string.title_failed),
                        getString(R.string.error_description_failedToVerifyPhone),
                    )
                }
                ErrorUtils.handleError(it)
            })
    }
}
