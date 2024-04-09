package com.getcode.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import com.getcode.navigation.screens.AppScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class CombinedNavigator(
    var sheetNavigator: BottomSheetNavigator
) : CodeNavigator, CoroutineScope by CoroutineScope(Dispatchers.Main) {
    override var screensNavigator: Navigator? = null

    override val lastItem: Screen?
        get() = if (isVisible) sheetNavigator.lastItemOrNull else screensNavigator?.lastItemOrNull

    override val lastModalItem: Screen?
        get() = sheetNavigator.lastItemOrNull

    override val sheetStackRoot: Screen?
        get() = sheetNavigator.sheetStacks.lastItemOrNull?.first

    override val lastEvent: StackEvent
        get() = if (isVisible) sheetNavigator.lastEvent else screensNavigator?.lastEvent
            ?: StackEvent.Idle

    override val isVisible: Boolean
        get() = sheetNavigator.isVisible

    override val sheetFullyVisible: Boolean
        get() = sheetNavigator.isVisible && sheetNavigator.progress == 1f

    override val progress: Float
        get() = sheetNavigator.progress


    override fun show(screen: Screen) {
        sheetNavigator.show(screen)
    }

    override fun hide() {
        sheetNavigator.hide()
    }

    override fun <T> hideWithResult(result: T) {
        with(sheetNavigator) {
            var prev = if (size < 2) null else items[items.size - 2] as? AppScreen
            if (prev == null) {
                // grab last screen from base
                prev = screensNavigator?.let {
                    with(it) {
                        items.lastOrNull() as AppScreen
                    }
                }
            }
            prev?.onResult(result)
            hide()
        }
    }

    override fun push(item: Screen, delay: Long) {
        launch {
            delay(delay)
            if (isVisible) {
                sheetNavigator.push(item)
            } else {
                screensNavigator?.push(item)
            }
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
        replaceAll(listOf(item), inSheet)
    }

    override fun replaceAll(items: List<Screen>, inSheet: Boolean,) {
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

    override fun <T> popWithResult(result: T): Boolean {
        return if (isVisible) {
            with(sheetNavigator) {
                val prev = if (size < 2) null else items[items.size - 2] as? AppScreen
                prev?.onResult(result)
                pop()
            }
        } else {
            screensNavigator?.let {
                with(it) {
                    val prev = if (size < 2) null else items[items.size - 2] as? AppScreen
                    prev?.onResult(result)
                    pop()
                }
            } ?: false
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
    override fun saveableState(
        key: String,
        screen: Screen?,
        content: @Composable () -> Unit
    ) {
        if (isVisible) {
            sheetNavigator.saveableState(key, screen = screen, content = content)
        } else {
            val lastScreen by remember(screen) {
                derivedStateOf {
                    screen ?: error("Navigator has no screen")
                }
            }
            screensNavigator?.saveableState(key = key, screen = lastScreen, content = content)
        }
    }
}