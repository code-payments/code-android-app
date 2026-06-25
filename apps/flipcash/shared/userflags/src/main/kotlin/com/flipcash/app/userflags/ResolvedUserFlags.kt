package com.flipcash.app.userflags

import com.flipcash.app.userflags.UserFlagsCoordinator.Overrides
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.services.models.UserFlags
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration

data class ResolvedFlag<T>(
    val serverValue: T,
    val override: FieldOverride<T>,
) {
    val effectiveValue: T get() = when (override) {
        is FieldOverride.None -> serverValue
        is FieldOverride.Value -> override.value
    }

    val isOverridden: Boolean get() = override !is FieldOverride.None
}

data class ResolvedUserFlags(
    val isStaff: ResolvedFlag<Boolean>,
    val isRegistered: ResolvedFlag<Boolean>,
    val requiresIapForRegistration: ResolvedFlag<Boolean>,
    val preferredOnRampProvider: ResolvedFlag<OnRampProvider.Defined?>,
    val supportedOnRampProviders: ResolvedFlag<List<OnRampProvider.Defined>>,
    val minimumVersion: ResolvedFlag<Int?>,
    val billExchangeDataTimeout: ResolvedFlag<Duration?>,
    val newCurrencyPurchaseAmount: ResolvedFlag<Fiat>,
    val newCurrencyFeeAmount: ResolvedFlag<Fiat>,
    val withdrawalFeeAmount: ResolvedFlag<Fiat>,
    val usdcOnRampLiquidityPool: ResolvedFlag<UsdcLiquidtyPool>,
    val enablePhoneNumberSend: ResolvedFlag<Boolean>,
    val minimumHolderAmountForLeaderboard: ResolvedFlag<Fiat>,
    val requireCoinbaseEmailVerification: ResolvedFlag<Boolean>,
)

internal fun UserFlags.resolve(overrides: Overrides): ResolvedUserFlags = ResolvedUserFlags(
    isStaff = ResolvedFlag(isStaff, FieldOverride.None),
    isRegistered = ResolvedFlag(isRegistered, FieldOverride.None),
    requiresIapForRegistration = ResolvedFlag(requiresIapForRegistration, FieldOverride.None),
    preferredOnRampProvider = ResolvedFlag(preferredOnRampProvider as? OnRampProvider.Defined, overrides.preferredOnRampProvider),
    supportedOnRampProviders = ResolvedFlag(supportedOnRampProviders.filterIsInstance<OnRampProvider.Defined>(), overrides.supportedOnRampProviders),
    minimumVersion = ResolvedFlag(minimumVersion, overrides.minimumVersion),
    billExchangeDataTimeout = ResolvedFlag(billExchangeDataTimeout, overrides.billExchangeDataTimeout),
    newCurrencyPurchaseAmount = ResolvedFlag(newCurrencyPurchaseAmount, overrides.newCurrencyPurchaseAmount),
    newCurrencyFeeAmount = ResolvedFlag(newCurrencyFeeAmount, overrides.newCurrencyPurchaseAmount),
    withdrawalFeeAmount = ResolvedFlag(withdrawalFeeAmount, overrides.withdrawalFeeAmount),
    usdcOnRampLiquidityPool = ResolvedFlag(preferredUsdcOnRampLiquidityPool, overrides.preferredUsdcOnRampLiquidityPool),
    enablePhoneNumberSend = ResolvedFlag(enablePhoneNumberSend, FieldOverride.None),
    minimumHolderAmountForLeaderboard = ResolvedFlag(minimumHolderValue, overrides.minimumHolderAmountForLeaderboard),
    requireCoinbaseEmailVerification = ResolvedFlag(requireCoinbaseEmailVerification, overrides.requireCoinbaseEmailVerification),
)