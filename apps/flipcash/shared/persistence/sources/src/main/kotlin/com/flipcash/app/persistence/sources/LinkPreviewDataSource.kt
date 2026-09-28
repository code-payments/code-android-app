package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.LinkPreviewEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** One stored link preview; see `LinkPreviewEntity`. */
data class LinkPreviewRecord(
    val key: String,
    val json: String,
    val updatedAt: Long,
)

/**
 * The `link_previews` table.
 *
 * The database is per user, so [observeAll] follows the instance rather than reading once: a
 * logout empties it, and the next login loads that user's previews. No database means no previews
 * and writes that go nowhere, matching the other data sources here.
 */
@Singleton
class LinkPreviewDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    /**
     * Every stored preview, read once each time a database opens, and empty while none is. Rows
     * last written before [writtenSince] returns are deleted first rather than loaded, so the
     * table holds the links the reader still comes across and not every link ever seen.
     *
     * Not a Room observable query: the writes that follow a load come from the same process that
     * holds the answers in memory, so re-reading the table on each of them would only hand back
     * what the reader already has.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAll(writtenSince: () -> Long): Flow<List<LinkPreviewRecord>> =
        FlipcashDatabase.observeInstance().flatMapLatest { instance ->
            if (instance == null) {
                flowOf(emptyList())
            } else {
                flow {
                    val dao = instance.linkPreviewDao()
                    dao.deleteWrittenBefore(writtenSince())
                    emit(
                        dao.getAll().map {
                            LinkPreviewRecord(key = it.key, json = it.json, updatedAt = it.updatedAt)
                        },
                    )
                }
            }
        }

    suspend fun upsert(record: LinkPreviewRecord) {
        db?.linkPreviewDao()?.upsert(
            LinkPreviewEntity(key = record.key, json = record.json, updatedAt = record.updatedAt),
        )
    }

    suspend fun delete(key: String) {
        db?.linkPreviewDao()?.delete(key)
    }
}
