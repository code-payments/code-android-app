package com.flipcash.app.login.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.user.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val userManager: UserManager,
    private val featureFlags: FeatureFlagController,
    private val contactVerificationController: ContactVerificationController,
) : ViewModel() {

    fun linkPhoneForPayment() {
        val phone = userManager.profile?.verifiedPhoneNumber ?: return
        val enabled = featureFlags.observe(FeatureFlag.PhoneNumberSend).value ||
            userManager.state.value.flags?.enablePhoneNumberSend == true
        if (!enabled) return
        viewModelScope.launch {
            contactVerificationController.linkForPayment(ContactMethod.Phone(phone))
        }
    }
}
