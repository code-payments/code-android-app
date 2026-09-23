package com.flipcash.analytics.events

import com.flipcash.analytics.Button
import com.flipcash.analytics.event

object ButtonEvents {
    fun tapped(button: Button) = event("Button: ${button.value}")
}
