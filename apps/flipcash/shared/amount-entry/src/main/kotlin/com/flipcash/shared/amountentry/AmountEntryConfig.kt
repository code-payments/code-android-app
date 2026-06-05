package com.flipcash.shared.amountentry

data class AmountEntryConfig(
    val hint: AmountEntryHint = AmountEntryHint.None,
    val canConfirm: Boolean = false,
    val canChangeCurrency: Boolean = true,
    val action: AmountEntryAction,
)
