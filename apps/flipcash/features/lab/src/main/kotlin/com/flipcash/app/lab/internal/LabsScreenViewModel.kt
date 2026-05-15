package com.flipcash.app.lab.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.lab.R
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.models.UserFlags
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.flipcash.services.models.PhoneVerificationError
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LabsScreenViewModel @Inject constructor(
    private val userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    private val contactController: ContactVerificationController,
    private val resources: ResourceHelper,
) : ViewModel() {

    val isLoggedIn = userManager
        .state.map { it.authState }
        .filterIsInstance<AuthState.LoggedInWithUser>()
        .map { true }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    val isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    fun unlinkEmail() = viewModelScope.launch {
        val email = userManager.profile?.verifiedEmailAddress
        if (email == null) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_failedToUnlinkEmail),
                message = resources.getString(R.string.error_description_failedToUnlinkEmailNonePresent)
            )
            return@launch
        }
        val method = ContactMethod.Email(email)
        contactController.unlink(method)
            .onFailure {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToUnlinkEmail),
                    message = resources.getString(R.string.error_description_failedToUnlinkEmail)
                )
            }.onSuccess {
                BottomBarManager.showSuccess(
                    title = resources.getString(R.string.prompt_title_emailUnlinked),
                    message = resources.getString(R.string.prompt_description_emailUnlinked),
                )
            }
    }

    fun unlinkPhone() = viewModelScope.launch {
        val phone = userManager.profile?.verifiedPhoneNumber
        if (phone == null) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_failedToUnlinkPhone),
                message = resources.getString(R.string.error_description_failedToUnlinkPhoneNonePresent)
            )
            return@launch
        }
        val method = ContactMethod.Phone(phone)
        contactController.unlink(method)
            .onFailure {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToUnlinkPhone),
                    message = resources.getString(R.string.error_description_failedToUnlinkPhone)
                )
            }.onSuccess {
                BottomBarManager.showSuccess(
                    title = resources.getString(R.string.prompt_title_phoneUnlinked),
                    message = resources.getString(R.string.prompt_description_phoneUnlinked),
                )
            }
    }
}