package com.getcode.oct24.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.oct24.network.controllers.ChatsController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatsController: ChatsController,
): ViewModel() {

    fun openStream() {
        chatsController.openEventStream(viewModelScope)
    }

    fun closeStream() {
        chatsController.closeEventStream()
    }

    override fun onCleared() {
        super.onCleared()
        chatsController.closeEventStream()
    }
}