package com.flipcash.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the wallet screen off-screen through the same graph as [main] and writes a PNG.
 * Usage: `./gradlew :apps:desktop:snapshot -Pout=<file>`; add `-Pscreen=link` for the link screen,
 * which skips sign-in.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "wallet.png")
    val linkScreen = args.getOrNull(1) == "link"
    val graph = createGraph<DesktopGraph>()
    runBlocking {
        graph.startup()
        if (!linkScreen) graph.restoreSession()
    }
    ImageComposeScene(width = 840, height = 1720, density = Density(2f)) {
        if (linkScreen) DesktopLinkContent(graph, onSignedIn = {}) else DesktopWalletContent(graph)
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
