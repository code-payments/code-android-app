package com.flipcash.desktop.window

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Dimension
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** The window as it was when last moved, resized or closed. */
@Serializable
data class SavedWindow(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
    val contextPaneOpen: Boolean,
)

/** Reads and writes [SavedWindow] as JSON in [file]. A missing or unreadable file loads as null. */
class WindowStateStore(private val file: Path) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedWindow? =
        if (!file.exists()) null else runCatching { json.decodeFromString<SavedWindow>(file.readText()) }.getOrNull()

    /**
     * Writes [w] through a temp file and a rename, so a crash mid-write leaves the old file. A failure
     * (read-only home, full disk, a locked file) is logged and dropped: losing the window's place must
     * never stop the window closing.
     */
    fun save(w: SavedWindow) {
        runCatching {
            file.parent?.createDirectories()
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            tmp.writeText(json.encodeToString(SavedWindow.serializer(), w))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { System.err.println("window state not saved: $it") }
    }
}

// The strip a user drags the window by. If none of it is on a screen, the window can't be moved back.
private const val TitleBarHeight = 28

/**
 * Fits the saved window to the [screens] now attached (usable bounds, without the dock or taskbar):
 * no larger than its screen, no smaller than [min], and wholly on it. A window whose title bar is on
 * no screen, as after unplugging a monitor, is centred on the first screen at its saved size.
 */
fun SavedWindow.fitTo(screens: List<Rectangle>, min: Dimension): SavedWindow {
    val first = screens.firstOrNull() ?: return this
    val titleBar = Rectangle(x, y, width, TitleBarHeight)
    val home = screens.firstOrNull { it.intersects(titleBar) }
    val screen = home ?: first
    val w = width.coerceAtMost(screen.width).coerceAtLeast(min.width)
    val h = height.coerceAtMost(screen.height).coerceAtLeast(min.height)
    if (home == null) {
        return copy(
            x = screen.x + (screen.width - w) / 2,
            y = screen.y + (screen.height - h) / 2,
            width = w,
            height = h,
        )
    }
    val maxX = screen.x + screen.width - w
    val maxY = screen.y + screen.height - h
    return copy(
        x = x.coerceAtMost(maxX).coerceAtLeast(screen.x),
        y = y.coerceAtMost(maxY).coerceAtLeast(screen.y),
        width = w,
        height = h,
    )
}
