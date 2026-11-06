package com.flipcash.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.awt.Taskbar
import java.lang.management.ManagementFactory

fun main() {
    val graph = createGraph<DesktopGraph>()
    val signedIn = runBlocking {
        graph.startup()
        graph.restoreSession()
    }
    val appIcon = loadAppIcon()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Flipcash",
            icon = appIcon,
            state = rememberWindowState(width = 420.dp, height = 860.dp),
        ) {
            DesktopApp(graph, signedIn)
            LaunchedEffect(Unit) {
                val sinceJvmStart = System.currentTimeMillis() -
                    ManagementFactory.getRuntimeMXBean().startTime
                println("startup: first composition ${sinceJvmStart}ms after JVM start")
            }
        }
    }
}

/**
 * Title bar and taskbar icon on Windows and Linux. A packaged Mac app takes its Dock icon from the bundle;
 * `gradle run` does not, so the Dock would show the Java icon without the Taskbar call. The Mac image
 * keeps the 824/1024 icon grid margin so it sits level with other Dock icons.
 */
private fun loadAppIcon(): BitmapPainter {
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage = loadBitmap("/flipcash-icon-macos.png").toAwtImage()
    }
    return BitmapPainter(loadBitmap("/flipcash-icon.png"))
}

private fun loadBitmap(path: String): ImageBitmap {
    val bytes = checkNotNull(object {}.javaClass.getResourceAsStream(path)) { "missing resource $path" }
        .use { it.readBytes() }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
