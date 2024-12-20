package xyz.flipchat.app.features.home

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.flipchat.app.R
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.util.Router
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val codeController: CodeController,
    private val chatsController: ChatsController,
    private val profileController: ProfileController,
    private val userManager: UserManager,
    val router: Router,
    val resources: ResourceHelper,
) : ViewModel() {

    val isLoggedIn = userManager.state.map { it.authState }.filterIsInstance<AuthState.LoggedIn>()
        .map { true }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, false)

    init {
        userManager.state
            .mapNotNull { it.authState }
            .filterIsInstance<AuthState.LoggedIn>()
            .distinctUntilChanged()
            .onEach { requestAirdrop() }
            .launchIn(viewModelScope)
    }

    fun onAppOpen() {
        getUpdatedUserFlags()
        openStream()
        requestAirdrop()
        updateChats()
    }

    private fun getUpdatedUserFlags() {
        viewModelScope.launch {
            profileController.getUserFlags()
        }
    }

    private fun requestAirdrop() {
        if (userManager.authState is AuthState.LoggedIn) {
            viewModelScope.launch {
                codeController.fetchBalance()
                    .onFailure { it.printStackTrace() }
                    .onSuccess { codeController.requestAirdrop() }
            }
        }
    }

    private fun updateChats() {
        viewModelScope.launch {
            if (userManager.authState.canOpenChatStream()) {
                chatsController.updateRooms()
            }
        }
    }

    fun openStream() {
        if (userManager.authState.canOpenChatStream()) {
            chatsController.openEventStream(viewModelScope)
        }
    }

    fun closeStream() {
        chatsController.closeEventStream()
    }

    fun handleLoginEntropy(entropy: String, onSwitchAccounts: () -> Unit, onCancel: () -> Unit) {
        if (entropy != userManager.entropy) {
            BottomBarManager.showMessage(
                BottomBarManager.BottomBarMessage(
                    title = resources.getString(R.string.subtitle_logoutAndLoginConfirmation),
                    positiveText = resources.getString(R.string.action_logIn),
                    tertiaryText = resources.getString(R.string.action_cancel),
                    isDismissible = false,
                    onPositive = onSwitchAccounts,
                    onTertiary = onCancel,
                )
            )
        }
    }

    fun logout(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.logout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }

    fun deleteAccount(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.deleteAndLogout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }

    override fun onCleared() {
        super.onCleared()
        closeStream()
    }
}