package com.flipcash.desktop

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph(AppScope::class)
interface DesktopGraph {
    val appName: String

    @Provides
    fun provideAppName(): String = "Flipcash"
}
