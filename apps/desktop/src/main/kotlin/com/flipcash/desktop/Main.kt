package com.flipcash.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metro.createGraph
import java.lang.management.ManagementFactory

fun main() {
    val graph = createGraph<DesktopGraph>()
    application {
        Window(onCloseRequest = ::exitApplication, title = "Flipcash") {
            MaterialTheme {
                Text(graph.appName)
            }
            LaunchedEffect(Unit) {
                val sinceJvmStart = System.currentTimeMillis() -
                    ManagementFactory.getRuntimeMXBean().startTime
                println("startup: first composition ${sinceJvmStart}ms after JVM start")
            }
        }
    }
}
