package com.flipcash.shared.chat.ui.media

import androidx.compose.runtime.Composable

/**
 * True when the user has turned system animations off. Android reads `ANIMATOR_DURATION_SCALE`;
 * desktop has no such setting, so it is always false.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
