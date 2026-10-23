package com.getcode.opencode

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface ProtocolConfig {
    val baseUrl: String
    val port: Int
        get() = 443
    val userAgent: String
    val keepAlive: Duration
        get() = 4.minutes
    val keepAliveTimeout: Duration
        get() = 1.minutes
}
