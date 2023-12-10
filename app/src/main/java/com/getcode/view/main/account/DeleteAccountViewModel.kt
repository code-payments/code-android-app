package com.getcode.view.main.account

import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


@HiltViewModel
class DeleteAccountViewModel @Inject constructor() : BaseViewModel() {
    val requiredPhrase = "Delete"
    val typedText = MutableStateFlow("")

    fun onTextUpdated(text: String) {
        typedText.value = text
    }

    fun isDeletionAllowed() = typedText.value.equals(requiredPhrase, ignoreCase = true)
}