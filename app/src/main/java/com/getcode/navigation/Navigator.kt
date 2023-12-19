package com.getcode.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.Stack
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> =
    staticCompositionLocalOf { error("CodeNavigator not initialized") }

class CodeNavigator(
    private val navigator: BottomSheetNavigator
) {

    val lastItem: Screen?
        get() = navigator.lastItemOrNull

    val isVisible: Boolean
        get() = navigator.isVisible

    val progress: Float
        get() = navigator.progress

    var screensNavigator: Navigator? = null


    suspend fun show(screen: Screen) {
        navigator.show(screen)
    }

    suspend fun hide(): Boolean {
        return navigator.hide()
    }

    fun push(item: Screen) {
        screensNavigator?.push(item)
    }

    fun push(items: List<Screen>) {
        screensNavigator?.push(items)
    }

    fun replace(item: Screen) {
        screensNavigator?.replace(item)
    }

    fun replaceAll(item: Screen) {
        screensNavigator?.replaceAll(item)
    }

    fun replaceAll(items: List<Screen>) {
        screensNavigator?.replaceAll(items)
    }

    fun pop(): Boolean {
        return screensNavigator?.pop() ?: false
    }

    fun popAll() {
        screensNavigator?.popAll()
    }

    fun popUntil(predicate: (Screen) -> Boolean): Boolean {
        return screensNavigator?.popUntil(predicate) ?: false
    }
}