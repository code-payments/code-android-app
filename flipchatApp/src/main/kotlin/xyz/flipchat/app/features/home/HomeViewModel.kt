package xyz.flipchat.app.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.flipchat.app.util.Router
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val codeController: CodeController,
    private val chatsController: ChatsController,
    private val userManager: UserManager,
    val router: Router,
): ViewModel() {

    init {
        userManager.state
            .mapNotNull { it.userId }
            .distinctUntilChanged()
            .onEach { requestAirdrop() }
            .launchIn(viewModelScope)
    }

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
        closeStream()
    }
}