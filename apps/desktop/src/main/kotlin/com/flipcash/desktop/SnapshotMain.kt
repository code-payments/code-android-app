package com.flipcash.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.flipcash.desktop.input.DesktopCommand
import com.flipcash.desktop.input.perform
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.DesktopNavState
import com.flipcash.desktop.shell.widthClassFor
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders a screen off-screen through the same graph as [main] and writes a PNG.
 * Usage: `./gradlew :apps:desktop:snapshot -Pout=<file> -Pscreen=shell|shell-wallet|you|link -Pwidth=<dp> -Pheight=<dp>`.
 * The link screen skips sign-in. Width picks the layout exactly as resizing the window does.
 *
 * `-Pmotion=peek|info` writes frames instead, every 33ms, into the directory `out` names, so a
 * transition can be stepped through: peek rests on the rail, moves onto the floating list's toggle, then presses Esc; info opens and
 * closes the Info pane.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "snapshot.png")
    val screen = args.getOrNull(1) ?: "chat"
    val width = args.getOrNull(2)?.toIntOrNull() ?: 1040
    val height = args.getOrNull(3)?.toIntOrNull() ?: 760
    val motion = args.getOrNull(4)?.takeIf { it.isNotEmpty() }
    val linkScreen = screen == "link"
    val context = when (screen) {
        "shell-wallet", "money", "wallet" -> ContextContent.Wallet
        "you" -> ContextContent.You
        else -> null
    }
    val graph = createGraph<DesktopGraph>()
    runBlocking {
        graph.startup()
        if (!linkScreen) graph.restoreSession()
    }
    val scale = 2f
    val nav = DesktopNavState().apply { this.context = context }
    ImageComposeScene(width = (width * scale).toInt(), height = (height * scale).toInt(), density = Density(scale)) {
        if (linkScreen) DesktopLinkContent(graph, onSignedIn = {}) else DesktopMainContent(graph, nav = nav)
    }.use { scene ->
        var nanos = 0L
        repeat(80) {
            scene.render(nanos)
            Thread.sleep(100)
            nanos += 100_000_000L
        }
        if (motion == null) {
            out.writeBytes(scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } else {
            out.mkdirs()
            var frame = 0
            // Delays in the shell run on real time, so each frame waits as long as it advances.
            fun frames(millis: Int) = repeat(millis / 33) {
                Thread.sleep(33)
                nanos += 33_000_000L
                val png = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "%03d.png".format(frame++)).writeBytes(png)
            }
            fun run(command: DesktopCommand) = nav.perform(command, nav.contextPaneModeAt(widthClassFor(width.dp)))
            frames(100)
            when (motion) {
                "peek" -> {
                    scene.sendPointerEvent(PointerEventType.Enter, Offset(40 * scale, height * scale / 2))
                    scene.sendPointerEvent(PointerEventType.Move, Offset(40 * scale, height * scale / 2))
                    frames(700)
                    // Onto the list's toggle, in the title row: the list should stay out.
                    scene.sendPointerEvent(PointerEventType.Move, Offset(24 * scale, 26 * scale))
                    frames(300)
                    run(DesktopCommand.Escape)
                    frames(500)
                }
                "info" -> {
                    run(DesktopCommand.ToggleInfo)
                    frames(500)
                    run(DesktopCommand.ToggleInfo)
                    frames(500)
                }
            }
        }
    }
    println("snapshot written to ${out.absolutePath}")
    exitProcess(0)
}
