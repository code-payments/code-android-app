package com.flipcash.desktop

import com.flipcash.libs.currency.math.Curves
import com.flipcash.libs.currency.math.initialize

/**
 * The startup work Android does in its androidx-startup initializers and `FlipcashApp.onCreate`
 * that the balance path relies on. `StartupContract` lists each step; `DesktopWiringTest` checks
 * that each took effect.
 */
internal suspend fun DesktopGraph.startup() {
    // NetworkImageLoader: token logos are https URLs, and Coil 3 has no network fetcher by default.
    installImageLoader()
    // CurveTables: balance valuations throw without them.
    Curves.initialize()
    // EventRepository: must exist before login, or OnLoggedIn reaches no session listener.
    eventRepository
}
