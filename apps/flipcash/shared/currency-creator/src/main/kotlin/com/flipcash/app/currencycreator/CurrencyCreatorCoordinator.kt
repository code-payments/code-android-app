package com.flipcash.app.currencycreator

import com.flipcash.app.core.tokens.CurrencyCreatorDraft
import com.flipcash.app.persistence.sources.CurrencyCreatorDraftDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyCreatorCoordinator @Inject constructor(
    private val draftDataSource: CurrencyCreatorDraftDataSource,
    private val dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)

    /** Tracks the draft ID for the active flow session. */
    private var activeDraftId: Long = 0

    /** Observable list of all drafts for future resume detection. */
    val drafts: Flow<List<CurrencyCreatorDraft>> = draftDataSource.observe()

    /** Save the current flow's draft. Creates a new row on first call,
     *  upserts by ID on subsequent calls. */
    suspend fun saveDraft(draft: CurrencyCreatorDraft) {
        val toSave = draft.copy(id = activeDraftId)
        val id = draftDataSource.insert(toSave)
        if (activeDraftId == 0L) {
            activeDraftId = id
        }
    }

    /** Clear the active flow's draft on completion or discard. */
    suspend fun clearDraft() {
        if (activeDraftId != 0L) {
            draftDataSource.deleteById(activeDraftId)
            activeDraftId = 0
        }
    }
}
