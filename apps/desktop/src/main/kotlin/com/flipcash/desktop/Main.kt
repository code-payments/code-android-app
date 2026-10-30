package com.flipcash.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.flipcash.desktop.shell.DesktopDimens
import com.flipcash.desktop.shell.LocalTitleBarInset
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.lang.management.ManagementFactory

internal val isMac = System.getProperty("os.name").orEmpty().lowercase().startsWith("mac")

// Height of the macOS title bar the content runs under, which the traffic lights sit in.
private val MacTitleBarHeight = 28.dp

fun main() {
    val graph = createGraph<DesktopGraph>()
    val signedIn = runBlocking {
        graph.startup()
        graph.restoreSession()
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Flipcash",
            state = rememberWindowState(width = 1040.dp, height = 760.dp),
        ) {
            LaunchedEffect(Unit) { configureChrome() }
            CompositionLocalProvider(LocalTitleBarInset provides if (isMac) MacTitleBarHeight else 0.dp) {
                DesktopApp(graph, signedIn)
            }
            LaunchedEffect(Unit) {
                val sinceJvmStart = System.currentTimeMillis() -
                    ManagementFactory.getRuntimeMXBean().startTime
                println("startup: first composition ${sinceJvmStart}ms after JVM start")
            }
        }
    }
}

/**
 * The minimum window, and on macOS a unified title bar: the content runs under a transparent title
 * bar with the traffic lights over it. Windows and Linux keep their normal decorated window.
 */
private fun FrameWindowScope.configureChrome() {
    window.minimumSize = Dimension(DesktopDimens.MinWindowWidth.value.toInt(), DesktopDimens.MinWindowHeight.value.toInt())
    if (isMac) {
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", true)
    }
}
