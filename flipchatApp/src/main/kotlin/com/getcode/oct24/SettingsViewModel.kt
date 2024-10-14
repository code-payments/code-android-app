package com.getcode.oct24

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.getcode.oct24.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    fun logout(activity: Activity, onComplete: () -> Unit) {
        authManager.logout(activity, onComplete = onComplete)
    }
}