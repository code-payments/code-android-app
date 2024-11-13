package com.getcode.oct24.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.oct24.network.controllers.CodeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val codeController: CodeController,
    private val chatsController: ChatsController,
): ViewModel() {

    fun requestAirdrop() {
        viewModelScope.launch {
            codeController.fetchBalance()
                .onFailure { it.printStackTrace() }
                .onSuccess {
                    codeController.requestAirdrop()
                        .onFailure {
                            println("Airdrop failed: \n ${it.printStackTrace()}")
                        }
                        .onSuccess {
                            println("Airdrop received => ${it.kin} KIN")
                        }
                }
        }
    }

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