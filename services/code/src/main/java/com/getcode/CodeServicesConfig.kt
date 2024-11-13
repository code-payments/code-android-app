package com.getcode

import com.getcode.services.BuildConfig
import com.getcode.services.ChannelConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal data class CodeServicesConfig(
    override val baseUrl: String = "api.codeinfra.net",
    override val userAgent: String = "Code/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
