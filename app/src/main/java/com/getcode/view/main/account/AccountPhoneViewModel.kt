package com.getcode.view.main.account

import com.codeinc.gen.user.v1.IdentityService
import com.getcode.manager.SessionManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.IdentityRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.util.PhoneUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.makeE164
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
    private val phoneRepository: PhoneRepository,
    private val phoneUtils: PhoneUtils,
    resources: ResourceHelper,
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(AccountPhoneUiModel())

    fun init() {
        phoneRepository.getAssociatedPhoneNumberLocal()
            .subscribe {
                uiFlow.value = AccountPhoneUiModel(
                    isLinked = it.isLinked,
                    phoneNumber = it.phoneNumber,
                    phoneNumberFormatted = phoneUtils.formatNumber(it.phoneNumber)
                )
            }
    }

    fun unlinkPhone() {
        val keyPair = SessionManager.authState.value?.keyPair ?: return
        val phoneNumber = uiFlow.value.phoneNumber
            ?.filter { it.isDigit() }?.makeE164()  ?: return

        identityRepository.unlinkAccount(keyPair, phoneNumber).subscribe { result ->
            if (result == IdentityService.UnlinkAccountResponse.Result.OK)
                phoneRepository.phoneLinked.value = false
            uiFlow.value = AccountPhoneUiModel(false, null)
        }
    }
}