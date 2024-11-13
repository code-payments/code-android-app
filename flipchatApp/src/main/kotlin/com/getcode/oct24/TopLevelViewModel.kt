package com.getcode.oct24

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.oct24.network.controllers.CodeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val controller: CodeController,
): ViewModel() {

    fun requestAirdrop() {
        viewModelScope.launch {
//            controller.requestAirdrop()
//                .onFailure {
//                    println(it)
//                }
//                .onSuccess {
//                    println("Airdrop received => ${it.kin} KIN")
//                }
        }
    }
}