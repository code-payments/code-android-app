package com.flipcash.app.core.ui

/** Determines the confirmation widget shown on amount entry screens. */
enum class ConfirmationStyle {
    /** Standard tap-to-confirm button (CodeButton). */
    Button,
    /** Swipe-to-confirm slider (SlideToConfirm). */
    Slide,
}
