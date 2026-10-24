package com.flipcash.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory

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
