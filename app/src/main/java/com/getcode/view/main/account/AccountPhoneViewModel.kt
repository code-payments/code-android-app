package com.getcode.view.main.account

import com.codeinc.gen.user.v1.IdentityService
import com.getcode.manager.SessionManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.util.PhoneUtils
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


data class AccountPhoneUiModel(
    val isLinked: Boolean = false,
    val phoneNumber: String? = null,
    val phoneNumberFormatted: String? = null
)

@HiltViewModel
class AccountPhoneViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val phoneRepository: PhoneRepository
) :
    BaseViewModel() {
    val uiFlow = MutableStateFlow(AccountPhoneUiModel())

    fun init() {
        phoneRepository.getAssociatedPhoneNumberLocal()
            .subscribe {
                uiFlow.value = AccountPhoneUiModel(
                    isLinked = it.isLinked,
                    phoneNumber = it.phoneNumber,
                    phoneNumberFormatted = PhoneUtils.formatNumber(it.phoneNumber)
                )
            }
    }

    fun unlinkPhone() {
        val keyPair = SessionManager.authState.value?.keyPair ?: return
        val phoneNumber = uiFlow.value.phoneNumber
            ?.filter { it.isDigit() }
            ?.let { com.getcode.utils.PhoneUtils.makeE164(it) } ?: return

        identityRepository.unlinkAccount(keyPair, phoneNumber).subscribe { result ->
            if (result == IdentityService.UnlinkAccountResponse.Result.OK)
                phoneRepository.phoneLinked = false
            uiFlow.value = AccountPhoneUiModel(false, null)
        }
    }
}