package com.flipcash.desktop.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.input.key.KeyShortcut

/** The commands the macOS menu bar owns; their shortcuts reach the app through the menu, not as key events. */
private val menuCommands = setOf(DesktopCommand.NewChat, DesktopCommand.ToggleInfo, DesktopCommand.Search)

/** The window's key handling: a key press becomes a [DesktopCommand], unless the menu bar already handles it. */
fun desktopKeyHandler(os: HostOs, run: (DesktopCommand) -> Boolean): (KeyEvent) -> Boolean = handler@{ event ->
    if (event.type != KeyEventType.KeyDown) return@handler false
    val command = commandFor(event.key, event.isMetaPressed, event.isCtrlPressed, event.isShiftPressed, os, event.isAltPressed)
        ?: return@handler false
    if (os == HostOs.Mac && command in menuCommands) return@handler false
    run(command)
}

/**
 * The macOS menu bar (spec: Native per OS). Flipcash's own menu, with Settings…, is the system app
 * menu and gets its item when Settings lands (Plan 7). Edit acts on the focused text field. Items for commands that can't run yet are
 * disabled rather than hidden, so the menus keep their final shape.
 */
@Composable
fun FrameWindowScope.DesktopMenuBar(windowState: WindowState, run: (DesktopCommand) -> Boolean) {
    fun item(command: DesktopCommand) = { run(command); Unit }
    val edit = remember { EditForwarder() }
    MenuBar {
        Menu("File") {
            Item("New Chat", enabled = DesktopCommand.NewChat.available,
                shortcut = KeyShortcut(Key.N, meta = true), onClick = item(DesktopCommand.NewChat))
        }
        Menu("Edit") {
            for (action in EditAction.entries) {
                Item(action.title, shortcut = action.shortcut, onClick = { edit.forward(action) })
                if (action == EditAction.Redo) Separator()
            }
        }
        Menu("View") {
            Item("Chat Info", enabled = DesktopCommand.ToggleInfo.available,
                shortcut = KeyShortcut(Key.I, meta = true), onClick = item(DesktopCommand.ToggleInfo))
            Item("Search", enabled = DesktopCommand.Search.available,
                shortcut = KeyShortcut(Key.K, meta = true), onClick = item(DesktopCommand.Search))
        }
        Menu("Window") {
            Item("Minimize", shortcut = KeyShortcut(Key.M, meta = true), onClick = { windowState.isMinimized = true })
            Item("Zoom", onClick = {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            })
        }
    }
}
