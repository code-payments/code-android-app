package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

sealed interface NamedScreen {

    val name: String?
        @Composable get() = null

    val hasName: Boolean
        @Composable get() = !name.isNullOrEmpty()
}

abstract class AppScreen: Screen {
    var result =  MutableStateFlow<Any?>(null)

    fun <T> onResult(obj: T) {
        Timber.d("onResult=$obj")
        result.value = obj
    }
}