package com.getcode.domain

import com.getcode.models.BillState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillController @Inject constructor(

) {
    private val _state = MutableStateFlow(BillState.Default)
    val state: StateFlow<BillState>
        get() = _state

    fun update(function: (BillState) -> BillState) {
        _state.update(function)
    }

    fun reset() {
        _state.update { BillState.Default }
    }
}