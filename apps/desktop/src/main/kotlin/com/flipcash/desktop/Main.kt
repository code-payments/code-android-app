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
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.DesktopDimens
import com.flipcash.desktop.shell.DesktopNavState
import com.flipcash.desktop.shell.LocalTitleBarInset
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

// Height of the macOS title bar the content runs under, which the traffic lights sit in.
private val MacTitleBarHeight = 28.dp

private val MinWindow = Dimension(DesktopDimens.MinWindowWidth.value.toInt(), DesktopDimens.MinWindowHeight.value.toInt())

@OptIn(FlowPreview::class)
fun main() {
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
            DesktopNavState().apply { if (saved?.contextPaneOpen == true) context = ContextContent.Wallet }
        }
        val awtWindowRef = remember { java.util.concurrent.atomic.AtomicReference<java.awt.Window?>() }
        fun current(): SavedWindow? {
            val b = awtWindowRef.get()?.bounds ?: return null
            return SavedWindow(
                x = b.x,
                y = b.y,
                width = b.width,
                height = b.height,
                maximized = windowState.placement == WindowPlacement.Maximized,
                contextPaneOpen = nav.context == ContextContent.Wallet,
            )
        }
        Window(
            onCloseRequest = {
                // Debounced saves can miss a move made just before quitting.
                current()?.let(windowStore::save)
                exitApplication()
            },
            title = "Flipcash",
            state = windowState,
        ) {
            LaunchedEffect(Unit) {
                awtWindowRef.set(window)
                configureChrome()
            }
            LaunchedEffect(Unit) {
                snapshotFlow { listOf(windowState.position, windowState.size, windowState.placement, nav.context) }
                    .drop(1)
                    .debounce(500)
                    .collect { current()?.let(windowStore::save) }
            }
            CompositionLocalProvider(LocalTitleBarInset provides if (isMac) MacTitleBarHeight else 0.dp) {
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
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", true)
    }
}
