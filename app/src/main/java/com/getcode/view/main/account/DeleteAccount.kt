package com.getcode.view.main.account

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun DeleteCodeAccount(navController: NavController) {
    val viewModel = hiltViewModel<AccountAccessKeyViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()
    val context = LocalContext.current

    Text(text = "Hello")
    SideEffect {
        viewModel.init()
    }
}