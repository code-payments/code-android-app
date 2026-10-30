package com.flipcash.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.flipcash.desktop.input.DesktopCommand
import com.flipcash.desktop.input.DesktopMenuBar
import com.flipcash.desktop.input.HostOs
import com.flipcash.desktop.input.desktopKeyHandler
import com.flipcash.desktop.input.perform
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.widthClassFor
import com.flipcash.desktop.shell.DesktopDimens
import com.flipcash.desktop.shell.DesktopNavState
import com.flipcash.desktop.shell.LocalWindowChrome
import com.flipcash.desktop.shell.WindowChrome
import com.flipcash.desktop.window.SavedWindow
import com.flipcash.desktop.window.WindowStateStore
import com.flipcash.desktop.window.fitTo
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.lang.management.ManagementFactory

internal val isMac = System.getProperty("os.name").orEmpty().lowercase().startsWith("mac")

// The standard macOS title bar the content runs under. Its traffic lights sit near the top edge, so
// the sidebar meets that edge flush rather than putting its rounded corner under them.
private val MacChrome = WindowChrome(titleBarHeight = 28.dp, controlsWidth = 72.dp, flushSidebar = true)

private val MinWindow = Dimension(DesktopDimens.MinWindowWidth.value.toInt(), DesktopDimens.MinWindowHeight.value.toInt())

@OptIn(FlowPreview::class)
fun main() {
    // Swing menus go in the window unless this is set before AWT starts; macOS apps use the screen menu bar.
    if (isMac) System.setProperty("apple.laf.useScreenMenuBar", "true")
    val graph = createGraph<DesktopGraph>()
    val signedIn = runBlocking {
        graph.startup()
        graph.restoreSession()
    }
    val windowStore = WindowStateStore(DesktopModule.appDir.toPath().resolve("window.json"))
    val saved = windowStore.load()?.fitTo(usableScreens(), MinWindow)
    application {
        val windowState = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            width = saved?.width?.dp ?: 1040.dp,
            height = saved?.height?.dp ?: 760.dp,
        )
        // Only Wallet comes back open; Info, You and a currency belong to what was on screen then.
        val nav = remember {
            DesktopNavState().apply {
                if (saved?.contextPaneOpen == true) context = ContextContent.Wallet
                sidebarHidden = saved?.sidebarHidden == true
            }
        }
        val awtWindowRef = remember { java.util.concurrent.atomic.AtomicReference<java.awt.Window?>() }
        // The floating bounds, kept while maximized or full screen so un-maximizing returns to them.
        val lastFloating = remember {
            java.util.concurrent.atomic.AtomicReference(saved?.let { Rectangle(it.x, it.y, it.width, it.height) })
        }
        fun current(): SavedWindow? {
            // A minimized window reports nonsense bounds (about -32000,-32000 on Windows); keep the last good save.
            if (windowState.isMinimized) return null
            val awt = awtWindowRef.get() ?: return null
            if (windowState.placement == WindowPlacement.Floating) lastFloating.set(awt.bounds)
            val b = lastFloating.get() ?: awt.bounds
            return SavedWindow(
                x = b.x,
                y = b.y,
                width = b.width,
                height = b.height,
                maximized = windowState.placement != WindowPlacement.Floating,
                // Before linking there is no Wallet to show; carry the saved choice forward.
                contextPaneOpen = if (signedIn) nav.context == ContextContent.Wallet else saved?.contextPaneOpen == true,
                sidebarHidden = nav.sidebarHidden,
            )
        }
        fun run(command: DesktopCommand): Boolean =
            nav.perform(command, nav.contextPaneModeAt(widthClassFor(windowState.size.width)))
        Window(
            onCloseRequest = {
                // Debounced saves can miss a move made just before quitting.
                try {
                    current()?.let(windowStore::save)
                } finally {
                    exitApplication()
                }
            },
            title = "Flipcash",
            state = windowState,
            onPreviewKeyEvent = desktopKeyHandler(HostOs.current, ::run),
        ) {
            if (isMac) DesktopMenuBar(windowState, nav.sidebarHidden, ::run)
            LaunchedEffect(Unit) {
                awtWindowRef.set(window)
                configureChrome()
            }
            LaunchedEffect(Unit) {
                snapshotFlow { listOf(windowState.position, windowState.size, windowState.placement, windowState.isMinimized, nav.context, nav.sidebarHidden) }
                    .drop(1)
                    .debounce(500)
                    .collect { current()?.let(windowStore::save) }
            }
            CompositionLocalProvider(LocalWindowChrome provides if (isMac) MacChrome else WindowChrome()) {
                DesktopApp(graph, signedIn, nav)
            }
            LaunchedEffect(Unit) {
                val sinceJvmStart = System.currentTimeMillis() -
                    ManagementFactory.getRuntimeMXBean().startTime
                println("startup: first composition ${sinceJvmStart}ms after JVM start")
            }
        }
    }
}

/** Each screen's bounds less the dock, menu bar or taskbar. */
private fun usableScreens(): List<Rectangle> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    val toolkit = Toolkit.getDefaultToolkit()
    return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        val config = device.defaultConfiguration
        val b = config.bounds
        val i = toolkit.getScreenInsets(config)
        Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
    }
}

/**
 * The minimum window, and on macOS a unified title bar: the content runs under a transparent title
 * bar with the traffic lights over it. Windows and Linux keep their normal decorated window.
 */
private fun FrameWindowScope.configureChrome() {
    window.minimumSize = MinWindow
    if (isMac) {
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        // The toolbar names the chat; the window title would sit over it.
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}
