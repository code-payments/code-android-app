package com.flipcash.shared.amountentry

import kotlinx.coroutines.flow.StateFlow

interface AmountEntryController {
    val state: StateFlow<AmountEntryDelegate.State>
    val config: StateFlow<AmountEntryConfig>

    fun onNumber(number: Int)
    fun onDecimal()
    fun onBackspace()
}
