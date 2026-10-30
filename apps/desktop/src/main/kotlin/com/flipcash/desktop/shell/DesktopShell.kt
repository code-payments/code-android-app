package com.flipcash.desktop.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// How long the pointer rests on the rail before the full list floats out (spec: Sidebar).
private const val PeekDelayMillis = 150L

/**
 * The signed-in window (macOS window node 13000:91, widths node 13045:3284): the chat list with its
 * footer, the main pane, and the context pane that [nav] opens. The width class comes from the space
 * this is given, not the window, so an off-screen render at a fixed width lays out as the window does.
 *
 * The list folds to a rail ([rail] and [railFooter]) at 840–1100 while a pane is open, and resting on
 * the rail floats the full list over the chat. The user can hide the list at any width; the toggle
 * then moves into the chat's toolbar. The context pane gets a column when there's room and otherwise
 * floats over the chat without dimming it. Compact shows the list or the main pane, never both.
 * [floatingSidebar] draws the sidebar as the macOS 26 glass panel inset from the window edges;
 * elsewhere it is a flat surface.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopShell(
    nav: DesktopNavState,
    sidebar: @Composable (Modifier) -> Unit,
    main: @Composable (Modifier) -> Unit,
    context: @Composable (ContextContent, Modifier) -> Unit,
    footer: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    rail: @Composable (Modifier) -> Unit = {},
    railFooter: @Composable (Modifier) -> Unit = {},
    title: String = "",
    floatingSidebar: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = widthClassFor(maxWidth)
        val sidebarMode = nav.sidebarModeAt(widthClass)
        val mode = nav.contextPaneModeAt(widthClass)
        val chrome = LocalWindowChrome.current
        val hazeState = rememberHazeState()
        val shown = nav.context

        // The floating list belongs to the rail; it can't outlive it.
        LaunchedEffect(sidebarMode) { if (sidebarMode != SidebarMode.Rail) nav.sidebarPeek = false }

        val listColumn: @Composable (Modifier, Boolean) -> Unit = { m, withToggle ->
            Column(m.testTag("sidebar")) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(DesktopDimens.TitleRowHeight).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    if (withToggle) SidebarToggle(nav::toggleSidebar)
                }
                sidebar(Modifier.weight(1f).fillMaxWidth())
                footer(Modifier.fillMaxWidth())
            }
        }
        val railColumn: @Composable (Modifier) -> Unit = { m ->
            Column(m.testTag("rail"), horizontalAlignment = Alignment.CenterHorizontally) {
                // The traffic lights take the top row on macOS; the toggle and search sit under them.
                Spacer(Modifier.height(chrome.titleBarHeight))
                SidebarToggle(nav::toggleSidebar)
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = CodeTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
                // A box holds the space, so the footer stays at the bottom whatever the slot draws.
                Box(Modifier.weight(1f).fillMaxWidth()) { rail(Modifier.fillMaxSize()) }
                railFooter(Modifier.fillMaxWidth())
            }
        }
        val mainWithToolbar: @Composable (Modifier, Boolean, (() -> Unit)?) -> Unit = { m, leadingEdge, onBack ->
            Column(m.testTag("main")) {
                Toolbar(
                    title = title,
                    // At the window's leading edge the toolbar makes room for the traffic lights.
                    leadingInset = if (leadingEdge) chrome.controlsWidth else 0.dp,
                    showToggle = leadingEdge && onBack == null,
                    onToggleSidebar = nav::toggleSidebar,
                    onBack = onBack,
                    onInfo = nav::toggleInfo,
                )
                main(Modifier.weight(1f).fillMaxWidth())
            }
        }
        val contextPane: @Composable (ContextContent, Modifier) -> Unit = { content, m ->
            Box(m.background(CodeTheme.colors.background)) { context(content, Modifier.fillMaxSize()) }
        }

        if (widthClass == WidthClass.Compact) {
            when {
                shown != null -> contextPane(shown, Modifier.fillMaxSize().testTag("context_overlay"))
                nav.compactShowsChat -> mainWithToolbar(Modifier.fillMaxSize(), true) { nav.compactShowsChat = false }
                else -> listColumn(Modifier.fillMaxSize(), false)
            }
            return@BoxWithConstraints
        }

        val listWidth = if (widthClass == WidthClass.Medium) DesktopDimens.SidebarMedium else DesktopDimens.SidebarExpanded
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                when (sidebarMode) {
                    SidebarMode.List -> SidebarSlot(listWidth, floatingSidebar, hazeState, chrome) { m -> listColumn(m, true) }
                    SidebarMode.Rail -> {
                        val scope = rememberCoroutineScope()
                        var resting by remember { mutableStateOf<Job?>(null) }
                        SidebarSlot(
                            width = DesktopDimens.SidebarRail,
                            floating = floatingSidebar,
                            hazeState = hazeState,
                            chrome = chrome,
                            modifier = Modifier
                                .onPointerEvent(PointerEventType.Enter) {
                                    resting?.cancel()
                                    resting = scope.launch {
                                        delay(PeekDelayMillis)
                                        nav.sidebarPeek = true
                                    }
                                }
                                .onPointerEvent(PointerEventType.Exit) { resting?.cancel() },
                            content = railColumn,
                        )
                    }
                    SidebarMode.Hidden -> Unit
                }
                Box(Modifier.weight(1f).fillMaxHeight().hazeSource(hazeState)) {
                    mainWithToolbar(Modifier.fillMaxSize(), sidebarMode == SidebarMode.Hidden, null)
                    if (shown != null && mode == ContextPaneMode.Overlay) {
                        // Keeps its width and covers the chat without dimming it; the chat beside it
                        // stays usable. Esc and the pane's ✕ close it.
                        contextPane(
                            shown,
                            Modifier
                                .align(Alignment.TopEnd)
                                .width(DesktopDimens.ContextPaneWidth)
                                .fillMaxHeight()
                                .shadow(16.dp)
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
            if (sidebarMode == SidebarMode.Rail && nav.sidebarPeek) {
                // The full list over the chat, which doesn't reflow. It covers the rail, so leaving it
                // is leaving both.
                SidebarSlot(
                    width = DesktopDimens.SidebarExpanded,
                    floating = true,
                    hazeState = hazeState,
                    chrome = chrome,
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Exit) { nav.sidebarPeek = false }
                        .testTag("sidebar_peek"),
                    elevated = true,
                ) { m -> listColumn(m, true) }
            }
        }
    }
}

/**
 * The sidebar's place at the leading edge. Floating, it's the glass panel inset from the window edges
 * (or meeting the top edge, see [WindowChrome.flushSidebar]); otherwise a flat column with a divider.
 */
@Composable
private fun SidebarSlot(
    width: Dp,
    floating: Boolean,
    hazeState: HazeState,
    chrome: WindowChrome,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    if (!floating) {
        Row(modifier.width(width).fillMaxHeight()) {
            content(Modifier.weight(1f).fillMaxHeight().background(CodeTheme.colors.background))
            PaneDivider()
        }
        return
    }
    val inset = DesktopDimens.SidebarGlassInset
    val shape: Shape = if (chrome.flushSidebar) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(16.dp)
    }
    Box(modifier.width(width + inset).fillMaxHeight()) {
        content(
            Modifier
                .padding(start = inset, end = inset, bottom = inset, top = if (chrome.flushSidebar) 0.dp else inset)
                .fillMaxSize()
                .then(if (elevated) Modifier.shadow(24.dp, shape) else Modifier)
                .floatingGlass(hazeState, shape),
        )
    }
}

/** Shows or hides the chat list, beside the traffic lights as in Finder and Notes (node 12997:185). */
@Composable
private fun SidebarToggle(onClick: () -> Unit) {
    val tint = CodeTheme.colors.textSecondary
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = "Toggle sidebar", onClick = onClick)
            .testTag("sidebar_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
            val stroke = 1.5.dp.toPx()
            val half = stroke / 2
            drawRoundRect(
                color = tint,
                topLeft = Offset(half, half),
                size = size.copy(width = size.width - stroke, height = size.height - stroke),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(stroke),
            )
            val x = size.width * 0.38f
            drawLine(tint, Offset(x, half), Offset(x, size.height - half), stroke)
        }
    }
}

/**
 * The bar over the main pane: the chat's title, and ⓘ for its Info. It's the window's title row, so
 * it shares the traffic lights' height; at the leading edge it starts after them, with the sidebar
 * toggle while the list is hidden. Back appears at compact.
 */
@Composable
private fun Toolbar(
    title: String,
    leadingInset: Dp,
    showToggle: Boolean,
    onToggleSidebar: () -> Unit,
    onBack: (() -> Unit)?,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesktopDimens.TitleRowHeight)
            .padding(start = leadingInset)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showToggle) SidebarToggle(onToggleSidebar)
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
