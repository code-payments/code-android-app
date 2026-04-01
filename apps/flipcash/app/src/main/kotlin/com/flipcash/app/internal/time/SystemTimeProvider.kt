package com.flipcash.app.internal.time

import com.flipcash.app.core.time.TimeProvider
import kotlin.time.Clock

class SystemTimeProvider : TimeProvider {
    override fun now() = Clock.System.now()
    override fun currentTimeMillis() = System.currentTimeMillis()
}