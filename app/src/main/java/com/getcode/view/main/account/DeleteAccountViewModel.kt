package com.getcode.view.main.account

import android.app.Activity
import com.getcode.manager.AuthManager
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authManager: AuthManager
) : BaseViewModel() {
    val requiredPhrase = "Delete"
    val typedText = MutableStateFlow("")

    fun onTextUpdated(text: String) {
        typedText.value = text
    }

    fun isDeletionAllowed() = typedText.value.equals(requiredPhrase, ignoreCase = true)

    fun onConfirmDelete(activity: Activity) {
        authManager.deleteAndLogout(activity)
    }
}