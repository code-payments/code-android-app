package com.flipcash.desktop.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** The standard macOS Edit menu items, with the shortcut each one stands for. */
enum class EditAction(val title: String, val key: Key, val keyCode: Int, val shift: Boolean = false) {
    Undo("Undo", Key.Z, KeyEvent.VK_Z),
    Redo("Redo", Key.Z, KeyEvent.VK_Z, shift = true),
    Cut("Cut", Key.X, KeyEvent.VK_X),
    Copy("Copy", Key.C, KeyEvent.VK_C),
    Paste("Paste", Key.V, KeyEvent.VK_V),
    SelectAll("Select All", Key.A, KeyEvent.VK_A);

    val shortcut get() = KeyShortcut(key, meta = true, shift = shift)
}

/**
 * Runs an Edit menu item by sending its shortcut to the focused component, where the Compose text
 * field handles it as if typed. The menu's accelerator takes the real key press before the field sees
 * it, so without this ⌘C would stop copying once the menu exists. A forward that comes back through
 * the menu (nothing focused handled it) is dropped instead of looping.
 */
class EditForwarder(
    private val focusOwner: () -> Component? = { KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner },
    private val dispatch: (Component, KeyEvent) -> Unit = { target, event ->
        KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(target, event)
    },
) {
    private var forwarding = false

    fun forward(action: EditAction) {
        if (forwarding) return
        val target = focusOwner() ?: return
        val modifiers = InputEvent.META_DOWN_MASK or (if (action.shift) InputEvent.SHIFT_DOWN_MASK else 0)
        val now = System.currentTimeMillis()
        forwarding = true
        try {
            for (id in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
                dispatch(target, KeyEvent(target, id, now, modifiers, action.keyCode, KeyEvent.CHAR_UNDEFINED))
            }
        } finally {
            forwarding = false
        }
    }
}
