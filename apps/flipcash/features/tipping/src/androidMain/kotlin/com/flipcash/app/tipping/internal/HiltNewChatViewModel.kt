package com.flipcash.app.tipping.internal

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.exchange.Exchange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hilt entry point for the shared [NewChatViewModel]. */
@HiltViewModel
internal class HiltNewChatViewModel @Inject constructor(
    tokenCoordinator: TokenCoordinator,
    exchange: Exchange,
) : NewChatViewModel(
    tokenBalances = tokenCoordinator.tokenBalances,
    preferredRate = exchange.observePreferredRate(),
)
