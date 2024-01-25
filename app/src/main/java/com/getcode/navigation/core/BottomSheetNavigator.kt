package com.getcode.navigation.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.stack.SnapshotStateStack
import cafe.adriel.voyager.core.stack.Stack
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.compositionUniqueId
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

typealias BottomSheetNavigatorContent = @Composable (bottomSheetNavigator: BottomSheetNavigator) -> Unit

val LocalBottomSheetNavigator: ProvidableCompositionLocal<BottomSheetNavigator> =
    staticCompositionLocalOf { error("BottomSheetNavigator not initialized") }

@OptIn(InternalVoyagerApi::class)
@ExperimentalMaterialApi
@Composable
fun BottomSheetNavigator(
    modifier: Modifier = Modifier,
    hideOnBackPress: Boolean = true,
    scrimColor: Color = CodeTheme.colors.surface.copy(alpha = 0.32f),
    sheetShape: Shape = CodeTheme.shapes.extraLarge,
    sheetElevation: Dp = ModalBottomSheetDefaults.Elevation,
    sheetBackgroundColor: Color = CodeTheme.colors.surface,
    sheetContentColor: Color = CodeTheme.colors.onSurface,
    sheetGesturesEnabled: Boolean = true,
    skipHalfExpanded: Boolean = true,
    animationSpec: AnimationSpec<Float> =  tween(),
    key: String = compositionUniqueId(),
    sheetContent: BottomSheetNavigatorContent = { CurrentScreen() },
    content: BottomSheetNavigatorContent
) {
    Navigator(HiddenBottomSheetScreen, onBackPressed = null, key = key) { navigator ->
        var hideBottomSheet: (() -> Unit)? = null
        val coroutineScope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmValueChange = { state ->
                if (state == ModalBottomSheetValue.Hidden) {
                    hideBottomSheet?.invoke()
                }
                true
            },
            skipHalfExpanded = skipHalfExpanded,
            animationSpec = animationSpec
        )

        val bottomSheetNavigator = remember(navigator, sheetState, coroutineScope) {
            BottomSheetNavigator(navigator, sheetState, coroutineScope)
        }

        hideBottomSheet = bottomSheetNavigator::hide

        CompositionLocalProvider(LocalBottomSheetNavigator provides bottomSheetNavigator) {
            ModalBottomSheetLayout(
                modifier = modifier,
                scrimColor = scrimColor,
                sheetState = sheetState,
                sheetShape = sheetShape,
                sheetElevation = sheetElevation,
                sheetBackgroundColor = sheetBackgroundColor,
                sheetContentColor = sheetContentColor,
                sheetGesturesEnabled = sheetGesturesEnabled,
                sheetContent = {
                    BottomSheetNavigatorBackHandler(bottomSheetNavigator, sheetState, hideOnBackPress)
                    sheetContent(bottomSheetNavigator)
                },
                content = {
                    content(bottomSheetNavigator)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
class BottomSheetNavigator @InternalVoyagerApi constructor(
    internal val navigator: Navigator,
    private val sheetState: ModalBottomSheetState,
    private val coroutineScope: CoroutineScope
) : Stack<Screen> by navigator {

    val isVisible: Boolean
        get() = sheetState.isVisible


    val sheetStacks = SheetStacks(LinkedHashMap())

    val progress: Float
        get() {
            val currentState = sheetState.currentValue
            val targetState = sheetState.targetValue
            if (currentState == ModalBottomSheetValue.Hidden && currentState == targetState) return 0f
            return when (targetState) {
                ModalBottomSheetValue.Hidden -> 1f - sheetState.progress
                ModalBottomSheetValue.Expanded -> sheetState.progress
                ModalBottomSheetValue.HalfExpanded -> 0f
            }.coerceIn(0f, 1f)
        }

    fun show(screen: Screen) {
        coroutineScope.launch {
            if (sheetStacks.isEmpty) {
                replaceAll(screen)
                // setup stack
                sheetStacks.push(screen)
                sheetState.show()
            } else {
                hideAndShow(screen)
            }
        }
    }

    private suspend fun hideAndShow(screen: Screen) {
        if (isVisible) {
            // animate sheet out
            sheetState.hide()
            // replacing w/ dummy sheet
            replaceAll(HiddenBottomSheetScreen)
            // push new stack
            sheetStacks.push(screen)
            // show new sheet
            replaceAll(screen)
            sheetState.show()
        } else {
            Timber.e("shouldn't get here; but ensuring a sheet is shown when requested.")
            sheetStacks.popAll()
            show(screen)
        }
    }


    fun hide() {
        coroutineScope.launch {
            if (isVisible) {
                sheetStacks.pop()
                sheetState.hide()
                replaceAll(HiddenBottomSheetScreen)
                showPreviousSheet()
            } else if (sheetState.targetValue == ModalBottomSheetValue.Hidden) {
                // Swipe down - sheetState is already hidden here so `isVisible` is false
                replaceAll(HiddenBottomSheetScreen)
            }
        }
    }

    override fun push(item: Screen) {
        if (isVisible) {
            sheetStacks.pushToLastStack(item)
        }
        navigator.push(item)
    }

    override fun pop(): Boolean {
        if (isVisible) {
            sheetStacks.popFromLastStack()
        }
        return navigator.pop()
    }

    private suspend fun showPreviousSheet(): Boolean {
        if (!sheetStacks.isEmpty) {
            val screens = sheetStacks.lastItemOrNull?.second.orEmpty()
            if (screens.isNotEmpty()) {
                replaceAll(screens)
                sheetState.show()
                return true
            }
        }

        return false
    }


    @Composable
    fun saveableState(
        key: String,
        screen: Screen? = lastItemOrNull,
        content: @Composable () -> Unit
    ) {
        val lastScreen by remember(screen) {
            derivedStateOf {
                screen ?: error("Navigator has no screen")
            }
        }

        navigator.saveableState(key, screen = lastScreen, content = content)
    }
}

object HiddenBottomSheetScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey
    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@ExperimentalMaterialApi
@Composable fun BottomSheetNavigatorBackHandler(
    navigator: BottomSheetNavigator,
    sheetState: ModalBottomSheetState,
    hideOnBackPress: Boolean
) {
    BackHandler(enabled = sheetState.isVisible) {
        if (navigator.pop().not() && hideOnBackPress) {
            navigator.hide()
        }
    }
}

class SheetStacks(
    map: LinkedHashMap<Screen, List<Screen>>
): Stack<Pair<Screen, List<Screen>>> by map.toMutableStateStack() {

    fun pushTo(stackRoot: Screen, screen: Screen) {
        val stack = items.firstOrNull { it.first == stackRoot } ?: return
        replace(stackRoot to stack.second + screen)
    }

    fun popFrom(stackRoot: Screen, screen: Screen) {
        val stack = items.firstOrNull { it.first == stackRoot } ?: return
        replace(stackRoot to stack.second - screen)
    }

    infix fun pushToLastStack(screen: Screen) {
        val stack = lastItemOrNull ?: return
        pushTo(stack.first, screen)
    }
    infix fun push(screen: Screen) {
        push(screen to listOf(screen))
    }

    fun popFromLastStack() {
        val stack = lastItemOrNull ?: return
        val screen = stack.second.lastOrNull() ?: return
        popFrom(stack.first, screen)
    }
}

private fun <K, V> LinkedHashMap<K, V>.toMutableStateStack(
    minSize: Int = 0
): SnapshotStateStack<Pair<K, V>> =
    SnapshotStateStack(this.toList(), minSize)