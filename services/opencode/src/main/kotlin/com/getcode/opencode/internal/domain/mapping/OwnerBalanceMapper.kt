package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.balance.v1.OcpBalanceService
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.OwnerBalance
import javax.inject.Inject

internal class OwnerBalanceMapper @Inject constructor() :
    Mapper<OcpBalanceService.OwnerBalance, OwnerBalance> {
    override fun map(from: OcpBalanceService.OwnerBalance): OwnerBalance {
        return OwnerBalance(
            owner = from.owner.toPublicKey(),
            coreMintValue = Fiat(quarks = from.coreMintValue, currencyCode = CurrencyCode.USD),
            balancesByMint = from.balancesByMintMap.values.associate { mintBalance ->
                mintBalance.mint.toMint() to Fiat(
                    quarks = mintBalance.coreMintValue,
                    currencyCode = CurrencyCode.USD
                )
            }
        )
    }
}
