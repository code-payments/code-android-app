package com.flipcash.app.tokens.ui

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.exchange.Exchange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hilt entry point for [SelectTokenViewModel]; obtain with `hiltViewModel<HiltSelectTokenViewModel>()`. */
@HiltViewModel
class HiltSelectTokenViewModel @Inject constructor(
    tokenCoordinator: TokenCoordinator,
    exchange: Exchange,
    dispatchers: DispatcherProvider,
) : SelectTokenViewModel(tokenCoordinator, exchange, dispatchers)
