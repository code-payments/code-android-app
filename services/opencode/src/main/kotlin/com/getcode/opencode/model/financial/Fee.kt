package com.getcode.opencode.model.financial

data class Fee(
    val fiat: Fiat,
    val type: FeeType,
)

sealed interface FeeType {
    data object WithdrawalCreateOnSend: FeeType
}
