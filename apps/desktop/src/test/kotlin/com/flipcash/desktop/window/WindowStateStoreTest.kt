package com.flipcash.desktop.window

import java.awt.Dimension
import java.awt.Rectangle
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowStateStoreTest {
    private val file = Files.createTempDirectory("window").resolve("window.json")
    private val store = WindowStateStore(file)
    private val screen = listOf(Rectangle(0, 0, 1440, 900))
    private val min = Dimension(400, 640)

    private fun SavedWindow.bounds() = Rectangle(x, y, width, height)

    @Test
    fun `round trips`() {
        val w = SavedWindow(10, 20, 1280, 800, maximized = false, contextPaneOpen = true)
        store.save(w)
        assertEquals(w, store.load())
    }

    @Test
    fun `a failed save does not throw and keeps the old file`() {
        val w = SavedWindow(10, 20, 1280, 800, maximized = false, contextPaneOpen = true)
        store.save(w)
        // A directory where the temp file should go makes the write fail.
        Files.createDirectory(file.resolveSibling("window.json.tmp"))
        store.save(w.copy(x = 99))
        assertEquals(w, store.load())
    }

    @Test
    fun `missing file loads as null`() {
        assertNull(store.load())
    }

    @Test
    fun `a file saved before the sidebar toggle loads with the sidebar shown`() {
        file.writeText("""{"x":1,"y":2,"width":1040,"height":760,"maximized":false,"contextPaneOpen":true}""")
        assertEquals(SavedWindow(1, 2, 1040, 760, maximized = false, contextPaneOpen = true, sidebarHidden = false), store.load())
    }

    @Test
    fun `corrupt file loads as null`() {
        file.writeText("{not json")
        assertNull(store.load())
    }

    @Test
    fun `off-screen window moves onto the first screen at its size`() {
        val fitted = SavedWindow(5000, 200, 1280, 800, false, false).fitTo(screen, min)
        assertEquals(Rectangle(80, 50, 1280, 800), fitted.bounds())
    }

    @Test
    fun `on-screen window stays put`() {
        val w = SavedWindow(100, 40, 1000, 700, false, false)
        assertEquals(w, w.fitTo(screen, min))
    }

    @Test
    fun `larger than the screen shrinks to it`() {
        val fitted = SavedWindow(0, 0, 3000, 2000, false, false).fitTo(screen, min)
        assertEquals(Rectangle(0, 0, 1440, 900), fitted.bounds())
    }

    @Test
    fun `never smaller than the minimum`() {
        val fitted = SavedWindow(100, 100, 100, 100, false, false).fitTo(screen, min)
        assertEquals(Dimension(400, 640), Dimension(fitted.width, fitted.height))
    }

    @Test
    fun `a window on the second screen stays there`() {
        val screens = listOf(Rectangle(0, 0, 1440, 900), Rectangle(1440, 0, 1920, 1080))
        val w = SavedWindow(1600, 100, 1280, 800, false, false)
        assertEquals(w, w.fitTo(screens, min))
    }
}
