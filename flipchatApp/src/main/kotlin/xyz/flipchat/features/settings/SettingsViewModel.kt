package xyz.flipchat.features.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.flipchat.auth.AuthManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    fun logout(activity: Activity, onComplete: () -> Unit) {
        authManager.logout(activity, onComplete = onComplete)
    }
}