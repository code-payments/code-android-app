package com.getcode.navigation.core

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import timber.log.Timber

// Note: If Timber is not used elsewhere, consider if it's needed or replace with another logging approach.

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> = compositionLocalOf { NavigatorNull() }

interface CodeNavigator {
    val lastItem: Screen?
    val lastModalItem: Screen?
    val sheetStackRoot: Screen?
    val lastEvent: StackEvent
    val isVisible: Boolean
    val progress: Float
    var screensNavigator: Navigator?

    fun show(screen: Screen) {}
    fun hide() {}
    fun <T> hideWithResult(result: T) {}

    fun push(item: Screen) {}
    fun push(items: List<Screen>) {}
    fun replace(item: Screen) {}
    fun replaceAll(item: Screen, inSheet: Boolean = false) {}
    fun replaceAll(items: List<Screen>, inSheet: Boolean = false) {}
    fun pop(): Boolean = false
    fun <T> popWithResult(result: T): Boolean = false
    fun popAll() {}
    fun popUntil(predicate: (Screen) -> Boolean): Boolean = false

    @SuppressLint("ComposableNaming")
    @Composable
    fun saveableState(key: String, screen: Screen? = null, content: @Composable () -> Unit) {
        content()
    }
}

class NavigatorNull : CodeNavigator {
    override var screensNavigator: Navigator? = null
    // Implementations rely on default methods.
}

// Additional utilities or extension functions can be added here as needed.
