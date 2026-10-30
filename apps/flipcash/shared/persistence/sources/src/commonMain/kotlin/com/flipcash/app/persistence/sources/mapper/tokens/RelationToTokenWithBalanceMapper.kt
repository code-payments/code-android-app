package com.flipcash.app.persistence.sources.mapper.tokens

import com.flipcash.app.persistence.entities.TokenWithBalanceRelation
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import javax.inject.Inject

class RelationToTokenWithBalanceMapper @Inject constructor(
    private val tokenMapper: EntityToTokenMapper,
) : Mapper<TokenWithBalanceRelation, TokenWithBalance> {

    override fun map(from: TokenWithBalanceRelation): TokenWithBalance {
        val token = tokenMapper.map(from.token)
        val balance = from.valuation?.let { Fiat(quarks = it.balanceQuarks) } ?: Fiat.Zero
        val appreciation = from.valuation?.let {
            balance - Fiat(fiat = it.costBasis)
        } ?: Fiat.Zero

        return TokenWithBalance(
            token = token,
            balance = balance,
            appreciation = appreciation,
        )
    }
}