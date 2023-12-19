package com.getcode.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> =
    staticCompositionLocalOf { error("CodeNavigator not initialized") }

class CodeNavigator(
    val sheetNavigator: BottomSheetNavigator
) {

    val lastItem: Screen?
        get() = sheetNavigator.lastItemOrNull

    val isVisible: Boolean
        get() = sheetNavigator.isVisible


    suspend fun show(screen: Screen) {
        sheetNavigator.show(screen)
    }

    fun hide() {
        sheetNavigator.hide()
    }

    fun push(item: Screen) {
        sheetNavigator.push(item)
    }

    fun push(items: List<Screen>) {
        sheetNavigator.push(items)
    }

    fun replace(item: Screen) {
        sheetNavigator.replace(item)
    }

    fun replaceAll(item: Screen) {
        sheetNavigator.replaceAll(item)
    }

    fun replaceAll(items: List<Screen>) {
        sheetNavigator.replaceAll(items)
    }

    fun pop(): Boolean {
        return sheetNavigator.pop()
    }

    fun popAll() {
        sheetNavigator.popAll()
    }

    fun popUntil(predicate: (Screen) -> Boolean): Boolean {
        return sheetNavigator.popUntil(predicate) ?: false
    }

    @Composable
    fun saveableState(
        key: String,
        content: @Composable () -> Unit
    ) {
        sheetNavigator.saveableState(key, content = content)
    }
}