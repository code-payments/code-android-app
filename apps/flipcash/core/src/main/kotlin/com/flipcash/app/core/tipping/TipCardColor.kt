package com.flipcash.app.core.tipping

import androidx.compose.ui.graphics.Color

/**
 * Opaque stand-in for the frosted-glass tone, used on devices where the live blurred-camera backdrop
 * is disabled (pre-API-31 / low-RAM). Rendered at full opacity so the card stays cheap and never
 * shows the stutter-prone live feed through a translucent fill.
 *
 * Here rather than with the card that draws it, because two modules paint a tip card now: the real
 * one in `:apps:flipcash:shared:bills`, and the link card a chat draws for a tip card URL, which
 * cannot reach that module. A card the link opens to and a card standing in for the link have to be
 * the same colour, and one literal in two places is how that stops being true.
 */
val TipCardOpaqueFallback = Color(0xFF1A1A1C)
