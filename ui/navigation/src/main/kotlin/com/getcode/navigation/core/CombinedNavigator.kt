package com.getcode.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.AppScreen
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CombinedNavigator(
    var sheetNavigator: BottomSheetNavigator
) : CodeNavigator, CoroutineScope by CoroutineScope(Dispatchers.Main) {
    override var screensNavigator: Navigator? = null
    override var tabsNavigator: TabNavigator? = null

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
        trace(message = "opening ${screen::class.java.simpleName} in a sheet")
        sheetNavigator.show(screen)
    }

    override fun show(items: List<Screen>) {
        trace(message = "opening ${items.joinToString { it::class.java.simpleName }} in a sheet")
        if (items.isEmpty()) return
        sheetNavigator.show(items)
    }

    override fun hide() {
        trace(message = "closing sheet")
        sheetNavigator.hide()
    }

    override fun <T> hideWithResult(result: T) {
        if (tabsNavigator != null) {
            val prev = (tabsNavigator?.current as? ChildNavTab)?.childNav?.lastItem as? AppScreen
            hide()
            prev?.onResult(result)
            return
        }

        with(sheetNavigator) {
            var prev = if (size < 2) null else items[items.size - 2] as? AppScreen
            if (prev == null) {
                // grab last screen from base
                prev = screensNavigator?.let {
                    with(it) {
                        items.lastOrNull() as? AppScreen
                    }
                }
            }
            prev?.onResult(result)
            hide()
        }
    }

    override fun push(item: Screen, delay: Long) {
        trace(message = "navigating to ${item::class.java.simpleName}")
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
        trace(message = "navigating to ${items.joinToString { it::class.java.simpleName }}")
        if (isVisible) {
            sheetNavigator.push(items)
        } else {
            screensNavigator?.push(items)
        }
    }

    override fun replace(item: Screen) {
        sheetNavigator.replace(item)
    }

    override fun replaceAll(item: Screen) {
        replaceAll(listOf(item))
    }

    override fun replaceAll(items: List<Screen>) {
        trace(message = "replacing all in back stack with ${items.joinToString { it::class.java.simpleName }}")
        val modalScreens = items.filterIsInstance<ModalScreen>()
        val otherScreens = items.filterNot { it is ModalScreen }
        screensNavigator?.replaceAll(otherScreens)
        if (modalScreens.isNotEmpty()) {
            sheetNavigator.replaceAll(modalScreens)
        }
    }

    override fun isAtRoot(): Boolean {
        return if (isVisible) {
            sheetNavigator.items.count() == 1
        } else {
            screensNavigator?.items?.count() == 1
        }
    }

    override fun pop(): Boolean {
        trace(message = "popping from back stack")
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
            if (tabsNavigator != null) {
                with (tabsNavigator!!) {
                    val prev = (current as? ChildNavTab)?.childNav?.lastItem as? AppScreen
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
    }

    override fun popAll() {
        trace(message = "popping all from back stack")
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
            screensNavigator?.popUntil(predicate) == true
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

    private fun trace(message: String) {
        trace(tag = "Navigator", message = message, type = TraceType.Navigation)
    }
}