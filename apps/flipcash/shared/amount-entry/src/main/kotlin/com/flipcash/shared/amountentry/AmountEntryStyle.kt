package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.ConfirmationStyle

data class AmountEntryStyle(
    val actionLabel: String,
    val actionStyle: ConfirmationStyle = ConfirmationStyle.Button,
    val canChangeCurrency: Boolean = true,
    val infoHint: (maxFormatted: String) -> String = { "" },
    val overMaxHint: (maxFormatted: String) -> String = { "" },
    val belowMinHint: ((minFormatted: String) -> String)? = null,
)
