package com.getcode.navigation.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.contentColorFor
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
import cafe.adriel.voyager.core.stack.Stack
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.compositionUniqueId
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    sheetShape: Shape = CodeTheme.shapes.large,
    sheetElevation: Dp = ModalBottomSheetDefaults.Elevation,
    sheetBackgroundColor: Color = CodeTheme.colors.surface,
    sheetContentColor: Color = CodeTheme.colors.onSurface,
    sheetGesturesEnabled: Boolean = true,
    skipHalfExpanded: Boolean = true,
    animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
    key: String = compositionUniqueId(),
    sheetContent: BottomSheetNavigatorContent = { CurrentScreen() },
    content: BottomSheetNavigatorContent
) {
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

    Navigator(HiddenBottomSheetScreen, onBackPressed = null, key = key) { navigator ->
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


    private var shownSheetScreens: MutableList<Screen> = mutableListOf()

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
            if (shownSheetScreens.isEmpty()) {
                replaceAll(screen)
                shownSheetScreens.add(screen)
                sheetState.show()
            } else {
                hideAndShow(screen)
            }
        }
    }

    private suspend fun hideAndShow(screen: Screen) {
        if (isVisible) {
            sheetState.hide()
            replaceAll(HiddenBottomSheetScreen)

            shownSheetScreens.add(screen)
            replaceAll(screen)
            sheetState.show()
        }
    }


    fun hide() {
        coroutineScope.launch {
            if (isVisible) {
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
            shownSheetScreens.add(item)
        }
        navigator.push(item)
    }

    override fun pop(): Boolean {
        if (isVisible) {
            shownSheetScreens.removeLastOrNull()
        }
        return navigator.pop()
    }

    private suspend fun showPreviousSheet() {
        shownSheetScreens.removeLastOrNull()
        if (shownSheetScreens.isNotEmpty()) {
            replaceAll(shownSheetScreens)
            sheetState.show()
        }
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

private object HiddenBottomSheetScreen : Screen {
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