package com.flipcash.shared.amountentry

sealed interface AmountEntryHint {
    data object None : AmountEntryHint
    data class Info(val text: String) : AmountEntryHint
    data class Error(val text: String) : AmountEntryHint
}
