package com.flipcash.app.featureflags.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class BackgroundResetTimeout(
    val duration: Duration?,
    val label: String,
) {
    OneMinute(1.minutes, "1 Minute"),
    FiveMinutes(5.minutes, "5 Minutes"),
    Never(null, "Never");
}
