package com.flipcash.app.persistence.sources

import com.flipcash.app.core.tokens.CurrencyCreatorDraft
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.draft.DraftToEntityMapper
import com.flipcash.app.persistence.sources.mapper.draft.EntityToDraftMapper
import com.flipcash.services.persistence.SingularDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyCreatorDraftDataSource @Inject constructor(
    private val toEntityMapper: DraftToEntityMapper,
    private val toDomainMapper: EntityToDraftMapper,
) : SingularDataSource<Long, CurrencyCreatorDraft, CurrencyCreatorDraft> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    override suspend fun getById(id: Long): CurrencyCreatorDraft? {
        val entity = db?.currencyCreatorDraftDao()?.getById(id) ?: return null
        return toDomainMapper.map(entity)
    }

    override suspend fun get(): List<CurrencyCreatorDraft> {
        val entities = db?.currencyCreatorDraftDao()?.getAll() ?: return emptyList()
        return entities.map { toDomainMapper.map(it) }
    }

    /** Upsert a draft and return the persisted ID (auto-generated for new drafts). */
    override suspend fun upsert(value: CurrencyCreatorDraft) {
        db?.currencyCreatorDraftDao()?.upsert(toEntityMapper.map(value))
    }

    suspend fun insert(value: CurrencyCreatorDraft): Long {
        return db?.currencyCreatorDraftDao()?.upsert(toEntityMapper.map(value)) ?: 0
    }

    override suspend fun query(whereClause: String): List<CurrencyCreatorDraft> = emptyList()

    override suspend fun getMostRecent(): CurrencyCreatorDraft? = get().firstOrNull()

    override suspend fun clear() {
        db?.currencyCreatorDraftDao()?.deleteAll()
    }

    suspend fun deleteById(id: Long) {
        db?.currencyCreatorDraftDao()?.deleteById(id)
    }

    override fun observe(): Flow<List<CurrencyCreatorDraft>> {
        return db?.currencyCreatorDraftDao()?.observeAll()
            ?.map { entities -> entities.map { toDomainMapper.map(it) } }
            ?: emptyFlow()
    }

    override fun observe(id: Long): Flow<CurrencyCreatorDraft?> {
        return observe().map { drafts -> drafts.firstOrNull { it.id == id } }
    }
}
