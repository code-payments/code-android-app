package com.flipcash.desktop.input

import androidx.compose.ui.input.key.Key
import com.flipcash.desktop.shell.ContextPaneMode
import com.flipcash.desktop.shell.DesktopNavState

/** What the window's shortcuts and menu items do (spec: Keyboard shortcuts). */
enum class DesktopCommand { Search, NewChat, ToggleInfo, Back, Settings, OpenInNewWindow, Escape }

enum class HostOs {
    Mac, Windows, Linux;

    companion object {
        val current: HostOs = System.getProperty("os.name").orEmpty().lowercase().let {
            when {
                it.startsWith("mac") -> Mac
                it.startsWith("windows") -> Windows
                else -> Linux
            }
        }
    }
}

/**
 * The command for a key press: ⌘ on macOS, Ctrl on Windows and Linux, the same letter on each.
 * Shift, Alt, or the other platform's modifier, makes it something else. Esc needs no modifier.
 */
fun commandFor(
    key: Key,
    meta: Boolean,
    ctrl: Boolean,
    shift: Boolean,
    os: HostOs,
    alt: Boolean = false,
): DesktopCommand? {
    if (key == Key.Escape) return if (!meta && !ctrl && !shift && !alt) DesktopCommand.Escape else null
    val primary = if (os == HostOs.Mac) meta && !ctrl else ctrl && !meta
    // Alt (AltGr arrives as Ctrl+Alt on Windows) types characters on many layouts; never a command.
    if (!primary || shift || alt) return null
    return when (key) {
        Key.K -> DesktopCommand.Search
        Key.N -> DesktopCommand.NewChat
        Key.I -> DesktopCommand.ToggleInfo
        Key.LeftBracket -> DesktopCommand.Back
        Key.Comma -> DesktopCommand.Settings
        Key.Enter -> DesktopCommand.OpenInNewWindow
        else -> null
    }
}

/**
 * Runs [command] and says whether it did anything, so an unhandled key still reaches the focused
 * field. Search and New Chat arrive with chat (roadmap Plan 3b), a new window with Plans 3b and 5,
 * and Settings with Plan 7; until then they do nothing.
 */
fun DesktopNavState.perform(command: DesktopCommand, mode: ContextPaneMode): Boolean = when (command) {
    DesktopCommand.ToggleInfo -> { toggleInfo(); true }
    DesktopCommand.Back -> back()
    DesktopCommand.Escape -> onEscape(mode)
    DesktopCommand.Search, DesktopCommand.NewChat, DesktopCommand.OpenInNewWindow, DesktopCommand.Settings -> false
}

/** Whether [perform] does anything for [command] yet; the menu bar greys out the rest. */
val DesktopCommand.available: Boolean
    get() = this == DesktopCommand.ToggleInfo || this == DesktopCommand.Back || this == DesktopCommand.Escape
