package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

sealed interface NamedScreen: Screen {

    val name: String
        @Composable get() = ""

    val hasName: Boolean
        @Composable get() = name.isNotEmpty()
}

abstract class AppScreen: Screen {
    var result =  MutableStateFlow<Any?>(null)

    fun <T> onResult(obj: T) {
        Timber.d("onResult=$obj")
        result.value = obj
    }
}