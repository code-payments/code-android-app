package com.flipcash.app.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import java.util.UUID

sealed interface MenuItem<T> {
    val id: Any

    @get:Composable
    val icon: Painter

    @get:Composable
    val name: String

    val action: T

    val isStaffOnly: Boolean
}

abstract class FullMenuItem<T>(
    override val id: Any = UUID.randomUUID().toString(),
    override val isStaffOnly: Boolean = false
) : MenuItem<T>


abstract class StaffMenuItem<T>(
    override val id: Any = UUID.randomUUID().toString(),
    override val isStaffOnly: Boolean = true
) : MenuItem<T>

