package com.flipcash.app.lab.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.models.ContactMethod
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
    private val contactController: ContactVerificationController,
    private val resources: ResourceHelper,
): ViewModel() {

    val isLoggedIn = userManager
        .state.map { it.authState }
        .filterIsInstance<AuthState.LoggedInWithUser>()
        .map { true }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly , initialValue = false)

    fun unlinkEmail() = viewModelScope.launch {
        val email = userManager.profile?.verifiedEmailAddress
        if (email == null) {
            BottomBarManager.showError(
                title = "Unable to Unlink Email Address",
                message = "No email is linked to your profile"
            )
            return@launch
        }
        val method = ContactMethod.Email(email)
        contactController.unlink(method)
            .onFailure {
                BottomBarManager.showError(
                    title = "Something Went Wrong",
                    message = "Unable to unlink your email. Please try again"
                )
            }.onSuccess {
                BottomBarManager.showMessage(
                    title = "Success",
                    subtitle = "Your email has been unlinked",
                    actions = listOf(
                        BottomBarAction(text = resources.getString(android.R.string.ok))
                    ),
                    type = BottomBarManager.BottomBarMessageType.SUCCESS,
                )
            }
    }

    fun unlinkPhone() = viewModelScope.launch {
        val phone = userManager.profile?.verifiedPhoneNumber
        if (phone == null) {
            BottomBarManager.showError(
                title = "Unable to Unlink Phone Number",
                message = "No phone number is linked to your profile"
            )
            return@launch
        }
        val method = ContactMethod.Phone(phone)
        contactController.unlink(method)
            .onFailure {
                BottomBarManager.showError(
                    title = "Something Went Wrong",
                    message = "Unable to unlink your phone number. Please try again"
                )
            }.onSuccess {
                BottomBarManager.showMessage(
                    title = "Success",
                    subtitle = "Your phone number has been unlinked",
                    actions = listOf(
                        BottomBarAction(text = resources.getString(android.R.string.ok))
                    ),
                    type = BottomBarManager.BottomBarMessageType.SUCCESS,
                )
            }
    }
}