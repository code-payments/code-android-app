package xyz.flipchat.app.features.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.controllers.ChatsController
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val chatsController: ChatsController,
) : ViewModel() {

    fun logout(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.logout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }
}