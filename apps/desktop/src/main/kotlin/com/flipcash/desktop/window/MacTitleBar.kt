package com.flipcash.desktop.window

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import java.awt.Frame

/**
 * Makes the macOS title bar [height] tall, so the traffic lights centre in the sidebar's title row
 * (node 13000:93) rather than near the window's top edge. Needs the JetBrains Runtime; on any other
 * runtime this returns null and the window keeps the standard title bar.
 */
fun installTallTitleBar(window: Frame, height: Float): CustomTitleBar? {
    if (!JBR.isWindowDecorationsSupported()) return null
    val decorations = JBR.getWindowDecorations()
    val bar = decorations.createCustomTitleBar()
    bar.height = height
    decorations.setCustomTitleBar(window, bar)
    return bar
}

/**
 * Inside the title bar a press drags the window unless the app claims it. A press that a control
 * consumed is the control's, and so is the drag that follows it until release; anything else (the
 * toolbar's title, empty space) drags the window, as a native title bar does. Same rule as Jewel's
 * TitleBar.MacOS.kt.
 */
fun Modifier.titleBarHitTest(bar: CustomTitleBar): Modifier = pointerInput(bar) {
    awaitPointerEventScope {
        var inControl = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val consumed = event.changes.any { it.isConsumed }
            if (event.type == PointerEventType.Press && consumed) inControl = true
            bar.forceHitTest(consumed || inControl)
            if (event.type == PointerEventType.Release) inControl = false
        }
    }
}
