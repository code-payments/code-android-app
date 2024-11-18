package xyz.flipchat.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import xyz.flipchat.app.util.Router
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val codeController: CodeController,
    private val chatsController: ChatsController,
    val router: Router
): ViewModel() {

    fun requestAirdrop() {
        viewModelScope.launch {
            codeController.fetchBalance()
                .onFailure { it.printStackTrace() }
                .onSuccess { codeController.requestAirdrop() }
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