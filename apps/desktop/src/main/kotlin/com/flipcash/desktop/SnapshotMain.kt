package com.flipcash.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.flipcash.app.core.navigation.NavBarButton
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders a screen off-screen through the same graph as [main] and writes a PNG.
 * Usage: `./gradlew :apps:desktop:snapshot -Pout=<file> -Pscreen=chat|money|you|link -Pwidth=<dp> -Pheight=<dp>`.
 * The link screen skips sign-in. Width picks the layout exactly as resizing the window does.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "snapshot.png")
    val screen = args.getOrNull(1) ?: "chat"
    val width = args.getOrNull(2)?.toIntOrNull() ?: 1040
    val height = args.getOrNull(3)?.toIntOrNull() ?: 760
    val linkScreen = screen == "link"
    val tab = when (screen) {
        "money", "wallet" -> NavBarButton.Wallet
        "you" -> NavBarButton.TipCard
        else -> NavBarButton.Chats
    }
    val graph = createGraph<DesktopGraph>()
    runBlocking {
        graph.startup()
        if (!linkScreen) graph.restoreSession()
    }
    val scale = 2f
    ImageComposeScene(width = (width * scale).toInt(), height = (height * scale).toInt(), density = Density(scale)) {
        if (linkScreen) DesktopLinkContent(graph, onSignedIn = {}) else DesktopMainContent(graph, initialTab = tab)
    }.use { scene ->
        var nanos = 0L
        repeat(80) {
            scene.render(nanos)
            Thread.sleep(100)
            nanos += 100_000_000L
        }
        val png = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
        out.writeBytes(png)
    }
    println("snapshot written to ${out.absolutePath}")
    exitProcess(0)
}
