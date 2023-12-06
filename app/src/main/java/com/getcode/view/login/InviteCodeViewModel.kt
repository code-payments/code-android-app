package com.getcode.view.login

import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.codeinc.gen.invite.v2.InviteService
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.network.repository.InviteRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.urlEncode
import com.getcode.utils.ErrorUtils
import com.getcode.view.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class InviteCodeUiModel(
    val phoneNumber: String = "",
    val inviteCode: String = "",
    val isContinue: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class InviteCodeViewModel @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val phoneRepository: PhoneRepository,
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(InviteCodeUiModel())

    fun reset() {
        uiFlow.update { InviteCodeUiModel() }
    }

    fun onSubmit(navController: NavController?) {
        if (!uiFlow.value.isContinue) return
        TopBarManager.setMessageShown()
        CoroutineScope(Dispatchers.IO).launch {
            performVerify(navController)
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        uiFlow.update { it.copy(phoneNumber = phoneNumber) }
    }

    fun setInviteCodeInput(inviteCode: String) {
        uiFlow.update { uiModel ->
            uiModel.copy(
                inviteCode = inviteCode,
                isContinue = inviteCode.trim().length in 3..32)
        }
    }

    private fun performVerify(navController: NavController?) {
        viewModelScope.launch {
        inviteRepository.redeem(uiFlow.value.phoneNumber, uiFlow.value.inviteCode)
            .flatMap {
                if (
                    it == InviteService.InvitePhoneNumberResponse.Result.OK ||
                    it == InviteService.InvitePhoneNumberResponse.Result.ALREADY_INVITED
                ) {
                    phoneRepository.sendVerificationCode(uiFlow.value.phoneNumber)
                } else {
                    Flowable.error(InvalidInviteCodeException())
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { uiFlow.update { it.copy(isLoading = true) } }
            .doOnComplete { uiFlow.update { it.copy(isLoading = false) } }
            .doOnError { uiFlow.update { it.copy(isLoading = false) } }
            .subscribe({
                navController?.navigate(
                    route = LoginSections.PHONE_CONFIRM.route
                        .replace(
                            "{${ARG_PHONE_NUMBER}}",
                            uiFlow.value.phoneNumber.urlEncode()
                        )
                        .replace(
                            "{${ARG_SIGN_IN_ENTROPY_B64}}",
                            ""
                        )
                        .replace(
                            "{${ARG_IS_PHONE_LINKING}}",
                            false.toString()
                        )
                        .replace(
                            "{${ARG_IS_NEW_ACCOUNT}}",
                            true.toString()
                        )
                )
            }, {
                if (it is InvalidInviteCodeException) {
                    TopBarManager.showMessage(getNotInvitedError())
                } else {
                    ErrorUtils.handleError(it)
                }
            })
        }
    }

    private fun getNotInvitedError() = TopBarManager.TopBarMessage(
        getString(R.string.error_title_invalidInviteCode),
        getString(R.string.error_description_invalidInviteCode),
        TopBarManager.TopBarMessageType.ERROR,
        getString(R.string.action_ok)
    )

    class InvalidInviteCodeException : Exception()
}