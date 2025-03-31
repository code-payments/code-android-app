package com.getcode.navigation.core

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

val LocalCodeNavigator: ProvidableCompositionLocal<CodeNavigator> =
    staticCompositionLocalOf { CodeNavigatorStub }

object NavigatorStub: NavigationLocator {
    override val lastItem: Screen? = null
    override fun push(item: Screen, delay: Long) { }
    override infix fun push(items: List<Screen>) { }

    override fun pop(): Boolean { return false }
    override fun popAll() { }
}

object CodeNavigatorStub : CodeNavigator {
    override val lastItem: Screen? = null
    override val lastModalItem: Screen? = null
    override val sheetStackRoot: Screen? = null
    override val lastEvent: StackEvent = StackEvent.Idle
    override val isVisible: Boolean = false
    override val sheetFullyVisible: Boolean = false
    override val progress: Float = 0f

    override var screensNavigator: Navigator? = null
    override var tabsNavigator: TabNavigator? = null

    override fun show(screen: Screen) = Unit

    override fun hide() = Unit
    override fun <T> hideWithResult(result: T) = Unit

    override fun push(item: Screen, delay: Long) = Unit

    override fun push(items: List<Screen>) = Unit

    override fun replace(item: Screen) = Unit

    override fun replaceAll(item: Screen, inSheet: Boolean) = Unit

    override fun replaceAll(items: List<Screen>, inSheet: Boolean) = Unit

    override fun isAtRoot(): Boolean = true

    override fun pop(): Boolean = false
    override fun <T> popWithResult(result: T) = false

    override fun popAll() = Unit

    override fun popUntil(predicate: (Screen) -> Boolean): Boolean = false

    @Composable
    override fun saveableState(
        key: String,
        screen: Screen?,
        content: @Composable () -> Unit
    ) {
        content()
    }
}

interface NavigationLocator {
    val lastItem: Screen?
    fun push(item: Screen, delay: Long = 0)
    infix fun push(items: List<Screen>)
    fun pop(): Boolean
    fun popAll()
}

interface CodeNavigator: NavigationLocator {
    override val lastItem: Screen?
    val lastModalItem: Screen?
    val sheetStackRoot: Screen?
    val lastEvent: StackEvent
    val isVisible: Boolean
    val sheetFullyVisible: Boolean
    val progress: Float
    var screensNavigator: Navigator?
    var tabsNavigator: TabNavigator?

    fun show(screen: Screen)
    fun hide()
    fun <T> hideWithResult(result: T)
    override fun push(item: Screen, delay: Long)

    override infix fun push(items: List<Screen>)

    infix fun replace(item: Screen)

    fun replaceAll(item: Screen, inSheet: Boolean = false)

    fun replaceAll(items: List<Screen>, inSheet: Boolean = false)

    fun isAtRoot(): Boolean
    override fun pop(): Boolean
    fun <T> popWithResult(result: T): Boolean

    override fun popAll()

    infix fun popUntil(predicate: (Screen) -> Boolean): Boolean

    @SuppressLint("ComposableNaming")
    @Composable
    fun saveableState(
        key: String,
        screen: Screen?,
        content: @Composable () -> Unit
    )
}

class NavigatorWrapper(
    private val navigator: Navigator,
): NavigationLocator {

    override val lastItem: Screen?
        get() = navigator.lastItemOrNull

    override fun push(item: Screen, delay: Long) {
        navigator.push(item)
    }

    override infix fun push(items: List<Screen>) {
        navigator.push(items)
    }

    override fun pop(): Boolean {
        return navigator.pop()
    }

    override fun popAll() {
        navigator.popUntilRoot()
    }
}