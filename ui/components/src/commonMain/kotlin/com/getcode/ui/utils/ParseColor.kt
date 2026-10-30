package com.getcode.ui.utils

/** Parses a `#RRGGBB` or `#AARRGGBB` color string into an ARGB int. */
internal expect fun parseColorInt(hex: String): Int
