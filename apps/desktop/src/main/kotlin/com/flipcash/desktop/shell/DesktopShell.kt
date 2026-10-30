package com.flipcash.desktop.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
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
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = widthClassFor(maxWidth)
        val sidebarMode = nav.sidebarModeAt(widthClass)
        val mode = nav.contextPaneModeAt(widthClass)
        val chrome = LocalWindowChrome.current
        val shown = nav.context

        // The floating list belongs to the rail; it can't outlive it.
        LaunchedEffect(sidebarMode) { if (sidebarMode != SidebarMode.Rail) nav.sidebarPeek = false }

        // The title row is left empty: the traffic lights and the toggle sit over it.
        val listColumn: @Composable (Modifier) -> Unit = { m ->
            Column(m.testTag("sidebar")) {
                Spacer(Modifier.height(DesktopDimens.TitleRowHeight))
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
        val mainWithToolbar: @Composable (Modifier, Dp, (() -> Unit)?) -> Unit = { m, leadingInset, onBack ->
            Column(m.testTag("main")) {
                Toolbar(
                    title = title,
                    leadingInset = leadingInset,
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
                nav.compactShowsChat -> mainWithToolbar(Modifier.fillMaxSize(), chrome.controlsWidth) { nav.compactShowsChat = false }
                else -> listColumn(Modifier.fillMaxSize())
            }
            return@BoxWithConstraints
        }

        val listWidth = if (widthClass == WidthClass.Medium) DesktopDimens.SidebarMedium else DesktopDimens.SidebarExpanded
        // Hiding, folding to the rail and opening a pane all move the panes, so they slide rather than
        // jump. The sidebar's content is laid out at its own width and clipped, so text doesn't reflow
        // as the edge moves.
        val sidebarWidth by animateDpAsState(
            targetValue = when (sidebarMode) {
                SidebarMode.List -> listWidth
                SidebarMode.Rail -> DesktopDimens.SidebarRail
                SidebarMode.Hidden -> 0.dp
            },
            animationSpec = PaneMotion.size(),
            label = "sidebar width",
        )
        // With the list hidden, the toolbar starts after the traffic lights and the toggle.
        val toolbarInset by animateDpAsState(
            targetValue = if (sidebarMode == SidebarMode.Hidden) toggleStart(chrome) + ToggleSize else 0.dp,
            animationSpec = PaneMotion.size(),
            label = "toolbar inset",
        )
        // While the sidebar slides away it keeps showing what it showed.
        var shownSidebar by remember { mutableStateOf(sidebarMode) }
        if (sidebarMode != SidebarMode.Hidden) shownSidebar = sidebarMode
        // The same for the context pane, so it has something to draw while it closes.
        var lastShown by remember { mutableStateOf(shown) }
        if (shown != null) lastShown = shown
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (sidebarWidth > 0.dp) {
                    val scope = rememberCoroutineScope()
                    var resting by remember { mutableStateOf<Job?>(null) }
                    SidebarSlot(
                        width = sidebarWidth,
                        modifier = Modifier
                            .onPointerEvent(PointerEventType.Enter) {
                                if (sidebarMode != SidebarMode.Rail) return@onPointerEvent
                                resting?.cancel()
                                resting = scope.launch {
                                    delay(PeekDelayMillis)
                                    nav.sidebarPeek = true
                                }
                            }
                            .onPointerEvent(PointerEventType.Exit) { resting?.cancel() },
                    ) { m ->
                        Crossfade(shownSidebar, animationSpec = PaneMotion.fade(), label = "sidebar content") { content ->
                            when (content) {
                                SidebarMode.Rail -> railColumn(m.wrapContentWidth(Alignment.Start, unbounded = true).width(DesktopDimens.SidebarRail))
                                else -> listColumn(m.wrapContentWidth(Alignment.Start, unbounded = true).width(listWidth))
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    mainWithToolbar(Modifier.fillMaxSize(), toolbarInset, null)
                    // Keeps its width and covers the chat without dimming it; the chat beside it stays
                    // usable. Esc and the pane's ✕ close it.
                    OverlayPane(
                        visible = shown != null && mode == ContextPaneMode.Overlay,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        lastShown?.let {
                            contextPane(
                                it,
                                Modifier
                                    .width(DesktopDimens.ContextPaneWidth)
                                    .fillMaxHeight()
                                    .shadow(16.dp)
                                    .testTag("context_overlay"),
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = shown != null && mode == ContextPaneMode.Column,
                    enter = expandHorizontally(PaneMotion.bounds(), expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(PaneMotion.bounds(), shrinkTowards = Alignment.Start),
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        PaneDivider()
                        lastShown?.let {
                            contextPane(
                                it,
                                Modifier.width(DesktopDimens.ContextPaneWidth).fillMaxHeight().testTag("context_pane"),
                            )
                        }
                    }
                }
            }
            // The full list over the chat, which doesn't reflow. It covers the rail, so leaving it is
            // leaving both.
            AnimatedVisibility(
                visible = sidebarMode == SidebarMode.Rail && nav.sidebarPeek,
                enter = slideInHorizontally(PaneMotion.offset()) { -it / 3 } + fadeIn(PaneMotion.fade()),
                exit = slideOutHorizontally(PaneMotion.offset()) { -it / 3 } + fadeOut(PaneMotion.fade()),
            ) {
                // Leaving is judged around the list and its toggle together; the toggle sits on top
                // of the list, so the list alone would see the pointer leave on the way to it.
                Box(
                    Modifier
                        .onPointerEvent(PointerEventType.Exit) { nav.sidebarPeek = false }
                        .testTag("sidebar_peek"),
                ) {
                    SidebarSlot(
                        width = DesktopDimens.SidebarExpanded,
                        elevated = true,
                    ) { m -> listColumn(m) }
                    // It covers the rail's toggle, so it brings the list's own, which travels with it.
                    TitleRowToggle(chrome, nav::toggleSidebar)
                }
            }
            // One toggle beside the traffic lights that stays put whether the list is shown or hidden,
            // as in Finder and Notes. The rail carries its own, under the lights.
            if (sidebarMode != SidebarMode.Rail) TitleRowToggle(chrome, nav::toggleSidebar)
        }
    }
}

private val ToggleSize = 32.dp

/** The sidebar toggle in the title row, beside the traffic lights on macOS. */
@Composable
private fun TitleRowToggle(chrome: WindowChrome, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = toggleStart(chrome)).height(DesktopDimens.TitleRowHeight),
        contentAlignment = Alignment.Center,
    ) { SidebarToggle(onClick) }
}

/** Where the toggle starts: after the traffic lights on macOS, or the window's edge elsewhere. */
private fun toggleStart(chrome: WindowChrome) = if (chrome.controlsWidth > 0.dp) chrome.controlsWidth else 8.dp

/** Durations for the panes moving: long enough to follow, short enough not to wait on. */
private object PaneMotion {
    fun size() = tween<Dp>(durationMillis = 250, easing = FastOutSlowInEasing)
    fun offset() = tween<IntOffset>(durationMillis = 250, easing = FastOutSlowInEasing)
    fun bounds() = tween<IntSize>(durationMillis = 250, easing = FastOutSlowInEasing)
    fun fade() = tween<Float>(durationMillis = 150)
}

/** The context pane sliding in over the chat from the trailing edge. */
@Composable
private fun OverlayPane(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(PaneMotion.offset()) { it } + fadeIn(PaneMotion.fade()),
        exit = slideOutHorizontally(PaneMotion.offset()) { it } + fadeOut(PaneMotion.fade()),
    ) { content() }
}

/**
 * The sidebar's place at the leading edge: the window's own background with a hairline divider, as in
 * node 13000:91. [elevated] is the list floating over the chat from the rail, which casts a shadow
 * instead.
 */
@Composable
private fun SidebarSlot(
    width: Dp,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier
            .width(width)
            .fillMaxHeight()
            .then(if (elevated) Modifier.shadow(24.dp) else Modifier)
            .background(CodeTheme.colors.background)
            .clipToBounds(),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
            content(Modifier.fillMaxHeight())
        }
        PaneDivider()
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
 * it shares the traffic lights' height; at the leading edge it starts after them and, while the list
 * is hidden, after the sidebar toggle. Back appears at compact.
 */
@Composable
private fun Toolbar(
    title: String,
    leadingInset: Dp,
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
