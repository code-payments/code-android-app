package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.runtime.Composable
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The one glass material for the chat's floating surfaces: the composer's message bar, the `$` button,
 * the join-requests card and the jump-to-bottom button. Layer `ChatInputDefaults.ContainerColor` and
 * `ChatInputDefaults.RimBrush` over it as the composer does.
 */
@Composable
internal fun chatGlassMaterial(): HazeBlurStyle =
    HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
