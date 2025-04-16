package com.getcode.services

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface ChannelConfig {
    val baseUrl: String
    val port: Int
        get() = 443
    val userAgent: String
    val keepAlive: Duration
        get() = 4.minutes
    val keepAliveTimeout: Duration
        get() = 1.minutes
}