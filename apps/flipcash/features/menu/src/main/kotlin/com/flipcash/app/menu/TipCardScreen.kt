package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.menu.internal.tipcard.TipCardScreenContent
import com.flipcash.app.menu.internal.tipcard.TipCardScreenViewModel
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
fun TipCardScreen() {
    val viewModel = hiltViewModel<TipCardScreenViewModel>()
    val navigator = LocalCodeNavigator.current

    TipCardScreenContent(viewModel = viewModel, onClose = { navigator.pop() })
}
