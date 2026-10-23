package com.getcode.opencode.utils

import kotlin.math.floor

val Long.floored: Long
    get() {
        val seconds = this / 1000.0
        val flooredSeconds = floor(seconds)
        return (flooredSeconds * 1_000).toLong()
    }