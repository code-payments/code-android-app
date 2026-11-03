package com.flipcash.desktop.input

import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditMenuTest {
    private val field = JPanel()

    private fun forwarded(action: EditAction, focused: Component? = field): List<KeyEvent> {
        val sent = mutableListOf<KeyEvent>()
        lateinit var forwarder: EditForwarder
        forwarder = EditForwarder(focusOwner = { focused }) { _, event ->
            sent += event
            // A menu accelerator that catches the forwarded key must not forward it again.
            forwarder.forward(action)
        }
        forwarder.forward(action)
        return sent
    }

    @Test
    fun `each item sends its shortcut to the focused field`() {
        val expected = mapOf(
            EditAction.Undo to (KeyEvent.VK_Z to false),
            EditAction.Redo to (KeyEvent.VK_Z to true),
            EditAction.Cut to (KeyEvent.VK_X to false),
            EditAction.Copy to (KeyEvent.VK_C to false),
            EditAction.Paste to (KeyEvent.VK_V to false),
            EditAction.SelectAll to (KeyEvent.VK_A to false),
        )
        for ((action, key) in expected) {
            val (code, shift) = key
            val press = forwarded(action).first()
            assertEquals(KeyEvent.KEY_PRESSED, press.id, "$action")
            assertEquals(code, press.keyCode, "$action")
            assertTrue(press.modifiersEx and InputEvent.META_DOWN_MASK != 0, "$action")
            assertEquals(shift, press.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0, "$action")
        }
    }

    @Test
    fun `press then release, once`() {
        assertEquals(listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED), forwarded(EditAction.Paste).map { it.id })
    }

    @Test
    fun `nothing focused sends nothing`() {
        assertTrue(forwarded(EditAction.Copy, focused = null).isEmpty())
    }
}
