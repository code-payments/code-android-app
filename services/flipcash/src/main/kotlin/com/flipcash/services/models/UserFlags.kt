package com.flipcash.services.models

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration

data class UserFlags(
    val isStaff: Boolean,
    val isRegistered: Boolean,
    val requiresIapForRegistration: Boolean,
    val preferredOnRampProvider: OnRampProvider?,
    val supportedOnRampProviders: List<OnRampProvider>,
    val minimumVersion: Int?,
    val billExchangeDataTimeout: Duration?,
    val newCurrencyPurchaseAmount: Fiat,
    val newCurrencyFeeAmount: Fiat,
) {
    companion object {
        val Default = UserFlags(
            isStaff = false,
            isRegistered = false,
            requiresIapForRegistration = false,
            preferredOnRampProvider = null,
            supportedOnRampProviders = emptyList(),
            minimumVersion = null,
            billExchangeDataTimeout = null,
            newCurrencyPurchaseAmount = Fiat.MAX_VALUE,
            newCurrencyFeeAmount = Fiat.MAX_VALUE,
        )
    }
}