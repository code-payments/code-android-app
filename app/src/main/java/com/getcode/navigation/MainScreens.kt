package com.getcode.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.view.main.home.HomeScan
import com.getcode.view.main.home.HomeViewModel

sealed interface MainGraph : Screen {
    fun readResolve(): Any = this
}

data class HomeScreen(val cashLink: String = ""): MainGraph {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val homeViewModel = getViewModel<HomeViewModel>()
        HomeScan(homeViewModel, cashLink)
    }
}