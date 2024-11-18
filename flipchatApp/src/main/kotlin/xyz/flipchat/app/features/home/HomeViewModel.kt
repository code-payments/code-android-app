package xyz.flipchat.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
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
                            trace("Airdrop failed: \n ${it.printStackTrace()}", type = TraceType.Silent)
                        }
                        .onSuccess {
                            trace("Airdrop received => ${it.kin} KIN", type = TraceType.Silent)
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