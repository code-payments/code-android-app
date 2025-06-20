package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.persistence.PoolResolutionConverter
import com.flipcash.app.persistence.entities.PoolEntity
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

class PoolEntityToPoolMapper @Inject constructor() : Mapper<PoolEntity, Pool> {
    override fun map(from: PoolEntity): Pool {
        val nativeAmount = from.buyInAmount
        val currencyCode = CurrencyCode.tryValueOf(from.buyInCurrency) ?: CurrencyCode.USD

        return Pool(
            id = from.id,
            creator = from.creator,
            name = from.name,
            buyIn = Fiat(nativeAmount, currencyCode),
            fundingDestination = from.fundingDestination,
            isOpen = from.isOpen,
            resolution = PoolResolutionConverter.toPoolResolution(from.resolution),
            createdAt = from.createdAt,
            didWin = from.didWin
        )
    }
}