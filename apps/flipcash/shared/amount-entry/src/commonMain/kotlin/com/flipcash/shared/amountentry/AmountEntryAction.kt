package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.ConfirmationStyle
import com.getcode.view.LoadingSuccessState

data class AmountEntryAction(
    val label: AmountEntryLabel,
    val style: ConfirmationStyle = ConfirmationStyle.Button,
    val loadingState: LoadingSuccessState = LoadingSuccessState(),
)
