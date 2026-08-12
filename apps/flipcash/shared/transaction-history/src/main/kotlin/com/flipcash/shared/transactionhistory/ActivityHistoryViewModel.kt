package com.flipcash.shared.transactionhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Backs the full unified paged activity history — the "dive in" from a recent-activity preview.
 * Uses the coordinator's presentation-ready paged feed (avatars/titles/icons resolve reactively from
 * the observed caches), the same rows a preview shows, just unbounded.
 */
@HiltViewModel
class ActivityHistoryViewModel @Inject constructor(
    feedCoordinator: ActivityFeedCoordinator,
) : ViewModel() {
    val transactions: Flow<PagingData<TransactionListItem>> =
        feedCoordinator.recentTransactions().cachedIn(viewModelScope)
}
