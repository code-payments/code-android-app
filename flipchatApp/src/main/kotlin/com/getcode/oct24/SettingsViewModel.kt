package com.getcode.oct24

import androidx.lifecycle.ViewModel
import com.getcode.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    fun logout(onComplete: () -> Unit) {
        sessionManager.clear()
        onComplete()
    }
}