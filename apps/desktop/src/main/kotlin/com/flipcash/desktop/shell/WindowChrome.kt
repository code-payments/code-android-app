package com.flipcash.desktop.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the OS window frame takes from the top-leading corner of the content. On macOS the content runs
 * under the title bar: [titleBarHeight] is the band the traffic lights sit in and [controlsWidth] the
 * room they take from the leading edge. Windows and Linux draw their own frame above the content, so
 * both are 0. [flushSidebar] is set when the runtime can't make the title bar as tall as the sidebar's
 * title row: the lights then sit near the top edge, so the glass panel meets that edge squarely
 * instead of putting its rounded corner under them.
 */
@Immutable
data class WindowChrome(
    val titleBarHeight: Dp = 0.dp,
    val controlsWidth: Dp = 0.dp,
    val flushSidebar: Boolean = false,
)

val LocalWindowChrome = staticCompositionLocalOf { WindowChrome() }
