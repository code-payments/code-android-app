package com.flipcash.app.tokens.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.CommunityController
import com.flipcash.services.models.chat.ChatMetadata
import com.getcode.solana.keys.Mint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Every chat `GetCommunity` ranks for one currency, for the View All screen. */
@HiltViewModel
class CommunityChatsViewModel @Inject constructor(
    private val communityController: CommunityController,
    private val tokenCoordinator: TokenCoordinator,
) : ViewModel() {
    data class State(
        val currencyName: String = "",
        val chats: List<ChatMetadata> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var loaded: Mint? = null

    fun load(mint: Mint) {
        if (loaded == mint) return
        loaded = mint
        _state.update { it.copy(currencyName = tokenCoordinator.cachedToken(mint)?.name.orEmpty()) }
        viewModelScope.launch {
            val chats = communityController.getCommunity(mint).topCommunityChats()
            _state.update { it.copy(chats = chats) }
        }
    }
}
