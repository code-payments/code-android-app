package com.flipcash.app.search.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Warms [CurrencySearch] while Home is up, so the first query finds Discover loaded. */
@HiltViewModel
internal class SearchPreloadViewModel @Inject constructor(
    currencySearch: CurrencySearch,
) : ViewModel() {
    init {
        viewModelScope.launch { currencySearch.warm() }
    }
}
