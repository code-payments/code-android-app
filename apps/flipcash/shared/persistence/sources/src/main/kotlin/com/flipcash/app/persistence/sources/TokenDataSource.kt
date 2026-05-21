package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.TokenValuationEntity
import com.flipcash.app.persistence.sources.mapper.tokens.EntityToTokenMapper
import com.flipcash.app.persistence.sources.mapper.tokens.RelationToTokenWithBalanceMapper
import com.flipcash.app.persistence.sources.mapper.tokens.TokenToEntityMapper
import com.flipcash.services.persistence.SingularDataSource
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenDataSource @Inject constructor(
    private val entityMapper: TokenToEntityMapper,
    private val domainMapper: EntityToTokenMapper,
    private val relationMapper: RelationToTokenWithBalanceMapper,
) : SingularDataSource<Mint, MintMetadata, List<MintMetadata>> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    // region SingularDataSource — Token Metadata

    override suspend fun getById(id: Mint): MintMetadata? {
        val entity = db?.tokenDao()?.getByMint(id.base58()) ?: return null
        return domainMapper.map(entity)
    }

    suspend fun getBySymbol(symbol: String): MintMetadata? {
        val entity = db?.tokenDao()?.getBySymbol(symbol) ?: return null
        return domainMapper.map(entity)
    }

    override suspend fun get(): List<MintMetadata> {
        val entities = db?.tokenDao()?.getAll() ?: return emptyList()
        return entities.map { domainMapper.map(it) }
    }

    override suspend fun upsert(value: List<MintMetadata>) {
        val entities = value.map { entityMapper.map(it) }
        db?.tokenDao()?.upsertTokens(*entities.toTypedArray())
    }

    override suspend fun query(whereClause: String): List<MintMetadata> {
        return emptyList()
    }

    override suspend fun getMostRecent(): MintMetadata? = null

    override suspend fun clear() {
        db?.tokenDao()?.clearAll()
    }

    override fun observe(): Flow<List<MintMetadata>> {
        return db?.tokenDao()?.observeAll()
            ?.map { entities -> entities.map { domainMapper.map(it) } }
            ?: emptyFlow()
    }

    override fun observe(id: Mint): Flow<MintMetadata?> {
        return observe().map { tokens -> tokens.firstOrNull { it.address == id } }
    }

    // endregion

    // region Combined token + balance

    suspend fun getTokensWithBalances(): List<TokenWithBalance> {
        val relations = db?.tokenDao()?.getTokensWithBalances() ?: return emptyList()
        return relations.map { relationMapper.map(it) }
    }

    fun observeTokensWithBalances(): Flow<List<TokenWithBalance>> {
        return db?.tokenDao()?.observeTokensWithBalances()
            ?.map { relations -> relations.map { relationMapper.map(it) } }
            ?: emptyFlow()
    }

    /**
     * Persists token metadata and balances from domain objects.
     * All entity mapping is handled internally.
     */
    suspend fun upsertWithBalances(updates: List<TokenWithBalance>) {
        val dao = db?.tokenDao() ?: return

        val tokenEntities = updates.map { entityMapper.map(it.token) }
        dao.upsertTokens(*tokenEntities.toTypedArray())

        val balanceEntities = updates.map { update ->
            TokenValuationEntity(
                tokenAddress = update.token.address.base58(),
                balanceQuarks = update.balance.quarks,
                costBasis = update.costBasis.decimalValue,
            )
        }
        dao.upsertBalances(*balanceEntities.toTypedArray())
    }

    // endregion
}