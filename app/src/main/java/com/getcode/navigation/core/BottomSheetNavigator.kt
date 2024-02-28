package com.getcode.navigation.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.compositionUniqueId
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.launch

// Simplify and consolidate typealias and ProvidableCompositionLocal declaration
typealias BottomSheetNavigatorContent = @Composable BottomSheetNavigator.() -> Unit

val LocalBottomSheetNavigator = staticCompositionLocalOf<BottomSheetNavigator> {
    error("BottomSheetNavigator not provided")
}

// Make use of OptIn annotations at the function level to reduce global scope annotations
@Composable
fun BottomSheetNavigator(
    modifier: Modifier = Modifier,
    hideOnBackPress: Boolean = true,
    scrimColor: Color = CodeTheme.colors.surface.copy(alpha = 0.32f),
    sheetShape: Shape = CodeTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
    sheetElevation: Dp = ModalBottomSheetDefaults.Elevation,
    sheetBackgroundColor: Color = CodeTheme.colors.surface,
    sheetContentColor: Color = CodeTheme.colors.onSurface,
    sheetGesturesEnabled: Boolean = true,
    skipHalfExpanded: Boolean = true,
    animationSpec: AnimationSpec<Float> = tween(),
    key: String = compositionUniqueId(),
    sheetContent: BottomSheetNavigatorContent,
    content: BottomSheetNavigatorContent
) {
    // Utilize the navigator for managing screen navigation
    Navigator(HiddenBottomSheetScreen, onBackPressed = null, key = key) { navigator ->
        val coroutineScope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmValueChange = { true },
            skipHalfExpanded = skipHalfExpanded,
            animationSpec = animationSpec
        )

        // Initialize BottomSheetNavigator with remembered values
        val bottomSheetNavigator = remember { BottomSheetNavigator(navigator, sheetState, coroutineScope) }

        // Provide the BottomSheetNavigator instance downwards
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
                    BottomSheetNavigatorBackHandler(bottomSheetNavigator, hideOnBackPress)
                    bottomSheetNavigator.sheetContent()
                },
                content = { bottomSheetNavigator.content() }
            )
        }
    }
}

// Refactor BottomSheetNavigator class for clarity and efficiency
class BottomSheetNavigator internal constructor(
    val navigator: Navigator,
    private val sheetState: ModalBottomSheetState,
    private val coroutineScope: CoroutineScope
) {
    // Simplified visibility and progress calculations
    val isVisible get() = sheetState.isVisible

    // Operation functions simplified for clarity
    fun show(screen: Screen) = coroutineScope.launch {
        sheetState.show()
        navigator.replaceAll(screen)
    }

    fun hide() = coroutineScope.launch {
        sheetState.hide()
        navigator.replaceAll(HiddenBottomSheetScreen)
    }

    // Define extension functions on Navigator for convenience
    private fun Navigator.replaceAll(screen: Screen) {
        // Implementation to replace all screens in the navigator stack with the given screen
    }
}

// Refactor BackHandler to simplify its usage with the BottomSheetNavigator
@Composable
fun BottomSheetNavigatorBackHandler(
    navigator: BottomSheetNavigator,
    hideOnBackPress: Boolean
) {
    BackHandler(enabled = navigator.isVisible && hideOnBackPress) {
        if (!navigator.navigator.pop()) {
            navigator.hide()
        }
    }
}

// Simplify HiddenBottomSheetScreen as it serves as a placeholder
object HiddenBottomSheetScreen : Screen {
    @Composable
    override fun Content() {
        Spacer(modifier = Modifier.height(1.dp))
    }
}
