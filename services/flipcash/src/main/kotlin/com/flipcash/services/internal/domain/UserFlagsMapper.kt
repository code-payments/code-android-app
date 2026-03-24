package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class UserFlagsMapper @Inject constructor():
    Mapper<FlipcashAccountService.UserFlags, UserFlags> {
    override fun map(from: FlipcashAccountService.UserFlags): UserFlags {
        return UserFlags(
            isRegistered = from.isRegisteredAccount,
            isStaff = from.isStaff,
            requiresIapForRegistration = from.requiresIapForRegistration,
            preferredOnRampProvider = from.preferredOnRampProvider.toDomain().takeIf { it != OnRampProvider.Unknown },
            supportedOnRampProviders = from.supportedOnRampProvidersList.map { it.toDomain() },
            minimumVersion = from.minBuildNumber,
            billExchangeDataTimeout = from.billExchangeDataTimeout.seconds.toDuration(DurationUnit.SECONDS),
            newCurrencyPurchaseAmount = Fiat(quarks = from.newCurrencyPurchaseAmount)
        )
    }
}

private fun FlipcashAccountService.UserFlags.OnRampProvider.toDomain(): OnRampProvider {
    return when (this) {
        FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_VIRTUAL -> OnRampProvider.Coinbase(OnRampType.Virtual)
        FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_PHYSICAL_DEBIT -> OnRampProvider.Coinbase(OnRampType.PhysicalDebit)
        FlipcashAccountService.UserFlags.OnRampProvider.COINBASE_PHYSICAL_CREDIT -> OnRampProvider.Coinbase(OnRampType.PhysicalCredit)
        FlipcashAccountService.UserFlags.OnRampProvider.MANUAL_DEPOSIT -> OnRampProvider.ManualDeposit
        FlipcashAccountService.UserFlags.OnRampProvider.PHANTOM -> OnRampProvider.Phantom
        FlipcashAccountService.UserFlags.OnRampProvider.SOLFLARE -> OnRampProvider.Solflare
        FlipcashAccountService.UserFlags.OnRampProvider.BACKPACK -> OnRampProvider.Backpack
        // Unhandled for now
        FlipcashAccountService.UserFlags.OnRampProvider.BASE,
        FlipcashAccountService.UserFlags.OnRampProvider.UNKNOWN,
        FlipcashAccountService.UserFlags.OnRampProvider.UNRECOGNIZED -> OnRampProvider.Unknown
    }
}