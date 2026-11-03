package com.flipcash.shared.common.ui.profile

/**
 * What a chatter is called under their portrait: their display name, else their handle, else
 * [fallback]. Blank counts as missing, since the server sends an empty display name for a profile
 * that has none.
 */
fun chatterName(displayName: String?, username: String?, fallback: String): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: fallback
