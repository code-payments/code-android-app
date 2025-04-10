package com.flipcash.services

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.billing.BillingClient
import com.flipcash.services.billing.StubBillingClient

val LocalBillingClient = staticCompositionLocalOf<BillingClient> { StubBillingClient }