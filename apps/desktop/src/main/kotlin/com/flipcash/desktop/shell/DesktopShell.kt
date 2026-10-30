package com.flipcash.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Height of the area the window's title bar draws over. On macOS the content runs under a transparent
 * title bar, so whatever sits at the top of a pane starts below the traffic lights; elsewhere it is 0.
 */
val LocalTitleBarInset = compositionLocalOf { 0.dp }

/**
 * The signed-in window (macOS window node 13000:91, widths node 13045:3284): the chat list with its
 * footer, the main pane, and the context pane that [nav] opens. The width class comes from the space
 * this is given, not the window, so an off-screen render at a fixed width lays out as the window does.
 *
 * Wide gives the context pane its own column; below that it slides over the main pane with a scrim.
 * Compact shows the list or the main pane, never both. [floatingSidebar] draws the sidebar as the
 * macOS 26 glass panel inset from the window edges; elsewhere it is a flat surface.
 */
@Composable
fun DesktopShell(
    nav: DesktopNavState,
    sidebar: @Composable (Modifier) -> Unit,
    main: @Composable (Modifier) -> Unit,
    context: @Composable (ContextContent, Modifier) -> Unit,
    footer: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    floatingSidebar: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = widthClassFor(maxWidth)
        val mode = contextPaneModeFor(widthClass)
        val hazeState = rememberHazeState()
        val shown = nav.context

        val sidebarColumn: @Composable (Modifier) -> Unit = { m ->
            Column(m.testTag("sidebar")) {
                sidebar(Modifier.weight(1f).fillMaxWidth().padding(top = LocalTitleBarInset.current))
                footer(Modifier.fillMaxWidth())
            }
        }
        val mainWithToolbar: @Composable (Modifier, (() -> Unit)?) -> Unit = { m, onBack ->
            Column(m.testTag("main")) {
                Toolbar(title = title, onBack = onBack, onInfo = nav::toggleInfo)
                main(Modifier.weight(1f).fillMaxWidth())
            }
        }
        val contextPane: @Composable (ContextContent, Modifier) -> Unit = { content, m ->
            Box(m.background(CodeTheme.colors.background)) { context(content, Modifier.fillMaxSize()) }
        }

        if (widthClass == WidthClass.Compact) {
            when {
                shown != null -> contextPane(shown, Modifier.fillMaxSize().testTag("context_overlay"))
                nav.compactShowsChat -> mainWithToolbar(Modifier.fillMaxSize()) { nav.compactShowsChat = false }
                else -> sidebarColumn(Modifier.fillMaxSize())
            }
            return@BoxWithConstraints
        }

        val sidebarWidth = if (widthClass == WidthClass.Medium) DesktopDimens.SidebarMedium else DesktopDimens.SidebarExpanded
        Row(Modifier.fillMaxSize()) {
            if (floatingSidebar) {
                // The glass panel floats over the window's background; the panes beside it are the
                // haze source, so the panel tints what scrolls under its rounded edge.
                Box(Modifier.width(sidebarWidth + DesktopDimens.SidebarGlassInset).fillMaxHeight()) {
                    sidebarColumn(
                        Modifier
                            .padding(DesktopDimens.SidebarGlassInset)
                            .padding(end = 0.dp)
                            .fillMaxSize()
                            .floatingGlass(hazeState, RoundedCornerShape(16.dp)),
                    )
                }
            } else {
                sidebarColumn(Modifier.width(sidebarWidth).fillMaxHeight())
                PaneDivider()
            }
            Box(Modifier.weight(1f).fillMaxHeight().hazeSource(hazeState)) {
                mainWithToolbar(Modifier.fillMaxSize(), null)
                if (shown != null && mode == ContextPaneMode.Overlay) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = nav::closeContext,
                            )
                            .testTag("context_scrim"),
                    )
                    contextPane(
                        shown,
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(DesktopDimens.ContextPaneWidth)
                            .fillMaxHeight()
                            .testTag("context_overlay"),
                    )
                }
            }
            if (shown != null && mode == ContextPaneMode.Column) {
                PaneDivider()
                contextPane(
                    shown,
                    Modifier.width(DesktopDimens.ContextPaneWidth).fillMaxHeight().testTag("context_pane"),
                )
            }
        }
    }
}

/** The bar over the main pane: the chat's title, and ⓘ for its Info. Back appears at compact. */
@Composable
private fun Toolbar(title: String, onBack: (() -> Unit)?, onInfo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LocalTitleBarInset.current)
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Text(
                text = "‹ Chats",
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("compact_back"),
            )
        }
        Text(
            text = title,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onInfo)
                .testTag("toolbar_info"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = "Info", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
