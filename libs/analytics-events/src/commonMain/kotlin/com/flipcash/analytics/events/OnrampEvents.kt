package com.flipcash.analytics.events

import com.flipcash.analytics.OnrampStep
import com.flipcash.analytics.event

object OnrampEvents {
    // Show Verification Info is Android only; part 2 decides whether iOS sends it.
    fun step(step: OnrampStep) = event("Onramp: ${step.value}")
}
