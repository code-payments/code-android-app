package com.getcode.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> =
    staticCompositionLocalOf { NavigatorNull() }

class NavigatorNull : CodeNavigator {
    override val lastItem: Screen? = null
    override val isVisible: Boolean = false
    override val progress: Float = 0f

    override var screensNavigator: Navigator? = null

    override fun show(screen: Screen) = Unit

    override fun hide() = Unit

    override fun push(item: Screen) = Unit

    override fun push(items: List<Screen>) = Unit

    override fun replace(item: Screen) = Unit

    override fun replaceAll(item: Screen, inSheet: Boolean) = Unit

    override fun replaceAll(items: List<Screen>, inSheet: Boolean) = Unit

    override fun pop(): Boolean = false

    override fun popAll() = Unit

    override fun popUntil(predicate: (Screen) -> Boolean): Boolean = false

}

interface CodeNavigator {
    val lastItem: Screen?
    val isVisible: Boolean
    val progress: Float
    var screensNavigator: Navigator?
    fun show(screen: Screen)
    fun hide()
    infix fun push(item: Screen)

    infix fun push(items: List<Screen>)

    infix fun replace(item: Screen)

    fun replaceAll(item: Screen, inSheet: Boolean = true)

    fun replaceAll(items: List<Screen>, inSheet: Boolean = true)

    fun pop(): Boolean

    fun popAll()

    infix fun popUntil(predicate: (Screen) -> Boolean): Boolean
}

class CombinedNavigator(
    val sheetNavigator: BottomSheetNavigator
) : CodeNavigator {
    override var screensNavigator: Navigator? = null


    override val lastItem: Screen?
        get() = if (isVisible) sheetNavigator.lastItemOrNull else screensNavigator?.lastItemOrNull

    override val isVisible: Boolean
        get() = sheetNavigator.isVisible

    override val progress: Float
        get() = sheetNavigator.progress


    override fun show(screen: Screen) {
        sheetNavigator.show(screen)
    }

    override fun hide() {
        sheetNavigator.hide()
    }

    override fun push(item: Screen) {
        if (isVisible) {
            sheetNavigator.push(item)
        } else {
            screensNavigator?.push(item)
        }
    }

    override fun push(items: List<Screen>) {
        if (isVisible) {
            sheetNavigator.push(items)
        } else {
            screensNavigator?.push(items)
        }
    }

    override fun replace(item: Screen) {
        sheetNavigator.replace(item)
    }

    override fun replaceAll(item: Screen, inSheet: Boolean) {
        if (isVisible && inSheet) {
            sheetNavigator.replaceAll(item)
        } else {
            if (isVisible) {
                hide()
            }
            screensNavigator?.replaceAll(item)
        }
    }

    override fun replaceAll(items: List<Screen>, inSheet: Boolean) {
        if (isVisible && inSheet) {
            sheetNavigator.replaceAll(items)
        } else {
            if (isVisible) {
                hide()
            }
            screensNavigator?.replaceAll(items)
        }
    }

    override fun pop(): Boolean {
        return if (isVisible) {
            sheetNavigator.pop()
        } else {
            screensNavigator?.pop() ?: false
        }
    }

    override fun popAll() {
        if (isVisible) {
            sheetNavigator.popAll()
        } else {
            screensNavigator?.popAll()
        }
    }

    override fun popUntil(predicate: (Screen) -> Boolean): Boolean {
        return if (isVisible) {
            sheetNavigator.popUntil(predicate)
        } else {
            screensNavigator?.popUntil(predicate) ?: false
        }
    }

    @Composable
    fun saveableState(
        key: String,
        content: @Composable () -> Unit
    ) {
        if (isVisible) {
            sheetNavigator.saveableState(key, content = content)
        } else {
            val screen by remember(lastItem) {
                derivedStateOf {
                    lastItem ?: error("Navigator has no screen")
                }
            }
            screensNavigator?.saveableState(key = key, screen = screen, content)
        }
    }
}