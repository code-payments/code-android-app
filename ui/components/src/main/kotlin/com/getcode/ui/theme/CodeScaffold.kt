package com.getcode.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.DrawerDefaults
import androidx.compose.material.FabPosition
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge

/** Where a scaffold's bars sit relative to its content. */
enum class ScaffoldBarPlacement {
    /** The bars take their own space and the content is laid out between them. */
    Inset,

    /**
     * The bars are drawn over the content, which fills the whole area and is handed their heights
     * as padding to inset itself by. Applied as content padding on a scrolling list, rows run
     * underneath the bars — fading into whatever fade the bar draws over them — instead of being
     * cut off at the bar's edge, while still coming to rest clear of it.
     */
    Overlay,
}

@Composable
fun CodeScaffold(
    modifier: Modifier = Modifier,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable (SnackbarHostState) -> Unit = {
        SnackbarHost(it) { data ->
            CodeSnackbar(snackbarData = data)
        }
    },
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    isFloatingActionButtonDocked: Boolean = false,
    drawerContent: @Composable (ColumnScope.() -> Unit)? = null,
    drawerGesturesEnabled: Boolean = true,
    drawerShape: Shape = CodeTheme.shapes.extraLarge,
    drawerElevation: Dp = DrawerDefaults.Elevation,
    drawerBackgroundColor: Color = CodeTheme.colors.background,
    drawerContentColor: Color = CodeTheme.colors.onBackground,
    drawerScrimColor: Color = CodeTheme.colors.brandLight,
    backgroundColor: Color = CodeTheme.colors.background,
    contentColor: Color = CodeTheme.colors.onBackground,
    barPlacement: ScaffoldBarPlacement = ScaffoldBarPlacement.Inset,
    content: @Composable (PaddingValues) -> Unit
) {
    val isOverlay = barPlacement == ScaffoldBarPlacement.Overlay
    Scaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        // Overlay hands the bars to [OverlayBars] below instead: [Scaffold]'s own slots shorten
        // the body by the bars' heights and place it between them, which is the placement Overlay
        // exists to avoid.
        topBar = if (isOverlay) ({}) else topBar,
        bottomBar = if (isOverlay) ({}) else bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        isFloatingActionButtonDocked = isFloatingActionButtonDocked,
        drawerContent = drawerContent,
        drawerGesturesEnabled = drawerGesturesEnabled,
        drawerShape = drawerShape,
        drawerElevation = drawerElevation,
        drawerBackgroundColor = drawerBackgroundColor,
        drawerContentColor = drawerContentColor,
        drawerScrimColor = drawerScrimColor,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        content = { padding ->
            if (isOverlay) {
                OverlayBars(topBar = topBar, bottomBar = bottomBar, content = content)
            } else {
                content(padding)
            }
        }
    )
}

/**
 * Draws [topBar] and [bottomBar] over a full-size [content], which is handed their heights as
 * padding.
 *
 * The bars are subcomposed and measured BEFORE the content in the same layout pass, so the content
 * receives correct padding on the very first frame. Measuring them with `onSizeChanged` instead fed
 * 0 padding on frame 1 and snapped to the real heights on frame 2, which made the chat's message
 * list visibly jump on every open and every pop-back.
 */
@Composable
private fun OverlayBars(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val topPlaceables = subcompose(OverlaySlot.Top, topBar).map { it.measure(looseConstraints) }
        val bottomPlaceables =
            subcompose(OverlaySlot.Bottom, bottomBar).map { it.measure(looseConstraints) }
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0

        val padding = PaddingValues(
            top = topHeight.toDp(),
            bottom = bottomHeight.toDp(),
        )

        val contentPlaceables = subcompose(OverlaySlot.Content) { content(padding) }
            .map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.place(0, 0) }
            topPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, 0) }
            bottomPlaceables.forEach {
                it.place((constraints.maxWidth - it.width) / 2, constraints.maxHeight - it.height)
            }
        }
    }
}

private enum class OverlaySlot { Top, Bottom, Content }